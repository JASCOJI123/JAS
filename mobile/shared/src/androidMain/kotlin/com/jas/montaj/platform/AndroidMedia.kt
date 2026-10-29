package com.jas.montaj.platform

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.io.IOException
import kotlin.math.abs

/** Ilova konteksti: MainActivity ishga tushganda o'rnatiladi. */
object AndroidPlatform {
    lateinit var context: Context
        private set

    fun init(context: Context) {
        this.context = context.applicationContext
    }

    /** Tanlangan va yozilgan media fayllar shu yerda doimiy saqlanadi. */
    val mediaDir: File get() = File(context.filesDir, "media").apply { mkdirs() }
}

actual fun mediaBackend(): MediaBackend = AndroidMediaBackend

object AndroidMediaBackend : MediaBackend {
    private val context get() = AndroidPlatform.context

    override fun videoInfo(path: String): VideoInfo = MediaMetadataRetriever().use { retriever ->
        retriever.setDataSource(path)
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        val rotated = rotation == 90 || rotation == 270
        VideoInfo(
            durationSec = durationMs / 1000.0,
            width = if (rotated) height else width,
            height = if (rotated) width else height,
            hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes",
        )
    }

    override fun audioDuration(path: String): Double = MediaMetadataRetriever().use { retriever ->
        retriever.setDataSource(path)
        (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) / 1000.0
    }

    override fun loadImage(path: String): ImageBitmap? = BitmapFactory.decodeFile(path)?.asImageBitmap()

    override fun thumbnail(path: String, maxSide: Int): ImageBitmap? = try {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(path)
            retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, maxSide, maxSide)?.asImageBitmap()
        }
    } catch (error: RuntimeException) {
        null
    }

    override fun openFrameSource(): FrameSource = AndroidFrameSource()

    override fun createPlayer(): PreviewPlayer = AndroidPreviewPlayer(context)

    override suspend fun export(job: ExportJob, onProgress: (Float) -> Unit): ExportedVideo =
        AndroidExporter.export(context, job, onProgress)

    override fun share(video: ExportedVideo) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, Uri.parse(video.handle))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Ulashish").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Tayyor videoni Movies/Montaj papkasiga saqlaydi (Android 10+ da ruxsat kerak emas). */
    fun saveToGallery(file: File): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "Montaj_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/Montaj")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Galereyaga saqlab bo'lmadi")
        val output = resolver.openOutputStream(uri) ?: throw IOException("Galereyaga saqlab bo'lmadi")
        output.use { target -> file.inputStream().use { it.copyTo(target) } }
        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }
}

/** Video qatlamlar va kliplar uchun kadr olish (MediaMetadataRetriever). */
private class AndroidFrameSource : FrameSource {
    private val retrievers = HashMap<String, MediaMetadataRetriever>()
    private val lastFrames = HashMap<String, CachedFrame>()

    private class CachedFrame(val timeUs: Long, val maxSide: Int, val image: ImageBitmap)

    @Synchronized
    override fun frame(path: String, timeSec: Double, maxSide: Int, exact: Boolean): ImageBitmap? {
        val timeUs = (timeSec * 1_000_000).toLong()
        lastFrames[path]?.let { cached ->
            if (cached.maxSide == maxSide && abs(cached.timeUs - timeUs) < 15_000) return cached.image
        }
        val retriever = retrievers.getOrPut(path) { MediaMetadataRetriever().apply { setDataSource(path) } }
        val option = if (exact) MediaMetadataRetriever.OPTION_CLOSEST else MediaMetadataRetriever.OPTION_CLOSEST_SYNC
        val bitmap: Bitmap = try {
            retriever.getScaledFrameAtTime(timeUs, option, maxSide, maxSide)
        } catch (error: RuntimeException) {
            null
        } ?: return lastFrames[path]?.image
        val image = bitmap.asImageBitmap()
        lastFrames[path] = CachedFrame(timeUs, maxSide, image)
        return image
    }

    @Synchronized
    override fun close() {
        retrievers.values.forEach { it.release() }
        retrievers.clear()
        lastFrames.clear()
    }
}
