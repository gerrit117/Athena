package de.athena.app

import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class HaClient(
    private val scope: CoroutineScope, private val changed: (List<Entity>) -> Unit,
    private val status: (String, Boolean) -> Unit, private val event: (Entity?, Entity?) -> Unit,
    private val areasChanged: (Map<String, String>, Map<String, String>) -> Unit,
    private val timezoneChanged: (String) -> Unit, private val alarmHint: () -> Unit
) {
    val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).pingInterval(25, TimeUnit.SECONDS).build()
    @Volatile private var socket: WebSocket? = null
    private var generation = 0
    private var reconnect: Job? = null
    private var base = ""; private var token = ""
    private var retry = 1L
    private var authenticated = false
    private val ids = AtomicInteger(1)
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<JSONObject>>()
    private val entities = ConcurrentHashMap<String, Entity>()
    private val entityNames = ConcurrentHashMap<String, Pair<String,String>>()
    private val duringSnapshot = mutableListOf<Pair<Entity?, Entity?>>()
    private var loadingSnapshot = false

    fun connect(url: String, credential: String) {
        disconnect(); entityNames.clear(); base = url; token = credential; retry = 1
        if (url.isBlank() || credential.isBlank()) { status("Home Assistant einrichten", false); return }
        open(generation)
    }
    fun disconnect() {
        generation++; reconnect?.cancel(); socket?.cancel(); socket = null; authenticated = false
        pending.values.forEach { it.cancel() }; pending.clear()
    }
    private fun open(gen: Int) {
        if (gen != generation) return
        status("Verbinde …", false)
        val request = Request.Builder().url(base.replaceFirst("http", "ws") + "/api/websocket").build()
        socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (gen != generation || socket !== webSocket) return
                runCatching { receive(webSocket, JSONObject(text), gen) }.onFailure { status("HA-Protokollfehler", false) }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { if (gen == generation && socket === webSocket) lost(gen) }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { if (gen == generation && socket === webSocket) lost(gen) }
        })
    }
    private fun lost(gen: Int) {
        authenticated = false; pending.values.forEach { it.cancel() }; pending.clear()
        status("HA nicht verbunden · erneuter Versuch", false)
        reconnect?.cancel(); reconnect = scope.launch { delay(retry * 1000); retry = (retry * 2).coerceAtMost(30); open(gen) }
    }
    private fun receive(ws: WebSocket, j: JSONObject, gen: Int) {
        when (j.optString("type")) {
            "auth_required" -> ws.send(JSONObject().put("type", "auth").put("access_token", token).toString())
            "auth_invalid" -> { status("HA-Anmeldung fehlgeschlagen · Token prüfen", false); socket = null; ws.close(1000, null) }
            "auth_ok" -> {
                authenticated = true; retry = 1
                scope.launch {
                    try {
                        synchronized(duringSnapshot) { loadingSnapshot = true; duringSnapshot.clear() }
                        request(JSONObject().put("type", "subscribe_events").put("event_type", "state_changed"))
                        val result = request(JSONObject().put("type", "get_states")).getJSONArray("result")
                        if (gen != generation) return@launch
                        synchronized(duringSnapshot) {
                            entities.clear()
                            for (i in 0 until result.length()) Entity.from(result.getJSONObject(i)).let { entities[it.id] = it }
                            duringSnapshot.forEach { (old, new) -> applyChange(old, new, true) }
                            duringSnapshot.clear(); loadingSnapshot = false
                        }
                        emit(); status("Verbunden", true)
                        runCatching {request(JSONObject().put("type", "get_config")).getJSONObject("result").optString("time_zone").takeIf {it.isNotBlank()}?.let(timezoneChanged)}
                        runCatching {request(JSONObject().put("type","subscribe_events").put("event_type","ha_alarmclock_command"))}
                        alarmHint()
                        loadAreas()
                    } catch (_: Exception) { if (gen == generation) { ws.cancel(); lost(gen) } }
                }
            }
            "result" -> pending.remove(j.optInt("id"))?.complete(j)
            "event" -> {
                if(j.optJSONObject("event")?.optString("event_type")=="ha_alarmclock_command") {alarmHint();return}
                val data = j.optJSONObject("event")?.optJSONObject("data") ?: return
                val old = data.optJSONObject("old_state")?.let(Entity::from)
                val new = data.optJSONObject("new_state")?.let(Entity::from)
                synchronized(duringSnapshot) {
                    if (loadingSnapshot) duringSnapshot.add(old to new) else { applyChange(old, new, true); emit() }
                }
            }
        }
    }
    private fun applyChange(old: Entity?, new: Entity?, notify: Boolean) {
        if (new != null) entities[new.id] = new else if (old != null) entities.remove(old.id)
        if (notify) event(old, new)
    }
    private fun emit() = changed(entities.values.map { e -> entityNames[e.id]?.let {e.copy(registryName=it.first,deviceName=it.second)} ?: e }.sortedBy { it.name.lowercase() })
    suspend fun request(body: JSONObject): JSONObject {
        check(authenticated) { "Home Assistant ist nicht verbunden" }
        val id = ids.getAndIncrement(); val deferred = CompletableDeferred<JSONObject>(); pending[id] = deferred
        try {
            check(socket?.send(body.put("id", id).toString()) == true)
            val result = withTimeout(10000) { deferred.await() }
            check(result.optBoolean("success")) { result.optJSONObject("error")?.optString("message") ?: "HA-Anfrage fehlgeschlagen" }
            return result
        } finally { pending.remove(id) }
    }
    suspend fun action(entity: Entity, action: String = "toggle", data: JSONObject = JSONObject()) {
        val allowed = when (entity.domain) {
            "light", "switch", "input_boolean" -> setOf("toggle", "turn_on", "turn_off")
            "cover" -> setOf("open_cover", "close_cover", "stop_cover")
            "climate" -> setOf("set_temperature")
            "scene" -> setOf("turn_on")
            else -> emptySet()
        }
        require(action in allowed) { "Diese Aktion wird nicht unterstützt" }
        check(entity.available)
        request(JSONObject().put("type", "call_service").put("domain", entity.domain).put("service", action)
            .put("target", JSONObject().put("entity_id", entity.id)).put("service_data", data))
    }
    private suspend fun loadAreas() = runCatching {
        val areas = request(JSONObject().put("type", "config/area_registry/list")).getJSONArray("result")
        val devices = request(JSONObject().put("type", "config/device_registry/list")).getJSONArray("result")
        val registry = request(JSONObject().put("type", "config/entity_registry/list")).getJSONArray("result")
        val areaNames = mutableMapOf<String, String>(); val deviceAreas = mutableMapOf<String, String>(); val entityAreas = mutableMapOf<String, String>()
        val deviceNames=mutableMapOf<String,String>()
        for (i in 0 until areas.length()) areas.getJSONObject(i).let { areaNames[it.getString("area_id")] = it.getString("name") }
        for (i in 0 until devices.length()) devices.getJSONObject(i).let {
            if (!it.isNull("area_id")) deviceAreas[it.getString("id")] = it.getString("area_id")
            deviceNames[it.getString("id")]=listOf("name_by_user","name").firstNotNullOfOrNull {key -> it.optString(key).takeIf {value -> value.isNotBlank() && value!="null"}} ?: ""
        }
        entityNames.clear()
        for (i in 0 until registry.length()) registry.getJSONObject(i).let {
            val area = if (!it.isNull("area_id")) it.getString("area_id") else deviceAreas[it.optString("device_id")]
            if (area != null) entityAreas[it.getString("entity_id")] = area
            val device=deviceNames[it.optString("device_id")] ?: ""
            val explicit=it.optString("name").takeUnless {v -> v.isBlank() || v=="null"}
            val original=it.optString("original_name").takeUnless {v -> v.isBlank() || v=="null"} ?: ""
            val label=explicit ?: listOf(device,original).filter {v -> v.isNotBlank()}.distinct().joinToString(" · ")
            entityNames[it.getString("entity_id")]=label to device
        }
        areasChanged(areaNames, entityAreas)
        emit()
    }
    suspend fun syncAlarms(payload: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        check(authenticated)
        val req=Request.Builder().url("$base/api/ha_alarmclock/sync").header("Authorization","Bearer $token")
            .post(payload.toString().toRequestBody("application/json".toMediaType())).build()
        http.newCall(req).execute().use {response ->
            if(response.code==404 || response.code==410)error("HA Alarm Clock · Athena ist in HA nicht eingerichtet")
            if(response.code==403)error("Für die Weckersynchronisierung wird ein HA-Administratorkonto benötigt")
            check(response.isSuccessful) {"Weckersynchronisierung fehlgeschlagen (${response.code})"}
            JSONObject(response.body!!.string()).also {check(it.optInt("protocol")==2) {"HA Alarm Clock benötigt die Athena-Erweiterung"}}
        }
    }
    fun snapshotRequest(id: String): Request {
        require(id.matches(Regex("camera\\.[a-z0-9_]+")))
        return Request.Builder().url("$base/api/camera_proxy/$id").header("Authorization", "Bearer $token").build()
    }
    suspend fun streamUrl(id: String): String {
        require(id.matches(Regex("camera\\.[a-z0-9_]+")))
        val path = request(JSONObject().put("type", "camera/stream").put("entity_id", id).put("format", "hls")).getJSONObject("result").getString("url")
        val origin = base.toHttpUrl()
        val url = origin.resolve(path) ?: error("Ungültige Stream-Adresse")
        require(url.host == origin.host && url.port == origin.port && url.scheme == origin.scheme) { "Stream-Adresse liegt außerhalb von HA" }
        return url.toString()
    }
    fun authHeaders() = mapOf("Authorization" to "Bearer $token")
}
