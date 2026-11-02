package de.athena.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

enum class VoicePhase(val label: String) {
    IDLE("Bereit"), LISTENING("Ich höre zu …"), THINKING("Ich verarbeite …"),
    SPEAKING("Ich antworte …"), MUTED("Mikrofon stumm"), OFFLINE("Sprachdienst nicht verbunden")
}
data class VoiceState(val phase: VoicePhase = VoicePhase.OFFLINE, val level: Float = 0f, val sequence: Long = 0)
data class Entity(val id: String, val state: String, val attributes: JSONObject = JSONObject(), val registryName: String = "", val deviceName: String = "") {
    val name get() = attributes.optString("friendly_name").takeIf {it.isNotBlank() && it != "null"}
        ?: registryName.takeIf {it.isNotBlank()} ?: id.substringAfter('.').replace('_', ' ')
    val domain get() = id.substringBefore('.')
    val available get() = state != "unavailable" && state != "unknown"
    fun json() = JSONObject().put("entity_id", id).put("name", name).put("deviceName",deviceName).put("state", state).put("attributes", attributes)
    companion object { fun from(j: JSONObject) = Entity(j.getString("entity_id"), j.getString("state"), j.optJSONObject("attributes") ?: JSONObject()) }
}
fun JSONArray.strings() = (0 until length()).map { getString(it) }
data class AppConfig(
    val name: String = "Wohnzimmer", val haUrl: String = "", val theme: String = "light",
    val animation: String = "wave", val favorites: List<String> = emptyList(),
    val domains: List<String> = listOf("light", "switch", "climate", "cover", "lock", "sensor", "scene"),
    val areas: List<String> = emptyList(), val camera: String = "", val doorbell: String = "",
    val doorbellValue: String = "on", val cameraSeconds: Int = 45,
    val weather: String = "", val autoStart: Boolean = false, val reducedMotion: Boolean = false,
    val ringtone: String = "two-note", val ringVolume: Int = 65,
    val homeGrouping: String = "none", val language: String = "de",
    val nightMode: String = "off", val nightLux: Float = 5f, val nightDelay: Int = 30,
    val nightBrightness: Int = 2, val nightShowDate: Boolean = false, val nightShowStatus: Boolean = true,
    val alarmSync: Boolean = true, val voiceSatellite: String = ""
) {
    fun json(): JSONObject = JSONObject().put("schemaVersion", 1).put("name", name).put("haUrl", haUrl)
        .put("theme", theme).put("animation", animation).put("favorites", JSONArray(favorites)).put("domains", JSONArray(domains))
        .put("areas", JSONArray(areas)).put("camera", camera).put("doorbell", doorbell).put("doorbellValue", doorbellValue)
        .put("cameraSeconds", cameraSeconds).put("weather", weather).put("autoStart", autoStart).put("reducedMotion", reducedMotion)
        .put("ringtone",ringtone).put("ringVolume",ringVolume)
        .put("homeGrouping",homeGrouping)
        .put("language",language)
        .put("nightMode",nightMode).put("nightLux",nightLux).put("nightDelay",nightDelay)
        .put("nightBrightness",nightBrightness).put("nightShowDate",nightShowDate).put("nightShowStatus",nightShowStatus)
        .put("alarmSync",alarmSync).put("voiceSatellite",voiceSatellite)
    companion object {
        fun from(j: JSONObject): AppConfig {
            require(j.optInt("schemaVersion", 1) == 1) { "Unbekannte Konfigurationsversion" }
            val url = j.optString("haUrl").trim().trimEnd('/')
            if (url.isNotEmpty()) {
                val uri = URI(url)
                require(uri.scheme in listOf("http", "https") && !uri.host.isNullOrEmpty() && uri.userInfo == null && uri.query == null && uri.fragment == null && uri.path.orEmpty().isEmpty()) { "HA-Adresse muss http(s)://host[:port] sein" }
            }
            val theme = j.optString("theme", "light")
            require(theme in listOf("light", "dark")) { "Unbekanntes Theme" }
            val anim = j.optString("animation", "wave")
            require(anim in listOf("wave", "pulse")) { "Unbekannte Animation" }
            val seconds = j.optInt("cameraSeconds", 45)
            require(seconds in 5..300) { "Kameradauer muss zwischen 5 und 300 Sekunden liegen" }
            val ringtone=j.optString("ringtone","two-note");val ringVolume=j.optInt("ringVolume",65)
            require(ringtone in setOf("none","two-note","soft")) {"Unbekannter Klingelton"}
            require(ringVolume in 0..100) {"Klingellautstärke muss zwischen 0 und 100 liegen"}
            val grouping=j.optString("homeGrouping","none")
            require(grouping in setOf("none","area","category","area_category")) {"Unbekannte Startseiten-Gruppierung"}
            val language=j.optString("language","de")
            require(language in setOf("de","en")) {"Unsupported language"}
            val nightMode=j.optString("nightMode","off");val nightLux=j.optDouble("nightLux",5.0).toFloat()
            val nightDelay=j.optInt("nightDelay",30);val nightBrightness=j.optInt("nightBrightness",2)
            require(nightMode in setOf("off","auto","always")) {"Ungültiger Nachtmodus"}
            require(nightLux.isFinite() && nightLux in 0.1f..1000f) {"Lichtschwelle muss zwischen 0,1 und 1000 Lux liegen"}
            require(nightDelay in 0..600 && nightBrightness in 1..20) {"Ungültige Nachteinstellungen"}
            val name = j.optString("name", "Wohnzimmer").trim()
            require(name.length in 1..60) { "Name muss 1–60 Zeichen lang sein" }
            fun strings(key: String, fallback: List<String> = emptyList()) = j.optJSONArray(key)?.strings()?.distinct() ?: fallback
            val satellite=j.optString("voiceSatellite")
            require(satellite.isEmpty() || satellite.matches(Regex("assist_satellite\\.[a-z0-9_]+"))) {"Ungültiger Sprachsatellit"}
            val camera = j.optString("camera")
            require(camera.isEmpty() || camera.matches(Regex("camera\\.[a-z0-9_]+"))) { "Ungültige Kamera" }
            val doorbell = j.optString("doorbell")
            require(doorbell.isEmpty() || doorbell.matches(Regex("(binary_sensor|event|sensor|input_boolean)\\.[a-z0-9_]+"))) { "Ungültige Klingel-Entität" }
            return AppConfig(name, url, theme, anim, strings("favorites"), strings("domains", AppConfig().domains), strings("areas"), camera,
                doorbell, j.optString("doorbellValue", "on"), seconds, j.optString("weather"), j.optBoolean("autoStart"), j.optBoolean("reducedMotion"),ringtone,ringVolume,grouping,language,
                nightMode,nightLux,nightDelay,nightBrightness,j.optBoolean("nightShowDate"),j.optBoolean("nightShowStatus",true),j.optBoolean("alarmSync",true),satellite)
        }
    }
}

