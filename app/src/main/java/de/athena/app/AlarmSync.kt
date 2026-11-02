package de.athena.app

import androidx.compose.runtime.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** Independent protocol client. No Android code from HA Alarm Clock is included. */
class AlarmSync(private val runtime: AthenaRuntime) {
    var status by mutableStateOf("Weckersynchronisierung wartet");private set
    var lastSuccess by mutableLongStateOf(0L);private set
    private var requested=true
    fun wake() {requested=true}
    private fun snapshot(): JSONObject {
        val days=listOf("mon","tue","wed","thu","fri","sat","sun")
        val enabled=runtime.alarms.alarms.filter {it.enabled}.minByOrNull {it.next}
        fun time(a: LocalAlarm)="%02d:%02d".format(java.util.Locale.ROOT,a.hour,a.minute)
        val active=runtime.alarms.active
        return JSONObject().put("protocol",2).put("device_id",runtime.store.deviceId).put("device_name",runtime.config.name)
            .put("zone",runtime.timeZone).put("voice_satellite",runtime.config.voiceSatellite)
            .put("alarms",JSONArray().apply {runtime.alarms.alarms.forEach {a -> put(JSONObject()
                .put("id",a.id).put("label",a.label).put("time",time(a)).put("enabled",a.enabled)
                .put("repeat",a.repeatDays.joinToString(",") {days[it-1]})
                .put("next_trigger",if(a.enabled)Instant.ofEpochMilli(a.next).toString() else JSONObject.NULL)
                .put("volume",a.volume).put("zone",a.zone))}})
            .put("timers",JSONArray())
            .put("ringing",JSONObject().put("active",active!=null).put("alarm_id",active?.id ?: JSONObject.NULL)
                .put("label",active?.label ?: "").put("time",active?.let(::time) ?: ""))
            .put("next_alarm",JSONObject().put("alarm_id",enabled?.id ?: JSONObject.NULL)
                .put("label",enabled?.label ?: "").put("trigger_at",enabled?.let {Instant.ofEpochMilli(it.next).toString()} ?: JSONObject.NULL))
            .put("acks",runtime.alarms.acknowledgements())
    }
    fun start() {runtime.scope.launch {
        var previous="";var attemptedAt=0L
        while(isActive) {
            if(!runtime.config.alarmSync) {status="Weckersynchronisierung ausgeschaltet";previous="";requested=true}
            else if(!runtime.connected)status="Wecker lokal gespeichert · HA nicht verbunden"
            else {
                val payload=snapshot();val now=System.currentTimeMillis()
                if(requested || payload.toString()!=previous || now-attemptedAt>=15000L) {
                    requested=false;attemptedAt=now
                    try {
                        val result=runtime.ha.syncAlarms(payload)
                        runtime.alarms.forgetAcknowledged(result.getJSONArray("acknowledged"))
                        val commands=result.getJSONArray("commands")
                        for(i in 0 until commands.length())runtime.alarms.applyRemote(commands.getJSONObject(i))
                        previous=payload.toString();lastSuccess=System.currentTimeMillis()
                        status=if(commands.length()>0)"Weckeränderung wird bestätigt" else "Wecker mit HA synchronisiert"
                    } catch(error: CancellationException) {throw error}
                    catch(error: Exception) {status=error.message ?: "Weckersynchronisierung fehlgeschlagen";previous=payload.toString()}
                }
            }
            delay(1000)
        }
    }}
}
