package de.athena.app

import android.app.*
import android.content.*
import android.os.IBinder
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.NetworkInterface

class AthenaRuntime(val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val store = ConfigStore(context)
    private val translations=I18n(context)
    fun t(key: String)=translations.text(config.language,key)
    val locale get()=java.util.Locale.forLanguageTag(config.language)
    var config by mutableStateOf(store.config); private set
    var entities by mutableStateOf<List<Entity>>(emptyList()); private set
    var areaNames by mutableStateOf<Map<String, String>>(emptyMap()); private set
    var entityAreas by mutableStateOf<Map<String, String>>(emptyMap()); private set
    var haStatus by mutableStateOf("Home Assistant einrichten"); private set
    var connected by mutableStateOf(false); private set
    var voice by mutableStateOf(VoiceState()); private set
    var page by mutableStateOf("home")
    var shownCamera by mutableStateOf("")
    var ringActive by mutableStateOf(false)
    var message by mutableStateOf("")
    var webStatus by mutableStateOf(""); private set
    val pairing = PairingGate()
    var pairingCode by mutableStateOf(""); private set
    var timeZone by mutableStateOf(java.util.TimeZone.getDefault().id); private set
    val cameraImage = mutableStateMapOf<String, Bitmap>()
    private var bridgeJob: Job? = null
    private var ringJob: Job? = null
    private var previousPage = "home"
    private var previousCamera = ""
    private val chime=ChimePlayer(context)
    var lux by mutableStateOf<Float?>(null);private set
    var nightActive by mutableStateOf(false);private set
    var nightPeekUntil by mutableLongStateOf(0L);private set
    var nightClock by mutableLongStateOf(0L);private set
    private val nightPolicy=NightPolicy()
    private val ambient=AmbientLight(context) {lux=it}
    val lightSensorAvailable get()=ambient.available
    fun peekDayView() {nightPeekUntil=android.os.SystemClock.elapsedRealtime()+30000L}
    val ha = HaClient(scope,
        { values -> scope.launch { entities = values } },
        { text, ok -> scope.launch { haStatus = text; connected = ok } },
        { old, new -> scope.launch { if (isDoorbellChange(config, old, new)) showDoorbell() } },
        { areas, mapping -> scope.launch { areaNames = areas; entityAreas = mapping } },
        { zone -> scope.launch { timeZone = zone } })
    private val web = WebServer(this)
    fun start() {
        ambient.start()
        scope.launch {while(isActive) {
            nightClock=android.os.SystemClock.elapsedRealtime()
            nightActive=nightPolicy.update(config.nightMode,lux,config.nightLux,config.nightDelay,nightClock)
            delay(500)
        }}
        if (JSONObject(store.browserSessions).length() == 0) newPairing()
        ha.connect(config.haUrl, store.token)
        runCatching { web.start() }.onSuccess { webStatus = "WebUI: ${address()}" }.onFailure { webStatus = "WebUI konnte nicht starten" }
        bridgeJob = scope.launch(Dispatchers.IO) {
            var sequence = 0L
            while (isActive) {
                val next = runCatching {
                    val req = okhttp3.Request.Builder().url("http://127.0.0.1:28931/api/athena/status").build()
                    ha.http.newCall(req).execute().use { response ->
                        check(response.isSuccessful)
                        val j = JSONObject(response.body!!.string())
                        check(j.getInt("schemaVersion") == 1)
                        val phase = if (!j.getBoolean("connected")) VoicePhase.OFFLINE else if (j.getBoolean("muted")) VoicePhase.MUTED
                            else VoicePhase.valueOf(j.getString("state").uppercase())
                        VoiceState(phase, 0f, sequence++)
                    }
                }.getOrDefault(VoiceState())
                withContext(Dispatchers.Main) { voice = next; pairingCode = pairing.visible() }
                delay(180)
            }
        }
    }
    fun newPairing() { pairingCode = pairing.open() }
    fun consumePin(pin: String): Boolean = pairing.consume(pin).also {pairingCode = pairing.visible()}
    fun close() { bridgeJob?.cancel(); ambient.close(); chime.close(); web.stop(); ha.disconnect(); scope.cancel() }
    fun testChime(kind: String=config.ringtone, volume: Int=config.ringVolume) {chime.play(kind,volume)}
    suspend fun save(json: JSONObject) {
        val next = AppConfig.from(json)
        val token = if (json.optBoolean("clearToken")) "" else json.optString("haToken").trim().ifEmpty { store.token }
        val reconnect = next.haUrl != config.haUrl || token != store.token
        withContext(Dispatchers.IO) { if (token != store.token) store.token = token; store.config = next }
        config = next
        if (reconnect) { entities = emptyList(); areaNames = emptyMap(); entityAreas = emptyMap(); cameraImage.clear(); ha.connect(next.haUrl, token) }
    }
    fun updateConfig(transform: (AppConfig) -> AppConfig) { scope.launch { runCatching { save(transform(config).json()) }.onFailure { message = it.message ?: "Speichern fehlgeschlagen" } } }
    fun toggleFavorite(id: String) = updateConfig { c -> c.copy(favorites = if (id in c.favorites) c.favorites - id else c.favorites + id) }
    fun filtered(): List<Entity> = entities.filter { it.domain in config.domains && (config.areas.isEmpty() || entityAreas[it.id] in config.areas) }
    fun favorites(): List<Entity> {
        val allowed = filtered().associateBy { it.id }
        return if (config.favorites.isEmpty()) allowed.values.take(6) else config.favorites.mapNotNull { allowed[it] }
    }
    fun homeSections() = groupHomeEntities(favorites(),config.homeGrouping,entityAreas,areaNames,::t)
    fun action(e: Entity, command: String = "toggle", data: JSONObject = JSONObject()) {
        scope.launch { runCatching { ha.action(e, command, data) }.onFailure { message = it.message ?: "Aktion fehlgeschlagen" } }
    }
    fun showDoorbell() {
        peekDayView()
        runCatching {testChime()}.onFailure {message="Klingelton konnte nicht abgespielt werden"}
        if (config.camera.isEmpty()) return
        if (!ringActive) { previousPage = page; previousCamera = shownCamera }
        ringActive = true; shownCamera = config.camera; page = "camera"
        ringJob?.cancel(); ringJob = scope.launch { delay(config.cameraSeconds * 1000L); closeCamera() }
    }
    fun closeCamera() {
        ringJob?.cancel()
        if (ringActive) { page = previousPage; shownCamera = previousCamera } else { page = "cameras"; shownCamera = "" }
        ringActive = false
    }
    suspend fun snapshot(id: String): Bitmap? = withContext(Dispatchers.IO) {
        if (!connected) return@withContext null
        runCatching { ha.http.newCall(ha.snapshotRequest(id)).execute().use { response ->
            check(response.isSuccessful)
            val body = response.body ?: return@use null
            if (body.contentLength() > 8 * 1024 * 1024) return@use null
            val bytes = body.byteStream().use { input ->
                val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                while(true) {val n=input.read(buffer);if(n<0)break;check(out.size()+n <= 8*1024*1024);out.write(buffer,0,n)}
                out.toByteArray()
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } }.getOrNull()
    }
    fun statusJson(): JSONObject = JSONObject().put("version", BuildConfig.VERSION_NAME).put("haStatus", haStatus).put("connected", connected)
        .put("voice", voice.phase.name).put("address", address()).put("bridgeAvailable", voice.phase != VoicePhase.OFFLINE)
        .put("nightActive",nightActive).put("lux",lux ?: JSONObject.NULL).put("lightSensorAvailable",lightSensorAvailable)
    fun configJson(): JSONObject = config.json().put("hasToken", store.token.isNotBlank())
    fun entitiesJson() = JSONArray().apply { entities.forEach { e -> put(e.json().put("area", entityAreas[e.id] ?: "")) } }
    fun address(): String {
        val ip = runCatching { NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
            .firstOrNull { !it.isLoopbackAddress && it is java.net.Inet4Address }?.hostAddress }.getOrNull() ?: "GERÄTE-IP"
        return "http://$ip:26804"
    }
    companion object {
        @Volatile private var instance: AthenaRuntime? = null
        fun get(context: Context): AthenaRuntime = instance ?: synchronized(this) {
            instance ?: AthenaRuntime(context.applicationContext).also { instance = it; it.start() }
        }
        fun stop() { instance?.close(); instance = null }
    }
}

class AthenaService: Service() {
    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("athena", "Athena", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        startForeground(1, Notification.Builder(this, "athena").setContentTitle("Athena")
            .setContentText("Geräteanzeige und lokale Web-Konfiguration aktiv").setSmallIcon(android.R.drawable.ic_menu_view).setContentIntent(open).build())
        AthenaRuntime.get(this)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if(intent?.getBooleanExtra("boot",false)==true) {
            val runtime=AthenaRuntime.get(this)
            runtime.scope.launch {
                // Let the existing voice adapter finish its own boot activity before showing Athena.
                delay(8000)
                if(runtime.config.autoStart) startActivity(Intent(this@AthenaService,MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            }
        }
        return START_STICKY
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { AthenaRuntime.stop(); super.onDestroy() }
}
class BootReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && ConfigStore(context).config.autoStart) {
            context.startForegroundService(Intent(context, AthenaService::class.java).putExtra("boot",true))
        }
    }
}
