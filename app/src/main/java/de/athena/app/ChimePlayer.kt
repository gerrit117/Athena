package de.athena.app

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer

/** Short bundled sounds. No stream URL, account or Music Assistant needed. */
class ChimePlayer(private val context: Context) {
    private var player: MediaPlayer? = null
    fun play(kind: String, volume: Int) {
        require(kind in setOf("none", "two-note", "soft"));require(volume in 0..100)
        if(kind=="none" || volume==0) return
        close()
        val next=MediaPlayer();player=next
        try {
            next.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            context.assets.openFd("chime-$kind.wav").use {next.setDataSource(it.fileDescriptor,it.startOffset,it.length)}
            val gain=volume/100f;next.setVolume(gain,gain)
            next.setOnPreparedListener {if(player===it)it.start()}
            next.setOnCompletionListener {release(it)}
            next.setOnErrorListener {p,_,_ -> release(p);true}
            next.prepareAsync()
        } catch(e:Exception) {release(next);throw e}
    }
    private fun release(value: MediaPlayer) {if(player===value)player=null;runCatching {value.release()}}
    fun close() {player?.let {release(it)}}
}