data class HomeSection(val id: String, val title: String, val entities: List<Entity>)
data class HomeCategory(val id: String, val name: String, val order: Int)
fun homeCategory(domain: String) = when(domain) {
    "light" -> HomeCategory("lighting","Beleuchtung",0)
    "climate" -> HomeCategory("heating","Heizung",1)
    "cover" -> HomeCategory("shading","Beschattung",2)
    "switch","input_boolean" -> HomeCategory("switches","Schalter",3)
    "lock" -> HomeCategory("access","Zugang",4)
    "media_player" -> HomeCategory("media","Medien",5)
    "scene" -> HomeCategory("scenes","Szenen",6)
    "sensor","binary_sensor" -> HomeCategory("sensors","Sensoren",7)
    else -> HomeCategory("other","Weitere Geräte",8)
}

/** Groups an already filtered favorite list. Never expands it or duplicates a device. */
fun groupHomeEntities(entities: List<Entity>, grouping: String, entityAreas: Map<String,String>, areas: Map<String,String>,translate:(String)->String={it}): List<HomeSection> {
    if(entities.isEmpty()) return emptyList()
    if(grouping=="none") return listOf(HomeSection("all","Favoriten",entities))
    val useAreas=grouping in setOf("area","area_category")
    val useCategories=grouping in setOf("category","area_category")
    require(useAreas||useCategories)
    fun areaId(e: Entity) = entityAreas[e.id]?.takeIf {areas.containsKey(it)}
    val grouped=entities.groupBy {e ->
        (if(useAreas) "area:${areaId(e).orEmpty()}" else "") +
            (if(useCategories) "/category:${homeCategory(e.domain).id}" else "")
    }
    return grouped.entries.sortedWith(compareBy<Map.Entry<String,List<Entity>>> {
        if(useAreas) areaId(it.value.first())==null else false
    }.thenBy {if(useAreas) areas[areaId(it.value.first())].orEmpty().lowercase(java.util.Locale.GERMAN) else ""}
        .thenBy {if(useCategories) homeCategory(it.value.first().domain).order else 0})
        .map {(id,list) ->
            val titles=mutableListOf<String>()
            if(useAreas) titles+=areas[areaId(list.first())] ?: translate("Ohne Raum")
            if(useCategories) titles+=translate(homeCategory(list.first().domain).name)
            HomeSection(id,titles.joinToString(" · "),list)
        }
}

/** Only actual subscribed changes may trigger a doorbell; a snapshot/reconnect never does. */
fun isDoorbellChange(config: AppConfig, old: Entity?, new: Entity?): Boolean {
    if (old == null || new == null || new.id != config.doorbell || !old.available || !new.available || old.state == new.state) return false
    return if (new.domain == "event") true else new.state == config.doorbellValue
}

data class PackageManifest(val id: String, val kind: String, val apiVersion: Int, val version: String) {
    companion object {
        fun parse(j: JSONObject): PackageManifest {
            val id = j.getString("id"); val kind = j.getString("kind"); val api = j.getInt("apiVersion")
            require(id.matches(Regex("[a-z][a-z0-9.-]{2,79}")))
            require(kind in listOf("theme", "animation", "layout"))
            require(api == 1) { "Paket benötigt eine andere Athena-Schnittstelle" }
            return PackageManifest(id, kind, api, j.getString("version"))
        }
    }
}
