package com.jas.montaj.motion

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontFamily
import com.jas.montaj.core.ExportState
import com.jas.montaj.core.History
import com.jas.montaj.core.ProjectInfo
import com.jas.montaj.core.ProjectKind
import com.jas.montaj.core.ProjectStore
import com.jas.montaj.platform.AudioSegment
import com.jas.montaj.platform.ExportJob
import com.jas.montaj.platform.MediaBackend
import com.jas.montaj.render.MotionRenderer
import com.jas.montaj.render.TextRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.time.TimeSource

/** Motion rejimining holati va amallari (iOS va Android uchun bitta kod). */
@OptIn(ExperimentalCoroutinesApi::class)
class MotionEditor(
    private val media: MediaBackend,
    private val fonts: FontFamily.Resolver,
    private val store: ProjectStore,
) {
    var project by mutableStateOf(MotionProject())
        private set
    var selectedLayerId by mutableStateOf<String?>(null)
    var currentTime by mutableStateOf(0.0)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var isImporting by mutableStateOf(false)
        private set
    var exportState by mutableStateOf<ExportState>(ExportState.Idle)
        private set
    var errorMessage by mutableStateOf<String?>(null)
    var previewFrame by mutableStateOf<ImageBitmap?>(null)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    /** Renderer bitta oqimda ishlaydi: keshlar va matn o'lchagich xavfsiz bo'ladi. */
    private val renderDispatcher = Dispatchers.Default.limitedParallelism(1)
    private val renderer = MotionRenderer(TextRenderer(fonts), media)
    private val frames = media.openFrameSource()
    private val renderRequests = Channel<Unit>(Channel.CONFLATED)
    private var playJob: Job? = null
    private var saveJob: Job? = null

    val history = History<MotionProject>()
    var projectInfo by mutableStateOf(store.newInfo(ProjectKind.MOTION))
        private set
    var projects by mutableStateOf(emptyList<ProjectInfo>())
        private set

    val selectedLayer: MotionLayer? get() = project.layers.firstOrNull { it.id == selectedLayerId }

    init {
        // Ko'rish kadrlarini fon oqimida chizadi; ortda qolgan so'rovlar tashlab yuboriladi.
        scope.launch {
            for (request in renderRequests) {
                val snapshot = project
                val time = currentTime
                val exact = !isPlaying
                val frame = withContext(renderDispatcher) { renderer.render(snapshot, time, PREVIEW_SCALE, frames, exact) }
                previewFrame = frame
            }
        }
        val lastId = store.lastOpened(ProjectKind.MOTION)
        if (lastId != null) openProject(lastId) else store.setLastOpened(ProjectKind.MOTION, projectInfo.id)
        requestRender()
    }

    private fun requestRender() {
        renderRequests.trySend(Unit)
    }

    fun updateProject(change: (MotionProject) -> MotionProject) {
        val before = project
        val after = change(before)
        if (after == before) return
        history.record(before)
        apply(after)
    }

    /** Loyihani o'rnatadi: kadr qayta chiziladi va avtomatik saqlanadi. */
    private fun apply(newProject: MotionProject, save: Boolean = true) {
        project = newProject
        currentTime = currentTime.coerceAtMost(newProject.duration)
        if (selectedLayerId != null && newProject.layers.none { it.id == selectedLayerId }) selectedLayerId = null
        requestRender()
        if (save) scheduleSave()
    }

    // region Undo / Redo va loyihalar

    fun undo() {
        history.undo(project)?.let { apply(it) }
    }

    fun redo() {
        history.redo(project)?.let { apply(it) }
    }

    fun refreshProjects() {
        projects = store.list(ProjectKind.MOTION)
    }

    fun openProject(id: String) {
        val loaded = store.loadMotion(id) ?: return
        flushSave()
        stopPlayback()
        projectInfo = store.list(ProjectKind.MOTION).firstOrNull { it.id == id } ?: projectInfo.copy(id = id)
        store.setLastOpened(ProjectKind.MOTION, id)
        history.clear()
        currentTime = 0.0
        selectedLayerId = null
        apply(loaded, save = false)
    }

    fun newProject() {
        flushSave()
        stopPlayback()
        projectInfo = store.newInfo(ProjectKind.MOTION)
        store.setLastOpened(ProjectKind.MOTION, projectInfo.id)
        history.clear()
        currentTime = 0.0
        selectedLayerId = null
        apply(MotionProject(), save = false)
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
            val snapshot = project
            withContext(Dispatchers.Default) { store.saveMotion(info, snapshot) }
        }
    }

    private fun flushSave() {
        if (saveJob?.isActive == true) {
            saveJob?.cancel()
            store.saveMotion(projectInfo, project)
        }
    }

    // endregion

    fun updateLayer(id: String, change: (MotionLayer) -> MotionLayer) = updateProject { current ->
        current.copy(layers = current.layers.map { if (it.id == id) change(it) else it })
    }

    /** UI oqimida chaqiriladi; matn o'lchami uchun alohida o'lchagich. */
    private val sizeRenderer = MotionRenderer(TextRenderer(fonts), media)
    fun contentSize(layer: MotionLayer): Pair<Float, Float> = sizeRenderer.contentSize(layer)

    // region Ijro

    fun togglePlayback() {
        if (isPlaying) {
            stopPlayback()
            return
        }
        if (currentTime >= project.duration - 0.05) currentTime = 0.0
        isPlaying = true
        playJob = scope.launch {
            var last = TimeSource.Monotonic.markNow()
            while (isActive && currentTime < project.duration) {
                delay(16)
                val elapsed = last.elapsedNow().inWholeMicroseconds / 1e6
                last = TimeSource.Monotonic.markNow()
                currentTime = min(currentTime + elapsed, project.duration)
                requestRender()
            }
            isPlaying = false
            requestRender()
        }
    }

    private fun stopPlayback() {
        playJob?.cancel()
        isPlaying = false
        requestRender()
    }

    fun seek(time: Double) {
        currentTime = time.coerceIn(0.0, project.duration)
        requestRender()
    }

    // endregion

    // region Qatlamlar

    fun addText() = append(newLayer("Matn", LayerContent.Text(), scale = 1.0))

    fun addShape(kind: ShapeKind) = append(newLayer(kind.title, LayerContent.Shape(kind), scale = 1.0))

    fun addSolid() = append(
        newLayer("Solid", LayerContent.Shape(ShapeKind.RECTANGLE, project.width.toFloat(), project.height.toFloat()), scale = 1.0),
    )

    fun addImages(paths: List<String>) {
        val path = paths.firstOrNull() ?: return
        scope.launch {
            isImporting = true
            try {
                val image = withContext(Dispatchers.Default) { media.loadImage(path) } ?: error("Rasmni o'qib bo'lmadi")
                val content = LayerContent.Image(path, image.width, image.height)
                append(newLayer("Rasm", content, fitScale(content.width, content.height)))
            } catch (error: Exception) {
                errorMessage = "Rasmni qo'shib bo'lmadi: ${error.message}"
            } finally {
                isImporting = false
            }
        }
    }

    fun addVideos(paths: List<String>) {
        val path = paths.firstOrNull() ?: return
        scope.launch {
            isImporting = true
            try {
                val info = withContext(Dispatchers.Default) { media.videoInfo(path) }
                val content = LayerContent.Video(path, info.durationSec, info.width, info.height)
                var updated = project
                if (updated.layers.isEmpty()) updated = updated.copy(duration = content.durationSec.coerceIn(1.0, 60.0))
                val start = if (project.layers.isEmpty()) 0.0 else currentTime.coerceAtMost((updated.duration - 0.5).coerceAtLeast(0.0))
                val layer = MotionLayer(
                    name = nextName("Video"),
                    content = content,
                    inPoint = start,
                    outPoint = min(start + content.durationSec, updated.duration),
                    position = Animatable(updated.center),
                    scale = Animatable(fitScale(content.width, content.height)),
                )
                updateProject { updated.copy(layers = updated.layers + layer) }
                selectedLayerId = layer.id
            } catch (error: Exception) {
                errorMessage = "Videoni qo'shib bo'lmadi: ${error.message}"
            } finally {
                isImporting = false
            }
        }
    }

    fun duplicateLayer(id: String) {
        val index = project.layers.indexOfFirst { it.id == id }
        if (index < 0) return
        val original = project.layers[index]
        val copy = original.copy(id = com.jas.montaj.core.newId(), name = "${original.name} nusxa")
        updateProject { it.copy(layers = it.layers.toMutableList().apply { add(index + 1, copy) }) }
        selectedLayerId = copy.id
    }

    fun deleteLayer(id: String) {
        updateProject { current -> current.copy(layers = current.layers.filterNot { it.id == id }) }
    }

    /** +1 — yuqoriga (oldinga), −1 — pastga (orqaga). */
    fun moveLayer(id: String, offset: Int) = updateProject { current ->
        val index = current.layers.indexOfFirst { it.id == id }
        val target = index + offset
        if (index < 0 || target !in current.layers.indices) return@updateProject current
        val layers = current.layers.toMutableList()
        layers[index] = layers[target].also { layers[target] = layers[index] }
        current.copy(layers = layers)
    }

    private fun newLayer(prefix: String, content: LayerContent, scale: Double) = MotionLayer(
        name = nextName(prefix),
        content = content,
        inPoint = 0.0,
        outPoint = project.duration,
        position = Animatable(project.center),
        scale = Animatable(scale),
    )

    private fun append(layer: MotionLayer) {
        updateProject { it.copy(layers = it.layers + layer) }
        selectedLayerId = layer.id
    }

    private fun nextName(prefix: String): String = "$prefix ${project.layers.count { it.name.startsWith(prefix) } + 1}"

    private fun fitScale(width: Int, height: Int): Double {
        if (width <= 0 || height <= 0) return 1.0
        return min(project.width / width.toDouble(), project.height / height.toDouble())
    }

    // endregion

    fun export() {
        stopPlayback()
        val snapshot = project
        val renderer = MotionRenderer(TextRenderer(fonts), media)
        val audio = snapshot.layers.mapNotNull { layer ->
            val video = layer.content as? LayerContent.Video ?: return@mapNotNull null
            if (!layer.visible || video.volume <= 0f) return@mapNotNull null
            val length = min(layer.outPoint - layer.inPoint, video.durationSec)
            if (length <= 0) null else AudioSegment(video.path, 0.0, length, layer.inPoint, volume = video.volume)
        }
        val job = ExportJob(
            width = snapshot.width,
            height = snapshot.height,
            fps = 30,
            durationSec = snapshot.duration,
            audio = audio,
            renderFrame = { time, exportFrames -> renderer.render(snapshot, time, 1f, exportFrames, exactVideo = true) },
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
        frames.close()
        scope.cancel()
    }

    companion object {
        /** Ko'rish oynasi tezligi uchun kadr yarim o'lchamda chiziladi. */
        const val PREVIEW_SCALE = 0.5f
    }
}
