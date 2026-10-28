package com.jas.montaj.motion

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jas.montaj.common.ExportRunner
import com.jas.montaj.common.ExportState
import com.jas.montaj.common.MediaFiles
import com.jas.montaj.motion.engine.MotionExport
import com.jas.montaj.motion.engine.MotionRenderer
import com.jas.montaj.motion.engine.VideoFrameSource
import com.jas.montaj.motion.model.Animatable
import com.jas.montaj.motion.model.LayerContent
import com.jas.montaj.motion.model.MotionLayer
import com.jas.montaj.motion.model.MotionProject
import com.jas.montaj.motion.model.ShapeKind
import com.jas.montaj.motion.model.Vec2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.min

class MotionViewModel(application: Application) : AndroidViewModel(application) {
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

    private val frames = VideoFrameSource()
    private val renderer = MotionRenderer(frames)
    private val renderRequests = Channel<Unit>(Channel.CONFLATED)
    private var playJob: Job? = null
    private val context get() = getApplication<Application>()

    val selectedLayer: MotionLayer? get() = project.layers.firstOrNull { it.id == selectedLayerId }

    init {
        // Ko'rish oynasi kadrlarini fon oqimida chizadi; ortda qolgan so'rovlar tashlab yuboriladi.
        viewModelScope.launch(Dispatchers.Default) {
            for (request in renderRequests) {
                val frame = renderer.render(project, currentTime, PREVIEW_SCALE, exactVideo = !isPlaying)
                withContext(Dispatchers.Main) { previewFrame = frame.asImageBitmap() }
            }
        }
        requestRender()
    }

    private fun requestRender() {
        renderRequests.trySend(Unit)
    }

    fun updateProject(change: (MotionProject) -> MotionProject) {
        project = change(project)
        currentTime = currentTime.coerceAtMost(project.duration)
        requestRender()
    }

    fun updateLayer(id: String, change: (MotionLayer) -> MotionLayer) = updateProject { current ->
        current.copy(layers = current.layers.map { if (it.id == id) change(it) else it })
    }

    fun contentSize(layer: MotionLayer): Pair<Float, Float> = renderer.contentSize(layer)

    // region Ijro

    fun togglePlayback() {
        if (isPlaying) {
            stopPlayback()
            return
        }
        if (currentTime >= project.duration - 0.05) currentTime = 0.0
        isPlaying = true
        playJob = viewModelScope.launch {
            var last = System.nanoTime()
            while (isActive && currentTime < project.duration) {
                delay(16)
                val now = System.nanoTime()
                currentTime = min(currentTime + (now - last) / 1e9, project.duration)
                last = now
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

    fun addImage(uri: Uri) {
        viewModelScope.launch {
            isImporting = true
            try {
                val content = withContext(Dispatchers.IO) {
                    val file = MediaFiles.prepareImage(context, uri)
                    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeFile(file.absolutePath, bounds)
                    LayerContent.Image(file, bounds.outWidth, bounds.outHeight)
                }
                append(newLayer("Rasm", content, fitScale(content.width, content.height)))
            } catch (error: Exception) {
                errorMessage = "Rasmni qo'shib bo'lmadi: ${error.message}"
            } finally {
                isImporting = false
            }
        }
    }

    fun addVideo(uri: Uri) {
        viewModelScope.launch {
            isImporting = true
            try {
                val content = withContext(Dispatchers.IO) {
                    val file = MediaFiles.copyToCache(context, uri, "mp4")
                    val info = MediaFiles.videoInfo(file)
                    LayerContent.Video(file, info.durationSec, info.width, info.height)
                }
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
                project = updated.copy(layers = updated.layers + layer)
                selectedLayerId = layer.id
                requestRender()
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
        val copy = original.copy(id = UUID.randomUUID().toString(), name = "${original.name} nusxa")
        updateProject { it.copy(layers = it.layers.toMutableList().apply { add(index + 1, copy) }) }
        selectedLayerId = copy.id
    }

    fun deleteLayer(id: String) {
        if (selectedLayerId == id) selectedLayerId = null
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
        viewModelScope.launch {
            exportState = ExportState.Exporting(0f)
            val exportFrames = VideoFrameSource()
            try {
                val composition = MotionExport.composition(context, project, exportFrames)
                val uri = ExportRunner.exportToGallery(context, composition) { progress ->
                    exportState = ExportState.Exporting(progress)
                }
                exportState = ExportState.Finished(uri)
            } catch (error: Exception) {
                exportState = ExportState.Idle
                errorMessage = "Eksport qilib bo'lmadi: ${error.message}"
            } finally {
                exportFrames.release()
            }
        }
    }

    fun dismissExport() {
        exportState = ExportState.Idle
    }

    override fun onCleared() {
        frames.release()
    }

    companion object {
        /** Ko'rish oynasi tezligi uchun kadr yarim o'lchamda chiziladi. */
        const val PREVIEW_SCALE = 0.5f
    }
}
