package com.readqurantoday.quran

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

// One word said on its own, from the word-by-word recordings; apart from Recite so a surah keeps its place
object WordVoice {

    private const val BASE = "https://audio.readqurantoday.com/wbw"

    private var player: ExoPlayer? = null
    private var ended: ((Boolean) -> Unit)? = null

    val saying get() = player != null

    /** Says [word] (counted from 0, as the page counts) of [surah]:[ayah]; [done] gets false if it could not be played. */
    fun say(context: Context, surah: Int, ayah: Int, word: Int, done: (Boolean) -> Unit) {
        stop()
        ended = done
        val name = "%03d_%03d_%03d.mp3".format(surah, ayah, word + 1)
        player = ExoPlayer.Builder(context.applicationContext).build().apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) finish(true)
                }
                override fun onPlayerError(error: PlaybackException) = finish(false)
            })
            setMediaItem(MediaItem.fromUri("$BASE/$name"))
            prepare()
            play()
        }
    }

    /** Cut short; the caller is told, as a word that ended. */
    fun stop() = finish(true)

    private fun finish(ok: Boolean) {
        player?.release()
        player = null
        val done = ended
        ended = null
        done?.invoke(ok)
    }
}
