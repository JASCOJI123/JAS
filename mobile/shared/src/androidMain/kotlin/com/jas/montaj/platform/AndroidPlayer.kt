package com.jas.montaj.platform

import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.effect.RgbMatrix
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/** Montaj ko'rish oynasi: ExoPlayer ketma-ket kliplarni ijro etadi, musiqa alohida pleyerda. */
internal class AndroidPreviewPlayer(private val context: Context) : PreviewPlayer {
    val player: ExoPlayer = ExoPlayer.Builder(context).build()
    /** Har bir ovoz yozuvi uchun alohida pleyer; vaqti kelganda yoqiladi. */
    private var voicePlayers: List<Pair<PreviewVoice, ExoPlayer>> = emptyList()
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val voiceSync = object : Runnable {
        override fun run() {
            syncVoices()
            if (player.isPlaying) handler.postDelayed(this, 100)
        }
    }
    private val musicPlayer: ExoPlayer = ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_ONE }
    private var timeline: PreviewTimeline? = null
    private var starts: List<Double> = emptyList()

    init {
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = applySpeed()

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    musicPlayer.pause()
                    voicePlayers.forEach { it.second.pause() }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                handler.removeCallbacks(voiceSync)
                if (isPlaying) handler.post(voiceSync) else voicePlayers.forEach { it.second.pause() }
            }
        })
    }

    override val isPlaying: Boolean get() = player.isPlaying

    override fun currentTime(): Double {
        val clips = timeline?.clips ?: return 0.0
        val index = player.currentMediaItemIndex
        val clip = clips.getOrNull(index) ?: return 0.0
        if (player.playbackState == Player.STATE_ENDED) return starts.lastOrNull()?.plus(outputDuration(clips.last())) ?: 0.0
        return starts.getOrElse(index) { 0.0 } + player.currentPosition / 1000.0 / clip.speed
    }

    override fun setTimeline(timeline: PreviewTimeline, resumeAt: Double) {
        val wasPlaying = player.isPlaying
        this.timeline = timeline
        var cursor = 0.0
        starts = timeline.clips.map { clip -> cursor.also { cursor += outputDuration(clip) } }

        player.setVideoEffects(timeline.colorMatrix?.let { listOf<Effect>(matrixEffect(it)) } ?: emptyList())
        player.setMediaItems(timeline.clips.map(::mediaItem))
        player.volume = timeline.originalVolume
        player.prepare()

        val musicPath = timeline.musicPath
        if (musicPath != null) {
            musicPlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(java.io.File(musicPath))))
            musicPlayer.volume = timeline.musicVolume
            musicPlayer.prepare()
        } else {
            musicPlayer.clearMediaItems()
        }
        voicePlayers.forEach { it.second.release() }
        voicePlayers = timeline.voiceovers.map { voice ->
            voice to ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(Uri.fromFile(java.io.File(voice.path))))
                volume = voice.volume
                prepare()
            }
        }
        if (timeline.clips.isNotEmpty()) seek(resumeAt)
        if (wasPlaying) play()
    }

    override fun play() {
        player.play()
        syncMusic(play = true)
    }

    override fun pause() {
        player.pause()
        musicPlayer.pause()
    }

    override fun seek(timeSec: Double) {
        val clips = timeline?.clips ?: return
        if (clips.isEmpty()) return
        val index = starts.indexOfLast { it <= timeSec }.coerceAtLeast(0)
        val clip = clips[index]
        val positionMs = ((timeSec - starts[index]) * clip.speed * 1000).toLong()
            .coerceIn(0L, ((clip.end - clip.start) * 1000).toLong())
        player.seekTo(index, positionMs)
        applySpeed()
        syncMusic(play = player.isPlaying)
        voicePlayers.forEach { it.second.pause() }
        syncVoices()
    }

    /** Ovoz yozuvlarini joriy vaqtga moslaydi: o'z oralig'ida bo'lsa ijro etadi. */
    private fun syncVoices() {
        val time = currentTime()
        val playing = player.isPlaying
        voicePlayers.forEach { (voice, voicePlayer) ->
            val inside = time >= voice.startAt && time < voice.startAt + voice.duration
            if (playing && inside) {
                if (!voicePlayer.isPlaying) {
                    voicePlayer.seekTo(((time - voice.startAt) * 1000).toLong())
                    voicePlayer.play()
                }
            } else if (voicePlayer.isPlaying) {
                voicePlayer.pause()
            }
        }
    }

    override fun release() {
        handler.removeCallbacks(voiceSync)
        voicePlayers.forEach { it.second.release() }
        player.release()
        musicPlayer.release()
    }

    private fun applySpeed() {
        val clip = timeline?.clips?.getOrNull(player.currentMediaItemIndex) ?: return
        player.playbackParameters = PlaybackParameters(clip.speed.toFloat())
    }

    private fun syncMusic(play: Boolean) {
        val current = timeline
        if (current?.musicPath == null || current.musicDuration <= 0) {
            musicPlayer.pause()
            return
        }
        musicPlayer.seekTo(((currentTime() % current.musicDuration) * 1000).toLong())
        if (play) musicPlayer.play() else musicPlayer.pause()
    }

    private fun outputDuration(clip: PreviewClip) = (clip.end - clip.start) / clip.speed

    companion object {
        fun mediaItem(clip: PreviewClip): MediaItem = MediaItem.Builder()
            .setUri(Uri.fromFile(java.io.File(clip.path)))
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs((clip.start * 1000).toLong())
                    .setEndPositionMs((clip.end * 1000).toLong())
                    .build(),
            )
            .build()

        /**
         * Umumiy 4x5 matritsani Media3 4x4 (ustun bo'yicha) matritsasiga aylantiradi.
         * Video alfa = 1 bo'lgani uchun ofsetlar alfa ustuniga joylanadi.
         */
        fun matrixEffect(matrix: FloatArray): RgbMatrix {
            val values = FloatArray(16)
            for (row in 0 until 4) {
                for (col in 0 until 3) values[col * 4 + row] = matrix[row * 5 + col]
                values[3 * 4 + row] = if (row == 3) 1f else matrix[row * 5 + 4] / 255f
            }
            return RgbMatrix { _, _ -> values }
        }
    }
}

@Composable
actual fun PlayerSurface(player: PreviewPlayer, modifier: Modifier) {
    val exo = (player as AndroidPreviewPlayer).player
    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                this.player = exo
            }
        },
        modifier = modifier,
    )
}
