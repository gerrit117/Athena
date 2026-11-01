package de.athena.app

import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/** Device-local administration. The HA credential never appears in responses or exports. */
class WebServer(private val runtime: AthenaRuntime): NanoHTTPD(26804) {
    private val sessions = ConcurrentHashMap<String, Long>().apply {
        runCatching {val saved=JSONObject(runtime.store.browserSessions); saved.keys().forEach { id -> if(saved.getLong(id)>System.currentTimeMillis()) put(id,saved.getLong(id)) }}
    }
    private fun sessionHash(raw: String) = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") {"%02x".format(it)}
    @Synchronized private fun saveSessions() {runtime.store.browserSessions=JSONObject(sessions.toMap()).toString()}
    private val attempts = ConcurrentHashMap<String, Pair<Long, Int>>()
    override fun serve(session: IHTTPSession): Response {
        return try {
            val host = session.headers["host"].orEmpty()
            val hostname = runCatching { URI("http://$host").host }.getOrNull()
            val own = URI(runtime.address()).host
            if (hostname !in setOf(own, "127.0.0.1", "localhost", "::1")) return reply(400, JSONObject().put("error", "Unbekannter Host"))
            val origin = session.headers["origin"]
            if (origin != null && origin != "http://$host") return reply(403, JSONObject().put("error", "Ursprung nicht erlaubt"))
            if (session.method == Method.GET && session.uri in listOf("/", "/index.html", "/app.js", "/style.css", "/i18n/de.json", "/i18n/en.json")) {
                val file = when(session.uri) { "/", "/index.html" -> "index.html"; else -> session.uri.drop(1) }
                val mime = when(file) { "app.js" -> "application/javascript"; "style.css" -> "text/css"; "i18n/de.json","i18n/en.json" -> "application/json"; else -> "text/html" }
                return secure(newFixedLengthResponse(Response.Status.OK, "$mime; charset=utf-8", runtime.context.assets.open(file).bufferedReader().use { it.readText() }))
            }
            if (session.uri == "/api/login" && session.method == Method.POST) {
                val now = System.currentTimeMillis(); val ip = session.remoteIpAddress
                val previous = attempts[ip]
                val count = if (previous != null && now - previous.first < 60000) previous.second else 0
                if (count >= 8) return reply(429, JSONObject().put("error", "Bitte eine Minute warten"))
                val supplied = body(session).optString("key")
                if (!main {runtime.consumePin(supplied)}) {
                    attempts[ip] = (previous?.first?.takeIf { now - it < 60000 } ?: now) to (count + 1)
                    return reply(401, JSONObject().put("error", "Code ungültig oder abgelaufen. Bei Bedarf am Echo einen neuen Code anzeigen."))
                }
                attempts.remove(ip)
                sessions.entries.removeIf { now > it.value }
                if (sessions.size >= 20) sessions.clear()
                val id = ByteArray(32).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
                sessions[sessionHash(id)] = now + 30 * 86400000L
                saveSessions()
                return reply(200, JSONObject().put("ok", true)).apply { addHeader("Set-Cookie", "athena=$id; HttpOnly; SameSite=Strict; Path=/; Max-Age=2592000") }
            }
            val cookie = session.headers["cookie"].orEmpty().split(';').map { it.trim() }.firstOrNull { it.startsWith("athena=") }?.substringAfter('=')
            if (cookie == null || (sessions[sessionHash(cookie)] ?: 0) < System.currentTimeMillis()) return reply(401, JSONObject().put("error", "Bitte anmelden"))
            if (session.method != Method.GET && session.headers["x-athena-request"] != "1") return reply(403, JSONObject().put("error", "Anfrage nicht erlaubt"))
            when {
                session.uri == "/api/logout" && session.method == Method.POST -> {
                    sessions.remove(sessionHash(cookie)); saveSessions()
                    reply(200, JSONObject().put("ok", true)).apply { addHeader("Set-Cookie", "athena=; HttpOnly; SameSite=Strict; Path=/; Max-Age=0") }
                }
                session.uri == "/api/state" && session.method == Method.GET -> main {
                    reply(200, JSONObject().put("status", runtime.statusJson()).put("config", runtime.configJson())
                        .put("entities", runtime.entitiesJson()).put("areas", JSONObject(runtime.areaNames)))
                }
                session.uri == "/api/config" && session.method == Method.POST -> {
                    val data = body(session)
                    main { runtime.save(data); reply(200, JSONObject().put("ok", true).put("config", runtime.configJson())) }
                }
                session.uri == "/api/view" && session.method == Method.POST -> {
                    val page=body(session).getString("page")
                    require(page in setOf("home", "camera", "voice"))
                    main {
                        if(page=="camera") {
                            require(runtime.config.camera.isNotEmpty()) {"Bitte zuerst eine Kamera wählen"}
                            runtime.shownCamera=runtime.config.camera
                        }
                        runtime.page=page
                        reply(200,JSONObject().put("ok",true))
                    }
                }
                session.uri == "/api/ringtone/test" && session.method == Method.POST -> {
                    val data=body(session)
                    main {runtime.testChime(data.getString("ringtone"),data.getInt("ringVolume"));reply(200,JSONObject().put("ok",true))}
                }
                session.uri == "/api/export" && session.method == Method.GET -> main {
                    reply(200, runtime.config.json()).apply { addHeader("Content-Disposition", "attachment; filename=athena-config.json") }
                }
                else -> reply(404, JSONObject().put("error", "Nicht gefunden"))
            }
        } catch (e: IllegalArgumentException) {
            reply(400, JSONObject().put("error", e.message ?: "Ungültige Eingabe"))
        } catch (_: Exception) {
            reply(500, JSONObject().put("error", "Anfrage konnte nicht verarbeitet werden"))
        }
    }
    private fun body(session: IHTTPSession): JSONObject {
        val size = session.headers["content-length"]?.toLongOrNull() ?: throw IllegalArgumentException("Inhaltslänge fehlt")
        require(size in 1..65536) { "Anfrage ist zu groß" }
        require(session.headers["content-type"].orEmpty().startsWith("application/json")) { "JSON erwartet" }
        val files = mutableMapOf<String, String>(); session.parseBody(files)
        return JSONObject(files["postData"] ?: "{}")
    }
    private fun <T> main(block: suspend () -> T): T = runBlocking { withTimeout(15000) { withContext(Dispatchers.Main) { block() } } }
    private fun reply(code: Int, json: JSONObject): Response = secure(newFixedLengthResponse(Response.Status.lookup(code) ?: Response.Status.INTERNAL_ERROR, "application/json; charset=utf-8", json.toString()))
    private fun secure(r: Response): Response = r.apply {
        addHeader("Cache-Control", "no-store")
        addHeader("X-Content-Type-Options", "nosniff")
        addHeader("X-Frame-Options", "DENY")
        addHeader("Referrer-Policy", "no-referrer")
        addHeader("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'")
    }
}
