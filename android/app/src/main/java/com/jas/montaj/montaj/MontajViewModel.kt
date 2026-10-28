package com.jas.montaj.montaj

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.jas.montaj.common.ExportRunner
import com.jas.montaj.common.ExportState
import com.jas.montaj.common.MediaFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MontajViewModel(application: Application) : AndroidViewModel(application) {
    var state by mutableStateOf(MontajState())
        private set
    var selectedClipId by mutableStateOf<String?>(null)
    var selectedOverlayId by mutableStateOf<String?>(null)
    var currentTime by mutableStateOf(0.0)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var isImporting by mutableStateOf(false)
        private set
    var exportState by mutableStateOf<ExportState>(ExportState.Idle)
        private set
    var errorMessage by mutableStateOf<String?>(null)
    val thumbnails = mutableStateMapOf<String, Bitmap>()

    val player: ExoPlayer = ExoPlayer.Builder(application).build()
    private val musicPlayer: ExoPlayer = ExoPlayer.Builder(application).build().apply {
        repeatMode = Player.REPEAT_MODE_ONE
    }
    private var rebuildJob: Job? = null
    private var ticker: Job? = null
    private val context get() = getApplication<Application>()

    init {
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                applySpeedForCurrentClip()
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing) startTicker() else ticker?.cancel()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    musicPlayer.pause()
                    currentTime = state.totalDuration
                }
            }
        })
    }

    private fun update(change: (MontajState) -> MontajState) {
        state = change(state)
        scheduleRebuild()
    }

    // region Kliplar

    fun importVideos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            isImporting = true
            try {
                for (uri in uris) {
                    val imported = withContext(Dispatchers.IO) {
                        val file = MediaFiles.copyToCache(context, uri, "mp4")
                        val info = MediaFiles.videoInfo(file)
                        Triple(file, info, MediaFiles.thumbnail(file))
                    }
                    val (file, info, thumbnail) = imported
                    if (info.durationSec < Clip.MIN_LENGTH) continue
                    val clip = Clip(file = file, durationSec = info.durationSec, hasAudio = info.hasAudio)
                    thumbnail?.let { thumbnails[clip.id] = it }
                    update { it.copy(clips = it.clips + clip) }
                    if (selectedClipId == null) selectedClipId = clip.id
                }
            } catch (error: Exception) {
                errorMessage = "Videoni yuklab bo'lmadi: ${error.message}"
            } finally {
                isImporting = false
            }
        }
    }

    fun updateClip(id: String, change: (Clip) -> Clip) = update { current ->
        current.copy(clips = current.clips.map { if (it.id == id) change(it) else it })
    }

    fun moveClip(id: String, offset: Int) = update { current ->
        val index = current.clips.indexOfFirst { it.id == id }
        val target = index + offset
        if (index < 0 || target !in current.clips.indices) return@update current
        val clips = current.clips.toMutableList()
        clips[index] = clips[target].also { clips[target] = clips[index] }
        current.copy(clips = clips)
    }

    fun deleteClip(id: String) {
        update { current -> current.copy(clips = current.clips.filterNot { it.id == id }) }
        thumbnails.remove(id)
        if (selectedClipId == id) selectedClipId = state.clips.firstOrNull()?.id
    }

    // endregion

    // region Ovoz

    fun importMusic(uri: Uri) {
        viewModelScope.launch {
            isImporting = true
            try {
                val track = withContext(Dispatchers.IO) {
                    val file = MediaFiles.copyToCache(context, uri, "m4a")
                    MusicTrack(file, MediaFiles.displayName(context, uri), MediaFiles.audioDuration(file))
                }
                update { it.copy(music = track) }
            } catch (error: Exception) {
                errorMessage = "Musiqani qo'shib bo'lmadi: ${error.message}"
            } finally {
                isImporting = false
            }
        }
    }

    fun removeMusic() = update { it.copy(music = null) }
    fun setOriginalVolume(volume: Float) = update { it.copy(originalVolume = volume) }
    fun setMusicVolume(volume: Float) = update { it.copy(musicVolume = volume) }

    // endregion

    // region Matn va filtr

    fun addOverlay() {
        val total = state.totalDuration
        val start = currentTime.coerceAtMost((total - 0.5).coerceAtLeast(0.0))
        val overlay = TextOverlayItem(text = "Matn", start = start, end = minOf(start + 3, maxOf(total, start + 0.5)))
        update { it.copy(overlays = it.overlays + overlay) }
        selectedOverlayId = overlay.id
    }

    fun updateOverlay(id: String, change: (TextOverlayItem) -> TextOverlayItem) = update { current ->
        current.copy(overlays = current.overlays.map { if (it.id == id) change(it) else it })
    }

    fun deleteOverlay(id: String) {
        if (selectedOverlayId == id) selectedOverlayId = null
        update { current -> current.copy(overlays = current.overlays.filterNot { it.id == id }) }
    }

    fun setFilter(filter: VideoFilter) = update { it.copy(filter = filter) }

    // endregion

    // region Ko'rish

    fun togglePlayback() {
        if (player.isPlaying) {
            player.pause()
            musicPlayer.pause()
        } else {
            if (currentTime >= state.totalDuration - 0.05) seek(0.0)
            player.play()
            syncMusic(play = true)
        }
    }

    /** Tayyor videodagi vaqtni klip va uning ichidagi pozitsiyaga aylantirib, o'sha joyga o'tadi. */
    fun seek(time: Double) {
        val clips = state.clips
        if (clips.isEmpty()) return
        val target = time.coerceIn(0.0, state.totalDuration)
        val starts = state.clipStarts
        val index = starts.indexOfLast { it <= target }.coerceAtLeast(0)
        val clip = clips[index]
        val positionMs = ((target - starts[index]) * clip.speed * 1000).toLong()
            .coerceIn(0L, (clip.trimmedDuration * 1000).toLong())
        player.seekTo(index, positionMs)
        currentTime = target
        syncMusic(play = player.isPlaying)
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (isActive) {
                currentTime = playerTime()
                delay(50)
            }
        }
    }

    private fun playerTime(): Double {
        val index = player.currentMediaItemIndex
        val clip = state.clips.getOrNull(index) ?: return currentTime
        val start = state.clipStarts.getOrElse(index) { 0.0 }
        return start + player.currentPosition / 1000.0 / clip.speed
    }

    private fun applySpeedForCurrentClip() {
        val clip = state.clips.getOrNull(player.currentMediaItemIndex) ?: return
        player.playbackParameters = PlaybackParameters(clip.speed)
    }

    private fun syncMusic(play: Boolean) {
        val music = state.music
        if (music == null || music.durationSec <= 0) {
            musicPlayer.pause()
            return
        }
        musicPlayer.seekTo(((currentTime % music.durationSec) * 1000).toLong())
        if (play) musicPlayer.play() else musicPlayer.pause()
    }

    private fun scheduleRebuild() {
        rebuildJob?.cancel()
        rebuildJob = viewModelScope.launch {
            delay(250)
            rebuildPreview()
        }
    }

    private fun rebuildPreview() {
        val current = state
        val resume = currentTime.coerceAtMost(current.totalDuration)
        val wasPlaying = player.isPlaying
        player.setVideoEffects(current.filter.effects())
        player.setMediaItems(current.clips.map(MontajComposer::clipMediaItem))
        player.volume = current.originalVolume
        player.prepare()

        val music = current.music
        if (music != null) {
            musicPlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(music.file)))
            musicPlayer.volume = current.musicVolume
            musicPlayer.prepare()
        } else {
            musicPlayer.clearMediaItems()
        }

        if (current.clips.isEmpty()) {
            currentTime = 0.0
            return
        }
        seek(resume)
        applySpeedForCurrentClip()
        if (wasPlaying) {
            player.play()
            syncMusic(play = true)
        }
    }

    // endregion

    fun export() {
        if (state.clips.isEmpty()) return
        player.pause()
        musicPlayer.pause()
        viewModelScope.launch {
            exportState = ExportState.Exporting(0f)
            try {
                val uri = ExportRunner.exportToGallery(context, MontajComposer.build(state)) { progress ->
                    exportState = ExportState.Exporting(progress)
                }
                exportState = ExportState.Finished(uri)
            } catch (error: Exception) {
                exportState = ExportState.Idle
                errorMessage = "Eksport qilib bo'lmadi: ${error.message}"
            }
        }
    }

    fun dismissExport() {
        exportState = ExportState.Idle
    }

    override fun onCleared() {
        player.release()
        musicPlayer.release()
    }
}
