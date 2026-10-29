package com.jas.montaj.montaj

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontFamily
import com.jas.montaj.core.AppSettings
import com.jas.montaj.core.ExportState
import com.jas.montaj.core.SettingsStore
import com.jas.montaj.core.Transcriber
import com.jas.montaj.core.History
import com.jas.montaj.core.ProjectInfo
import com.jas.montaj.core.ProjectKind
import com.jas.montaj.core.ProjectStore
import com.jas.montaj.platform.AudioRecorder
import com.jas.montaj.platform.AudioSegment
import com.jas.montaj.platform.ExportJob
import com.jas.montaj.platform.MediaBackend
import com.jas.montaj.platform.PreviewClip
import com.jas.montaj.platform.PreviewVoice
import com.jas.montaj.platform.PreviewTimeline
import com.jas.montaj.render.MontajRenderer
import com.jas.montaj.render.TextRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Montaj rejimining holati va amallari (iOS va Android uchun bitta kod). */
@OptIn(ExperimentalCoroutinesApi::class)
class MontajEditor(
    private val media: MediaBackend,
    private val fonts: FontFamily.Resolver,
    private val store: ProjectStore,
    private val settingsStore: SettingsStore,
) {
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
    val thumbnails = mutableStateMapOf<String, ImageBitmap>()

    /** Ko'rish oynasidagi matnlar ham eksportdagi bilan bir xil chizuvchi orqali. */
    val previewText = TextRenderer(fonts)
    val player = media.createPlayer()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var rebuildJob: Job? = null
    private var saveJob: Job? = null

    /** Pauzada o'tish effekti ustida turganda ko'rsatiladigan aniq kadr (pleyer o'tishni ko'rsatmaydi). */
    var transitionPreview by mutableStateOf<ImageBitmap?>(null)
        private set
    private val previewRenderer = MontajRenderer(TextRenderer(fonts))
    private val previewFrames = media.openFrameSource()
    private val renderDispatcher = Dispatchers.Default.limitedParallelism(1)
    private var transitionJob: Job? = null

    val history = History<MontajState>()
    var projectInfo by mutableStateOf(store.newInfo(ProjectKind.MONTAJ))
        private set
    var projects by mutableStateOf(emptyList<ProjectInfo>())
        private set

    init {
        val lastId = store.lastOpened(ProjectKind.MONTAJ)
        if (lastId != null) openProject(lastId) else store.setLastOpened(ProjectKind.MONTAJ, projectInfo.id)

        scope.launch {
            while (isActive) {
                val playing = player.isPlaying
                if (playing || isPlaying) currentTime = player.currentTime().coerceIn(0.0, state.totalDuration)
                val stopped = isPlaying && !playing
                isPlaying = playing
                if (playing) transitionPreview = null
                if (stopped) refreshTransitionPreview()
                delay(50)
            }
        }
    }

    private fun update(change: (MontajState) -> MontajState) {
        val before = state
        val after = change(before)
        if (after == before) return
        history.record(before)
        apply(after)
    }

    /** Holatni o'rnatadi: ko'rish oynasi qayta quriladi va loyiha avtomatik saqlanadi. */
    private fun apply(newState: MontajState, save: Boolean = true) {
        state = newState
        if (selectedClipId != null && newState.clips.none { it.id == selectedClipId }) selectedClipId = newState.clips.firstOrNull()?.id
        if (selectedOverlayId != null && newState.overlays.none { it.id == selectedOverlayId }) selectedOverlayId = null
        scheduleRebuild()
        if (save) scheduleSave()
        refreshTransitionPreview()
    }

    private fun refreshTransitionPreview() {
        transitionJob?.cancel()
        val snapshot = state
        val time = currentTime
        if (isPlaying || snapshot.transitionAt(time) == null) {
            transitionPreview = null
            return
        }
        transitionJob = scope.launch {
            delay(120)
            transitionPreview = withContext(renderDispatcher) { previewRenderer.render(snapshot, time, previewFrames, maxSide = 960) }
        }
    }

    // region Undo / Redo va loyihalar

    fun undo() {
        history.undo(state)?.let { apply(it) }
    }

    fun redo() {
        history.redo(state)?.let { apply(it) }
    }

    fun refreshProjects() {
        projects = store.list(ProjectKind.MONTAJ)
    }

    fun openProject(id: String) {
        val loaded = store.loadMontaj(id) ?: return
        flushSave()
        player.pause()
        projectInfo = store.list(ProjectKind.MONTAJ).firstOrNull { it.id == id } ?: projectInfo.copy(id = id)
        store.setLastOpened(ProjectKind.MONTAJ, id)
        history.clear()
        thumbnails.clear()
        currentTime = 0.0
        selectedClipId = loaded.clips.firstOrNull()?.id
        selectedOverlayId = null
        apply(loaded, save = false)
        scope.launch {
            loaded.clips.forEach { clip ->
                withContext(Dispatchers.Default) { media.thumbnail(clip.path) }?.let { thumbnails[clip.id] = it }
            }
        }
    }

    fun newProject() {
        flushSave()
        player.pause()
        projectInfo = store.newInfo(ProjectKind.MONTAJ)
        store.setLastOpened(ProjectKind.MONTAJ, projectInfo.id)
        history.clear()
        thumbnails.clear()
        currentTime = 0.0
        selectedClipId = null
        selectedOverlayId = null
        apply(MontajState(), save = false)
    }

    fun renameProject(name: String) {
        projectInfo = projectInfo.copy(name = name.ifBlank { projectInfo.name })
        scheduleSave()
    }

    fun deleteProject(id: String) {
        store.delete(id)
        if (id == projectInfo.id) newProject()
        refreshProjects()
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(800)
            val info = projectInfo
            val snapshot = state
            withContext(Dispatchers.Default) { store.saveMontaj(info, snapshot) }
        }
    }

    /** Boshqa loyihaga o'tishdan oldin kutilayotgan saqlashni darhol bajaradi. */
    private fun flushSave() {
        if (saveJob?.isActive == true) {
            saveJob?.cancel()
            store.saveMontaj(projectInfo, state)
        }
    }

    // endregion

    // region Kliplar

    fun importVideos(paths: List<String>) {
        if (paths.isEmpty()) return
        scope.launch {
            isImporting = true
            try {
                for (path in paths) {
                    val (info, thumbnail) = withContext(Dispatchers.Default) { media.videoInfo(path) to media.thumbnail(path) }
                    if (info.durationSec < Clip.MIN_LENGTH) continue
                    val clip = Clip(path = path, durationSec = info.durationSec, width = info.width, height = info.height, hasAudio = info.hasAudio)
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
    }

    // endregion

    // region Ovoz

    fun importMusic(paths: List<String>) {
        val path = paths.firstOrNull() ?: return
        scope.launch {
            isImporting = true
            try {
                val duration = withContext(Dispatchers.Default) { media.audioDuration(path) }
                val name = path.substringAfterLast('/').substringBeforeLast('.').ifBlank { "Musiqa" }
                update { it.copy(music = MusicTrack(path, name, duration)) }
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

    // region Ovoz yozish (voiceover)

    var isRecording by mutableStateOf(false)
        private set
    private var recorder: AudioRecorder? = null
    private var recordStartAt = 0.0
    private var recordMark: TimeMark? = null

    /** Joriy vaqtdan yozishni boshlaydi; video ham ijro etiladi — ustiga gapirish uchun. */
    fun startRecording() {
        if (isRecording || state.clips.isEmpty()) return
        val created = media.createRecorder()
        if (!created.start()) {
            errorMessage = "Mikrofonni yoqib bo'lmadi"
            return
        }
        recorder = created
        recordStartAt = currentTime
        recordMark = TimeSource.Monotonic.markNow()
        isRecording = true
        player.play()
    }

    fun stopRecording() {
        val current = recorder ?: return
        recorder = null
        isRecording = false
        player.pause()
        val duration = recordMark?.elapsedNow()?.inWholeMilliseconds?.div(1000.0) ?: 0.0
        val path = current.stop()
        if (path == null || duration < 0.3) {
            errorMessage = "Ovoz yozilmadi"
            return
        }
        val voice = VoiceoverItem(path = path, startAt = recordStartAt, duration = duration)
        update { it.copy(voiceovers = it.voiceovers + voice) }
    }

    fun updateVoiceover(id: String, change: (VoiceoverItem) -> VoiceoverItem) = update { current ->
        current.copy(voiceovers = current.voiceovers.map { if (it.id == id) change(it) else it })
    }

    fun deleteVoiceover(id: String) = update { current -> current.copy(voiceovers = current.voiceovers.filterNot { it.id == id }) }

    // endregion

    // region Avto-subtitr

    var settings by mutableStateOf(settingsStore.load())
        private set
    /** null — ish ketmayapti; aks holda 0..1. */
    var subtitleProgress by mutableStateOf<Float?>(null)
        private set

    fun saveSettings(newSettings: AppSettings) {
        settings = newSettings
        settingsStore.save(newSettings)
    }

    /** Har bir klip ovozini matnga aylantirib, vaqtlari bilan subtitr qo'shadi. */
    fun generateSubtitles() {
        val key = settings.openAiKey
        if (key.isBlank()) {
            errorMessage = "Avval OpenAI API kalitini kiriting"
            return
        }
        val snapshot = state
        val clips = snapshot.clips.withIndex().filter { it.value.hasAudio }
        if (clips.isEmpty()) {
            errorMessage = "Kliplarda ovoz yo'q"
            return
        }
        player.pause()
        scope.launch {
            subtitleProgress = 0f
            try {
                val starts = snapshot.clipStarts
                val created = mutableListOf<TextOverlayItem>()
                clips.forEachIndexed { done, (index, clip) ->
                    val audio = media.extractAudio(clip.path, clip.trimStart, clip.trimEnd)
                    val segments = Transcriber.transcribe(audio, key, settings.subtitleLanguage)
                    val clipEnd = starts[index] + clip.outputDuration
                    segments.forEach { segment ->
                        val start = starts[index] + segment.start / clip.speed
                        val end = minOf(starts[index] + segment.end / clip.speed, clipEnd)
                        if (end - start > 0.1) {
                            created += TextOverlayItem(text = segment.text, start = start, end = end, fontSize = 58f)
                        }
                    }
                    subtitleProgress = (done + 1f) / clips.size
                }
                if (created.isEmpty()) error("Nutq topilmadi")
                update { it.copy(overlays = it.overlays + created) }
            } catch (error: Exception) {
                errorMessage = "Subtitr yaratib bo'lmadi: ${error.message}"
            } finally {
                subtitleProgress = null
            }
        }
    }

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
        update { current -> current.copy(overlays = current.overlays.filterNot { it.id == id }) }
    }

    fun setFilter(filter: VideoFilter) = update { it.copy(filter = filter) }

    // endregion

    // region Ijro

    fun togglePlayback() {
        if (player.isPlaying) {
            player.pause()
        } else {
            if (currentTime >= state.totalDuration - 0.05) seek(0.0)
            player.play()
        }
    }

    fun seek(time: Double) {
        val target = time.coerceIn(0.0, state.totalDuration)
        currentTime = target
        player.seek(target)
        refreshTransitionPreview()
    }

    private fun scheduleRebuild() {
        rebuildJob?.cancel()
        rebuildJob = scope.launch {
            delay(250)
            val current = state
            player.setTimeline(
                PreviewTimeline(
                    clips = current.clips.map { PreviewClip(it.path, it.trimStart, it.trimEnd, it.speed) },
                    colorMatrix = current.filter.matrix(),
                    musicPath = current.music?.path,
                    musicDuration = current.music?.durationSec ?: 0.0,
                    originalVolume = current.originalVolume,
                    musicVolume = current.musicVolume,
                    voiceovers = current.voiceovers.map { PreviewVoice(it.path, it.startAt, it.duration, it.volume) },
                ),
                resumeAt = currentTime.coerceAtMost(current.totalDuration),
            )
        }
    }

    // endregion

    fun export() {
        val snapshot = state
        if (snapshot.clips.isEmpty()) return
        player.pause()
        val renderer = MontajRenderer(TextRenderer(fonts))
        val audio = buildList {
            snapshot.clips.zip(snapshot.clipStarts).forEach { (clip, start) ->
                if (clip.hasAudio && snapshot.originalVolume > 0f) {
                    add(AudioSegment(clip.path, clip.trimStart, clip.trimEnd, start, clip.speed, snapshot.originalVolume))
                }
            }
            snapshot.voiceovers.forEach { voice ->
                add(AudioSegment(voice.path, 0.0, voice.duration, voice.startAt, volume = voice.volume))
            }
            snapshot.music?.let { music ->
                if (snapshot.musicVolume > 0f) add(AudioSegment(music.path, 0.0, music.durationSec, 0.0, volume = snapshot.musicVolume, loop = true))
            }
        }
        val job = ExportJob(
            width = MontajState.WIDTH,
            height = MontajState.HEIGHT,
            fps = 30,
            durationSec = snapshot.totalDuration,
            audio = audio,
            renderFrame = { time, frames -> renderer.render(snapshot, time, frames) },
        )
        scope.launch {
            exportState = ExportState.Exporting(0f)
            try {
                val video = media.export(job) { progress -> exportState = ExportState.Exporting(progress) }
                exportState = ExportState.Finished(video)
            } catch (error: Exception) {
                exportState = ExportState.Idle
                errorMessage = "Eksport qilib bo'lmadi: ${error.message}"
            }
        }
    }

    fun dismissExport() {
        exportState = ExportState.Idle
    }

    fun share() {
        (exportState as? ExportState.Finished)?.let { media.share(it.video) }
    }

    fun dispose() {
        flushSave()
        previewFrames.close()
        player.release()
        scope.cancel()
    }
}
