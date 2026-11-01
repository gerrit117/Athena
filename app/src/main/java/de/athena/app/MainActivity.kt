package de.athena.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class MainActivity: ComponentActivity() {
    private var keyboardInset by mutableIntStateOf(0)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Fullscreen Fire OS otherwise pans the complete window and hides every dismiss control.
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        window.decorView.viewTreeObserver.addOnGlobalLayoutListener {
            val rect=android.graphics.Rect()
            window.decorView.getWindowVisibleDisplayFrame(rect)
            val height=window.decorView.rootView.height
            keyboardInset=(height-rect.bottom).takeIf {it>height/5} ?: 0
        }
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        startForegroundService(Intent(this, AthenaService::class.java))
        val runtime = AthenaRuntime.get(this)
        intent.getStringExtra("page")?.takeIf {it in setOf("home", "devices", "cameras", "settings", "voice")}?.let {runtime.page=it}
        setContent { MaterialTheme(colorScheme = ThemeRegistry.find(runtime.config.theme).colors) { AthenaApp(runtime, keyboardInset) } }
    }
}

@Composable fun AthenaApp(r: AthenaRuntime, keyboardInset: Int = 0) {
    val busy = r.voice.phase in setOf(VoicePhase.LISTENING, VoicePhase.THINKING, VoicePhase.SPEAKING)
    val displayed = if (busy && r.page != "camera") "voice" else r.page
    val keyboard=LocalSoftwareKeyboardController.current
    val focus=LocalFocusManager.current
    val inset=with(LocalDensity.current) {keyboardInset.toDp()}
    val night=r.nightActive && r.nightClock>=r.nightPeekUntil && !busy && !r.ringActive && r.page!="settings" && keyboardInset==0
    val activity=LocalActivity.current
    DisposableEffect(activity,night,r.config.nightBrightness) {
        val window=activity?.window
        val previous=window?.attributes?.screenBrightness ?: -1f
        window?.let {w -> w.attributes=w.attributes.apply {screenBrightness=if(night)r.config.nightBrightness/100f else -1f}}
        onDispose {window?.let {w -> w.attributes=w.attributes.apply {screenBrightness=previous}}}
    }
    LaunchedEffect(busy) {if(busy) {focus.clearFocus();keyboard?.hide()}}
    if(night) {NightScreen(r);return}
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize().padding(bottom=inset)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Athena", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text(r.config.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(Modifier.size(7.dp).background(if (r.connected) Color(0xFF39A568) else Color(0xFFD9A143), RoundedCornerShape(50)))
                Spacer(Modifier.width(7.dp))
                Text(r.t(r.haStatus), style = MaterialTheme.typography.labelMedium, maxLines = 1, modifier = Modifier.widthIn(max = 240.dp))
                Spacer(Modifier.width(10.dp))
                if(r.page=="settings" || keyboardInset>0) IconButton(onClick={focus.clearFocus();keyboard?.hide()}) {Icon(Icons.Outlined.KeyboardHide,r.t("Tastatur schließen"))}
                IconButton(onClick = { r.page = "settings" }) { Icon(Icons.Outlined.Settings, r.t("Einstellungen")) }
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when(r.page) {
                    "settings" -> SettingsScreen(r)
                    "voice" -> VoiceScreen(r)
                    "devices" -> DevicesScreen(r)
                    "cameras" -> CamerasScreen(r)
                    "camera" -> CameraScreen(r)
                    else -> HomeScreen(r)
                }
                // Keep the underlying screen composed so in-progress settings survive the voice interaction.
                if(busy && r.page !in listOf("voice", "camera")) Surface(Modifier.fillMaxSize(), color=MaterialTheme.colorScheme.background) {VoiceScreen(r)}
            }
            if (r.message.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.t(r.message), color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f), maxLines = 2)
                TextButton(onClick = { r.message = "" }) { Text(r.t("OK")) }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NavItem(r.t("Zuhause"), Icons.Outlined.Home, displayed == "home", Modifier.weight(1f)) { r.page = "home" }
                NavItem(r.t("Geräte"), Icons.Outlined.GridView, displayed == "devices", Modifier.weight(1f)) { r.page = "devices" }
                NavItem(r.t("Kameras"), Icons.Outlined.Videocam, displayed in listOf("camera", "cameras"), Modifier.weight(1f)) { r.page = "cameras" }
                FilledTonalIconButton(onClick = { r.page = if (r.page == "voice") "home" else "voice" }) { Icon(Icons.Outlined.GraphicEq, r.t("Sprachstatus")) }
            }
        }
    }
}
@Composable private fun NightScreen(r: AthenaRuntime) {
    var now by remember {mutableLongStateOf(System.currentTimeMillis())}
    LaunchedEffect(Unit) {while(true) {now=System.currentTimeMillis();delay(1000)}}
    val color=Color(0xFF646971)
    Box(Modifier.fillMaxSize().background(Color.Black).clickable {r.peekDayView()},contentAlignment=Alignment.Center) {
        Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(SimpleDateFormat("HH:mm",r.locale).apply {timeZone=TimeZone.getTimeZone(r.timeZone)}.format(Date(now)),fontSize=92.sp,fontWeight=FontWeight.Light,color=color)
            if(r.config.nightShowDate) Text(SimpleDateFormat("EEE, d. MMM",r.locale).apply {timeZone=TimeZone.getTimeZone(r.timeZone)}.format(Date(now)),color=color)
            if(r.config.nightShowStatus) Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                Icon(if(r.connected)Icons.Outlined.Wifi else Icons.Outlined.WifiOff,r.t(r.haStatus),tint=color,modifier=Modifier.size(18.dp))
                if(r.voice.phase==VoicePhase.MUTED)Icon(Icons.Outlined.MicOff,r.t("Stumm"),tint=color,modifier=Modifier.size(18.dp))
            }
        }
    }
}
@Composable private fun NavItem(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, click: () -> Unit) {
    Surface(onClick = click, shape = RoundedCornerShape(16.dp), color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, modifier = modifier) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp)); Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
