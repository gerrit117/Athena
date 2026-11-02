package de.athena.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.compose.runtime.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.*
import java.util.UUID

data class LocalAlarm(
    val id: String, val label: String, val hour: Int, val minute: Int,
    val daily: Boolean, val enabled: Boolean, val zone: String, val next: Long,
    val volume: Int=75, val repeatDays: List<Int> = if(daily)(1..7).toList() else emptyList()
) {
    fun json()=JSONObject().put("id",id).put("label",label).put("hour",hour).put("minute",minute)
        .put("daily",repeatDays.size==7).put("enabled",enabled).put("zone",zone).put("next",next).put("volume",volume).put("repeatDays",JSONArray(repeatDays))
    companion object {
        fun from(j: JSONObject): LocalAlarm {
            val id=j.getString("id");val label=j.optString("label","Wecker").trim()
            val hour=j.getInt("hour");val minute=j.getInt("minute");val zone=j.getString("zone")
            val volume=j.optInt("volume",75)
            require(id.matches(Regex("[a-zA-Z0-9-]{1,64}")) && label.length<=80)
            require(hour in 0..23 && minute in 0..59 && volume in 1..100)
            ZoneId.of(zone)
            val days=j.optJSONArray("repeatDays")?.let {a -> (0 until a.length()).map {a.getInt(it)}.distinct().sorted()} ?: if(j.optBoolean("daily"))(1..7).toList() else emptyList()
            require(days.all {it in 1..7})
            return LocalAlarm(id,label,hour,minute,days.size==7,j.optBoolean("enabled",true),zone,j.getLong("next"),volume,days)
        }
    }
}

/** Select the next real instant, including the second occurrence during a DST fold. */
fun nextAlarmTime(hour: Int,minute: Int,zone: String,after: Long,days: List<Int> = emptyList()): Long {
    require(days.all {it in 1..7})
    require(hour in 0..23 && minute in 0..59)
    val tz=ZoneId.of(zone);val instant=Instant.ofEpochMilli(after)
    val today=instant.atZone(tz).toLocalDate()
    for(day in 0L..7L) {
        val date=today.plusDays(day)
        if(days.isNotEmpty() && date.dayOfWeek.value !in days)continue
        val local=date.atTime(hour,minute)
        val offsets=tz.rules.getValidOffsets(local)
        val candidates=if(offsets.isEmpty()) listOf(local.atZone(tz).toInstant()) else offsets.map {local.toInstant(it)}
        candidates.filter {it.isAfter(instant)}.minOrNull()?.let {return it.toEpochMilli()}
    }
    error("No next alarm time")
}
fun nextDailyAlarmTime(hour: Int,minute: Int,zone: String,after: Long,days: List<Int> = (1..7).toList()): Long {
    val tz=ZoneId.of(zone)
    val nextDayStart=Instant.ofEpochMilli(after).atZone(tz).toLocalDate().plusDays(1).atStartOfDay(tz).toInstant().toEpochMilli()
    return nextAlarmTime(hour,minute,zone,nextDayStart-1,days)
}

