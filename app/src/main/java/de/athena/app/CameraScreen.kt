package de.athena.app

import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable fun CameraScreen(r: AthenaRuntime) {
    val id = r.shownCamera.ifEmpty {r.config.camera}
    val camera = r.entities.firstOrNull {it.id == id}
    val context = LocalContext.current
    var stream by remember(id) {mutableStateOf("")}
    var ready by remember(id) {mutableStateOf(false)}
    var failed by remember(id) {mutableStateOf(false)}
    LaunchedEffect(id, r.connected) {
        stream=""; ready=false; failed=false
        if(id.isNotEmpty() && r.connected) runCatching {r.ha.streamUrl(id)}.onSuccess {stream=it}.onFailure {failed=true}
    }
    LaunchedEffect(stream) {if(stream.isNotEmpty()) {delay(15000); if(!ready) failed=true}}
    val player = remember(stream, failed) {
        if(stream.isNotEmpty() && !failed) ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultHttpDataSource.Factory().setDefaultRequestProperties(r.ha.authHeaders())
                .setConnectTimeoutMs(8000).setReadTimeoutMs(10000)))
            .build().apply {
                volume=0f
                addListener(object: Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {ready=state==Player.STATE_READY}
                    override fun onPlayerError(error: PlaybackException) {failed=true; ready=false}
                })
                setMediaItem(MediaItem.fromUri(stream)); prepare(); playWhenReady=true
            } else null
    }
    DisposableEffect(player) { onDispose {player?.release()} }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if(r.ringActive) r.t("Es hat geklingelt") else camera?.name ?: r.t("Kamera"), style=MaterialTheme.typography.titleLarge)
                Text(if(ready) r.t("Live · Ton aus") else if(failed) r.t("Einzelbilder · Aktualisierung alle 2 Sekunden") else r.t("Kamera wird verbunden …"), style=MaterialTheme.typography.bodySmall)
            }
            if(r.voice.phase in setOf(VoicePhase.LISTENING, VoicePhase.THINKING, VoicePhase.SPEAKING)) Text(r.t(r.voice.phase.label), color=MaterialTheme.colorScheme.primary)
            TextButton(onClick={r.closeCamera()}) {Text(r.t("Schließen"))}
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment=Alignment.Center) {
            if(player != null) AndroidView(factory={PlayerView(it).apply {useController=false; layoutParams=ViewGroup.LayoutParams(-1,-1); this.player=player}},
                update={it.player=player}, modifier=Modifier.fillMaxSize())
            if(!ready && id.isNotEmpty()) CameraImage(r,id,Modifier.fillMaxSize(),true)
            if(id.isEmpty()) Text(r.t("Keine Kamera ausgewählt"))
        }
    }
}