@Composable private fun HomeScreen(r: AthenaRuntime) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var selectedGroup by remember(r.config.homeGrouping) {mutableStateOf("")}
    val sections=r.homeSections()
    val activeGroup=selectedGroup.takeIf {id -> sections.any {it.id==id}}.orEmpty()
    val homeGrid=rememberLazyGridState()
    LaunchedEffect(r.config.homeGrouping,activeGroup) {homeGrid.scrollToItem(0)}
    LaunchedEffect(Unit) { while(true) { now = System.currentTimeMillis(); delay(1000) } }
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(SimpleDateFormat("HH:mm", r.locale).apply {timeZone=TimeZone.getTimeZone(r.timeZone)}.format(Date(now)), fontSize = 60.sp, lineHeight = 66.sp, fontWeight = FontWeight.SemiBold)
                Text(SimpleDateFormat("EEEE, d. MMMM", r.locale).apply {timeZone=TimeZone.getTimeZone(r.timeZone)}.format(Date(now)), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val weather = r.entities.firstOrNull { it.id == r.config.weather }
            if(weather != null) Column(horizontalAlignment = Alignment.End) {
                Icon(Icons.Outlined.Cloud, null, Modifier.size(35.dp), tint = MaterialTheme.colorScheme.primary)
                Text("${weather.attributes.optString("temperature", "–")}°", style = MaterialTheme.typography.headlineMedium)
                Text(r.t(weather.state), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(18.dp))
        if (!r.connected && r.entities.isEmpty()) EmptyState(r.t("Dein Zuhause, auf einen Blick"), r.t("Verbinde Athena mit Home Assistant. Favoriten und Einstellungen kannst du anschließend auch im Browser verwalten."), r.t("Einrichten")) { r.page = "settings" }
        else if (r.favorites().isEmpty()) EmptyState(r.t("Platz für deine Favoriten"), r.t("Wähle unter Geräte deine Favoriten oder passe die Filter an."), r.t("Geräte auswählen")) { r.page = "devices" }
        else {
            if(r.config.homeGrouping!="none") {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected=activeGroup.isEmpty(),onClick={selectedGroup=""},label={Text(r.t("Alle"))})
                    sections.forEach {section -> FilterChip(selected=activeGroup==section.id,onClick={selectedGroup=section.id},label={Text(section.title)})}
                }
                Spacer(Modifier.height(8.dp))
            }
            LazyVerticalGrid(columns = GridCells.Fixed(2), state=homeGrid, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                sections.filter {activeGroup.isEmpty()||it.id==activeGroup}.forEach {section ->
                    if(r.config.homeGrouping!="none") item(key="header:${section.id}",span={GridItemSpan(maxLineSpan)}) {
                        Text(section.title,style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=4.dp))
                    }
                    items(section.entities,key={"entity:${it.id}"}) {DeviceCard(it,r,false)}
                }
            }
        }
    }
}
@Composable private fun EmptyState(title: String, text: String, button: String, click: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = click) { Text(button) }
        }
    }
}
@Composable private fun DevicesScreen(r: AthenaRuntime) {
    var query by remember { mutableStateOf("") }
    var category by remember {mutableStateOf("")}
    var room by remember {mutableStateOf("")}
    val available=r.filtered()
    val categories=available.map {homeCategory(it.domain)}.distinctBy {it.id}.sortedBy {it.order}
    val matches=available.filter {e ->
        (category.isEmpty() || homeCategory(e.domain).id==category) &&
        (room.isEmpty() || (if(room=="__none") r.entityAreas[e.id].isNullOrBlank() else r.entityAreas[e.id]==room)) &&
        query.trim().split(Regex("\\s+")).all {part -> listOf(e.name,e.id,e.deviceName,r.areaNames[r.entityAreas[e.id]] ?: "",r.t(homeCategory(e.domain).name)).any {it.contains(part,true)}}
    }
    Column {
        OutlinedTextField(query, {query = it}, singleLine = true, label = { Text(r.t("Geräte suchen")) }, leadingIcon = {Icon(Icons.Outlined.Search, null)}, modifier = Modifier.fillMaxWidth())
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(category.isEmpty(),{category=""},label={Text(r.t("Alle Kategorien"))})
            categories.forEach {c -> FilterChip(category==c.id,{category=c.id},label={Text(r.t(c.name))})}
        }
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(room.isEmpty(),{room=""},label={Text(r.t("Alle Räume"))})
            r.areaNames.entries.sortedBy {it.value}.forEach {(id,name) -> FilterChip(room==id,{room=id},label={Text(name)})}
            FilterChip(room=="__none",{room="__none"},label={Text(r.t("Ohne Raum"))})
        }
        Text("${matches.size} ${r.t("Treffer")}",style=MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(matches, key = {it.id}) { DeviceCard(it, r, true) }
        }
    }
}
@Composable private fun DeviceCard(e: Entity, r: AthenaRuntime, favoriteButton: Boolean) {
    val icon = when(e.domain) { "light" -> Icons.Outlined.Lightbulb; "climate" -> Icons.Outlined.Thermostat; "cover" -> Icons.Outlined.Blinds; "lock" -> Icons.Outlined.Lock; "scene" -> Icons.Outlined.AutoAwesome; else -> Icons.Outlined.ToggleOn }
    val active = e.state == "on"
    val tint = if (active && e.domain == "light") Color(0xFFE7A332) else MaterialTheme.colorScheme.primary
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(30.dp), tint)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if(favoriteButton) {
                        Text(e.id,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
                        r.areaNames[r.entityAreas[e.id]]?.let {Text(it,style=MaterialTheme.typography.labelSmall)}
                    }
                    val text = when(e.domain) {
                        "climate" -> "${e.attributes.optString("current_temperature", "–")}° · ${r.t("Ziel")} ${e.attributes.optString("temperature", "–")}°"
                        "sensor" -> "${e.state} ${e.attributes.optString("unit_of_measurement")}"
                        "lock" -> if(e.state == "locked") r.t("Verriegelt") else e.state
                        "cover" -> if(e.state == "open") r.t("Geöffnet") else if(e.state == "closed") r.t("Geschlossen") else e.state
                        else -> if(e.state == "on") r.t("An") else if(e.state == "off") r.t("Aus") else e.state
                    }
                    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                }
                if(favoriteButton) IconButton(onClick = {r.toggleFavorite(e.id)}) { Icon(if(e.id in r.config.favorites) Icons.Outlined.Star else Icons.Outlined.StarBorder, r.t("Favorit")) }
                else if(e.domain in listOf("light", "switch", "input_boolean")) Switch(checked = active, onCheckedChange = { r.action(e) }, enabled = r.connected && e.available)
            }
            if(favoriteButton && e.domain in listOf("light", "switch", "input_boolean", "scene")) TextButton(onClick = {r.action(e, if(e.domain == "scene") "turn_on" else "toggle")}, enabled = r.connected && e.available) {Text(if(e.domain == "scene") r.t("Aktivieren") else r.t("Schalten"))}
            if(e.domain == "cover") Row {
                IconButton(onClick = {r.action(e, "open_cover")}, enabled = r.connected && e.available) {Icon(Icons.Outlined.KeyboardArrowUp, r.t("Öffnen"))}
                IconButton(onClick = {r.action(e, "stop_cover")}, enabled = r.connected && e.available) {Icon(Icons.Outlined.Stop, r.t("Stoppen"))}
                IconButton(onClick = {r.action(e, "close_cover")}, enabled = r.connected && e.available) {Icon(Icons.Outlined.KeyboardArrowDown, r.t("Schließen"))}
            }
            if(e.domain == "climate" && e.attributes.has("temperature")) Row(verticalAlignment = Alignment.CenterVertically) {
                val target = e.attributes.optDouble("temperature", 20.0)
                val step = e.attributes.optDouble("target_temp_step", .5)
                IconButton(onClick = {r.action(e, "set_temperature", JSONObject().put("temperature", (target-step).coerceAtLeast(e.attributes.optDouble("min_temp", 7.0))))}, enabled = r.connected && e.available) {Icon(Icons.Outlined.Remove, r.t("Temperatur senken"))}
                Text("${target}°", fontWeight = FontWeight.SemiBold)
                IconButton(onClick = {r.action(e, "set_temperature", JSONObject().put("temperature", (target+step).coerceAtMost(e.attributes.optDouble("max_temp", 35.0))))}, enabled = r.connected && e.available) {Icon(Icons.Outlined.Add, r.t("Temperatur erhöhen"))}
            }
        }
    }
}
@Composable private fun VoiceScreen(r: AthenaRuntime) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(r.t(r.voice.phase.label), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        VisualizerRegistry.find(r.config.animation).Render(VisualizerInput(r.voice.phase, null, r.config.reducedMotion), Modifier.size(175.dp))
        Spacer(Modifier.height(12.dp))
        Text(if(r.voice.phase == VoicePhase.OFFLINE) r.t("Die lokale Audioverbindung wird automatisch wiederhergestellt.") else r.t("Sprachstatus dieses Geräts"), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun SettingsScreen(r: AthenaRuntime) {
    val keyboard=LocalSoftwareKeyboardController.current
    val focus=LocalFocusManager.current
    val done=KeyboardActions(onDone={focus.clearFocus();keyboard?.hide()})
    var url by remember(r.config.haUrl) { mutableStateOf(r.config.haUrl) }
    var token by remember { mutableStateOf("") }
    var name by remember(r.config.name) { mutableStateOf(r.config.name) }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(r.t("Einstellungen"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(r.t("Anzeigesprache"),style=MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            listOf("de" to "Deutsch","en" to "English").forEach {(id,label) ->
                FilterChip(selected=r.config.language==id,onClick={r.updateConfig {it.copy(language=id)}},label={Text(label)})
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(18.dp)) {
                Text(r.t("Bequem im Browser einrichten"), fontWeight = FontWeight.SemiBold)
                SelectionContainerText(r.address())
                Text(r.t("Browser einmalig koppeln"), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                if(r.pairingCode.isNotEmpty()) {
                    Text(r.pairingCode, fontSize=34.sp, fontWeight=FontWeight.Bold, letterSpacing=9.sp)
                    Text(r.t("Einmalig gültig · 5 Minuten · maximal 5 Fehlversuche"), style=MaterialTheme.typography.bodySmall)
                } else Text(r.t("Kein Kopplungscode aktiv"), style=MaterialTheme.typography.bodySmall)
                TextButton(onClick={r.newPairing()}) {Text(r.t("Neuen 4-stelligen Code anzeigen"))}
                Text(r.t("Nach der Kopplung bleibt dein Browser 30 Tage angemeldet."), style = MaterialTheme.typography.bodySmall)
            }
        }
        OutlinedTextField(name, {name=it}, label = {Text(r.t("Gerätename"))}, singleLine = true, keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done), keyboardActions=done, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(url, {url=it}, label = {Text("Home Assistant · http://IP:8123")}, singleLine = true, keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done), keyboardActions=done, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(token, {token=it}, label = {Text(if(r.store.token.isBlank()) r.t("HA-Zugriffstoken") else r.t("Neuer HA-Token · leer lassen zum Beibehalten"))}, singleLine=true, keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done), keyboardActions=done, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Button(onClick = { r.scope.launch { runCatching { r.save(r.config.copy(name=name, haUrl=url).json().put("haToken", token)) }.onSuccess {token=""; r.message=r.t("Gespeichert")}.onFailure {r.message=it.message ?: r.t("Fehler")} } }) { Text(r.t("Verbindung speichern")) }
        Text(r.t("Darstellung"), style = MaterialTheme.typography.titleMedium)
        Text(r.t("Nachtansicht"),style=MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("off" to "Aus","auto" to "Lichtsensor","always" to "Immer").forEach {(mode,label) ->
                FilterChip(r.config.nightMode==mode,{r.updateConfig {it.copy(nightMode=mode)}},label={Text(r.t(label))})
            }
        }
        Text(if(r.lightSensorAvailable) "${r.t("Helligkeit")}: ${r.lux?.let {"%.1f".format(it)} ?: "–"} lx" else r.t("Kein Lichtsensor verfügbar"),style=MaterialTheme.typography.bodySmall)
        if(r.config.nightMode!="off") {
            Text("${r.t("Lichtschwelle")}: ${r.config.nightLux} lx")
            Slider(r.config.nightLux.coerceIn(0.1f,50f),{v -> r.updateConfig {it.copy(nightLux=v)}},valueRange=0.1f..50f)
            Text("${r.t("Verzögerung")}: ${r.config.nightDelay} s")
            Slider(r.config.nightDelay.toFloat().coerceIn(0f,120f),{v -> r.updateConfig {it.copy(nightDelay=v.toInt())}},valueRange=0f..120f,steps=23)
            Text("${r.t("Nachthelligkeit")}: ${r.config.nightBrightness} %")
            Slider(r.config.nightBrightness.toFloat(),{v -> r.updateConfig {it.copy(nightBrightness=v.toInt())}},valueRange=1f..20f,steps=18)
            Row(verticalAlignment=Alignment.CenterVertically) {Switch(r.config.nightShowDate,{v -> r.updateConfig {it.copy(nightShowDate=v)}});Text(r.t("Datum nachts anzeigen"))}
            Row(verticalAlignment=Alignment.CenterVertically) {Switch(r.config.nightShowStatus,{v -> r.updateConfig {it.copy(nightShowStatus=v)}});Text(r.t("Statussymbole nachts anzeigen"))}
            Text(r.t("Tippen zeigt die normale Ansicht für 30 Sekunden. Sprache und Klingeln bleiben aktiv."),style=MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { ThemeRegistry.themes.forEach { theme -> FilterChip(selected=r.config.theme==theme.id, onClick={r.updateConfig {it.copy(theme=theme.id)}}, label={Text(theme.label)}) } }
        Text(r.t("Startseite gruppieren"),style=MaterialTheme.typography.titleMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("none" to r.t("Ohne Gruppierung"),"area" to r.t("Räume"),"category" to r.t("Kategorien"),"area_category" to r.t("Räume & Kategorien")).forEach {(id,label) ->
                FilterChip(selected=r.config.homeGrouping==id,onClick={r.updateConfig {it.copy(homeGrouping=id)}},label={Text(label)})
            }
        }
        Text(r.t("Gruppiert deine Startseiten-Favoriten. Raumzuordnungen stammen aus Home Assistant."),style=MaterialTheme.typography.bodySmall)
        Text(r.t("Sprachanimation"), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { VisualizerRegistry.renderers.forEach { renderer -> FilterChip(selected=r.config.animation==renderer.id, onClick={r.updateConfig {it.copy(animation=renderer.id)}}, label={Text(r.t(renderer.label))}) } }
        Row(verticalAlignment = Alignment.CenterVertically) { Switch(r.config.reducedMotion, {value -> r.updateConfig {it.copy(reducedMotion=value)}}); Spacer(Modifier.width(10.dp)); Text(r.t("Bewegung reduzieren")) }
        Row(verticalAlignment = Alignment.CenterVertically) { Switch(r.config.autoStart, {value -> r.updateConfig {it.copy(autoStart=value)}}); Spacer(Modifier.width(10.dp)); Text(r.t("Athena beim Gerätestart öffnen")) }
        Text(r.t("Klingelton"),style=MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            listOf("none" to r.t("Aus"),"two-note" to r.t("Zweiklang"),"soft" to r.t("Sanfter Ton")).forEach {(id,label) ->
                FilterChip(selected=r.config.ringtone==id,onClick={r.updateConfig {it.copy(ringtone=id)}},label={Text(label)})
            }
        }
        var ringVolume by remember(r.config.ringVolume) {mutableFloatStateOf(r.config.ringVolume.toFloat())}
        Text("${r.t("Klingellautstärke")}: ${ringVolume.toInt()} % · ${r.t("zusätzlich gilt die Medienlautstärke")}")
        Slider(value=ringVolume,onValueChange={ringVolume=it},valueRange=0f..100f,onValueChangeFinished={r.updateConfig {it.copy(ringVolume=ringVolume.toInt())}})
        OutlinedButton(onClick={runCatching {r.testChime()}.onFailure {r.message=r.t("Klingelton konnte nicht abgespielt werden")}}) {Text(r.t("Klingelton testen"))}
        Text(r.t("Klingelauslöser und Kamera wählst du in der WebUI unter Kameras & Klingel."),style=MaterialTheme.typography.bodySmall)
        Text("Athena ${BuildConfig.VERSION_NAME} · ${r.webStatus}", style = MaterialTheme.typography.bodySmall)
    }
}
@Composable private fun SelectionContainerText(text: String) {
    androidx.compose.foundation.text.selection.SelectionContainer { Text(text, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical=4.dp)) }
}
@Composable private fun CamerasScreen(r: AthenaRuntime) {
    val cameras = r.entities.filter {it.domain == "camera"}
    if(cameras.isEmpty()) EmptyState(r.t("Kameras"), r.t("Sobald HA verbunden ist, erscheinen hier deine Kamera-Entitäten."), r.t("Einstellungen")) {r.page="settings"}
    else LazyVerticalGrid(columns=GridCells.Fixed(2), horizontalArrangement=Arrangement.spacedBy(12.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
        items(cameras, key={it.id}) { camera -> Card(onClick={r.shownCamera=camera.id;r.page="camera"}, colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
            Column { CameraImage(r, camera.id, Modifier.fillMaxWidth().height(125.dp), false); Text(camera.name, Modifier.padding(14.dp), fontWeight=FontWeight.SemiBold) }
        } }
    }
}
@Composable fun CameraImage(r: AthenaRuntime, id: String, modifier: Modifier, refresh: Boolean) {
    var bitmap by remember(id) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(id, r.connected, refresh) {
        do { bitmap = r.snapshot(id); if(refresh) delay(2000) } while(refresh)
    }
    val image = bitmap
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment=Alignment.Center) {
        if(image != null) Image(image.asImageBitmap(), r.t("Kamerabild"), Modifier.fillMaxSize(), contentScale=ContentScale.Crop)
        else Text(if(r.connected) r.t("Kein Kamerabild verfügbar") else r.t("HA nicht verbunden"), color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