class AlarmEngine(private val context: Context) {
    private val prefs=context.getSharedPreferences("athena.alarms",Context.MODE_PRIVATE)
    private val manager=context.getSystemService(AlarmManager::class.java)
    private val audio=context.getSystemService(AudioManager::class.java)
    private val handler=Handler(Looper.getMainLooper())
    private var player: MediaPlayer?=null
    private var focus: AudioFocusRequest?=null
    private var wakeLock: PowerManager.WakeLock?=null
    private val stopAfterTimeout=Runnable {dismiss()}
    var alarms by mutableStateOf(read()); private set
    var active by mutableStateOf<LocalAlarm?>(null);private set
    private fun read(): List<LocalAlarm> = runCatching {
        val json=JSONArray(prefs.getString("alarms","[]"))
        (0 until json.length()).map {LocalAlarm.from(json.getJSONObject(it))}
    }.getOrDefault(emptyList())
    private fun persist() {check(prefs.edit().putString("alarms",JSONArray().apply {alarms.forEach {put(it.json())}}.toString()).commit())}
    private fun pending(id: String): PendingIntent {
        val intent=Intent(context,AlarmReceiver::class.java).setAction("de.athena.ALARM")
            .setData(Uri.parse("athena://alarm/$id")).putExtra("alarmId",id)
        return PendingIntent.getBroadcast(context,0,intent,PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
    private fun schedule(alarm: LocalAlarm) {
        manager.cancel(pending(alarm.id))
        if(alarm.enabled) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,alarm.next,pending(alarm.id))
    }
    fun restoreSchedules() {
        val now=System.currentTimeMillis()
        alarms=read().map {a ->
            if(!a.enabled || a.next>now) a
            else if(now-a.next<=5*60_000L) a.copy(next=now+1000L)
            else if(a.repeatDays.isNotEmpty()) a.copy(next=nextAlarmTime(a.hour,a.minute,a.zone,now,a.repeatDays))
            else a.copy(enabled=false)
        }
        alarms.forEach(::schedule);persist()
    }
    fun create(hour: Int,minute: Int,label: String,daily: Boolean,zone: String,volume: Int=75): LocalAlarm {
        require(alarms.size<32) {"Maximal 32 Wecker"}
        val candidate=LocalAlarm(UUID.randomUUID().toString(),label.trim(),hour,minute,daily,true,zone,nextAlarmTime(hour,minute,zone,System.currentTimeMillis()),volume)
        val valid=LocalAlarm.from(candidate.json())
        schedule(valid);alarms=alarms+valid;persist()
        return valid
    }
    fun toggle(id: String,enabled: Boolean) {
        require(alarms.any {it.id==id}) {"Wecker nicht gefunden"}
        alarms=alarms.map {a -> if(a.id==id) a.copy(enabled=enabled,next=if(enabled)nextAlarmTime(a.hour,a.minute,a.zone,System.currentTimeMillis(),a.repeatDays) else a.next) else a}
        alarms.first {it.id==id}.let(::schedule);persist()
    }
    fun remove(id: String) {
        require(alarms.any {it.id==id}) {"Wecker nicht gefunden"}
        manager.cancel(pending(id));alarms=alarms.filterNot {it.id==id};persist()
        if(active?.id==id)dismiss()
    }
    fun fire(id: String) {
        val alarm=alarms.firstOrNull {it.id==id && it.enabled} ?: return
        val now=System.currentTimeMillis()
        if(alarm.next>now+3000L || now-alarm.next>5*60_000L)return
        alarms=alarms.map {a -> if(a.id!=id)a else if(a.repeatDays.isNotEmpty())a.copy(next=nextDailyAlarmTime(a.hour,a.minute,a.zone,now,a.repeatDays)) else a.copy(enabled=false)}
        persist();alarms.first {it.id==id}.let(::schedule)
        dismiss();active=alarm
        check(prefs.edit().putString("ringing",alarm.json().toString()).putLong("ringingSince",now).commit())
        play(alarm)
    }
    fun resumeRinging() {
        val raw=prefs.getString("ringing",null) ?: return
        val elapsed=System.currentTimeMillis()-prefs.getLong("ringingSince",0L)
        if(elapsed !in 0..5*60_000L) {dismiss();return}
        runCatching {LocalAlarm.from(JSONObject(raw))}.onSuccess {active=it;play(it,5*60_000L-elapsed)}.onFailure {dismiss()}
    }
    private fun play(alarm: LocalAlarm,duration: Long=5*60_000L) {
        val attributes=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        focus=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener {}.build().also {audio.requestAudioFocus(it)}
        wakeLock=context.getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Athena:alarm")
            .also {it.acquire(duration+5000)}
        handler.removeCallbacks(stopAfterTimeout);handler.postDelayed(stopAfterTimeout,duration)
        val next=MediaPlayer();player=next
        try {
            next.setAudioAttributes(attributes)
            context.assets.openFd("chime-two-note.wav").use {next.setDataSource(it.fileDescriptor,it.startOffset,it.length)}
            next.isLooping=true;val gain=alarm.volume/100f;next.setVolume(gain,gain)
            next.setOnPreparedListener {if(player===it)it.start()}
            next.setOnErrorListener {_,_,_ -> dismiss();true};next.prepareAsync()
        } catch(error: Exception) {dismiss();throw error}
    }
    fun dismiss() {
        handler.removeCallbacks(stopAfterTimeout)
        player?.let {runCatching {it.release()}};player=null
        focus?.let {audio.abandonAudioFocusRequest(it)};focus=null
        wakeLock?.let {if(it.isHeld)it.release()};wakeLock=null
        active=null;check(prefs.edit().remove("ringing").remove("ringingSince").commit())
    }
    fun snooze() {
        val alarm=active ?: return
        val snoozed=alarm.copy(enabled=true,next=System.currentTimeMillis()+5*60_000L)
        schedule(snoozed);alarms=alarms.map {if(it.id==alarm.id)snoozed else it};persist();dismiss()
    }
    private fun receipts()=JSONObject(prefs.getString("commandReceipts","{}")!!)
    fun acknowledgements()=JSONArray().apply {val all=receipts();all.keys().forEach {put(all.getJSONObject(it))}}
    fun forgetAcknowledged(ids: JSONArray) {
        val all=receipts();for(i in 0 until ids.length())all.remove(ids.getString(i))
        check(prefs.edit().putString("commandReceipts",all.toString()).commit())
    }
    fun applyRemote(command: JSONObject) {
        val requestId=command.getString("request_id")
        require(requestId.matches(Regex("[a-zA-Z0-9-]{1,64}")))
        val all=receipts();if(all.has(requestId))return
        check(all.length()<128) {"Zu viele unbestätigte Weckerbefehle"}
        val receipt=JSONObject().put("request_id",requestId)
        runCatching {
            require(command.getDouble("expires_at")*1000>System.currentTimeMillis()) {"Weckerbefehl abgelaufen"}
            when(command.getString("command")) {
                "create_alarm", "update_alarm" -> {
                    val id=if(command.getString("command")=="create_alarm")"ha-$requestId" else command.getString("alarm_id")
                    val existing=alarms.firstOrNull {it.id==id}
                    if(command.getString("command")=="update_alarm")require(existing!=null) {"Wecker wurde lokal gelöscht"}
                    // A deterministic create id also prevents duplicates after a crash between scheduling and receipt storage.
                    if(command.getString("command")=="create_alarm" && existing!=null) {
                        schedule(existing)
                    } else {
                        require(existing!=null || alarms.size<32)
                        val time=command.getString("time").split(":");require(time.size==2)
                        val names=listOf("mon","tue","wed","thu","fri","sat","sun")
                        val days=command.getJSONArray("repeat_days").let {a -> (0 until a.length()).map {names.indexOf(a.getString(it))+1}.distinct().sorted()}
                        val next=command.getLong("scheduled_for")
                        require(next>System.currentTimeMillis() && next-System.currentTimeMillis()<=8*86400_000L) {"Weckzeit abgelaufen"}
                        val valid=LocalAlarm.from(LocalAlarm(id,command.optString("label"),time[0].toInt(),time[1].toInt(),days.size==7,command.optBoolean("enabled",existing?.enabled ?: true),command.getString("zone"),next,command.optInt("volume",75),days).json())
                        alarms=alarms.filterNot {it.id==id}+valid;persist();schedule(valid)
                    }
                    receipt.put("alarm_id",id)
                }
                "set_alarm_enabled" -> toggle(command.getString("alarm_id"),command.getBoolean("enabled"))
                "delete_alarm" -> command.getString("alarm_id").let {id -> if(alarms.any {it.id==id})remove(id)}
                "dismiss", "snooze" -> {
                    val id=command.optString("alarm_id")
                    if(active?.id==id) {if(command.getString("command")=="snooze")snooze() else dismiss()}
                }
                else -> error("Unbekannter Weckerbefehl")
            }
        }.onSuccess {receipt.put("ok",true)}.onFailure {receipt.put("ok",false).put("error",it.message ?: "Weckerbefehl fehlgeschlagen")}
        all.put(requestId,receipt)
        check(prefs.edit().putString("commandReceipts",all.toString()).commit())
    }
    fun json()=JSONObject().put("items",JSONArray().apply {alarms.forEach {put(it.json())}}).put("active",active?.json() ?: JSONObject.NULL)
    fun close() {dismiss()}
}

class AlarmReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context,intent: Intent) {
        if(intent.action!="de.athena.ALARM")return
        val id=intent.getStringExtra("alarmId") ?: return
        context.startForegroundService(Intent(context,AthenaService::class.java).setAction("de.athena.ALARM").putExtra("alarmId",id))
    }
}
