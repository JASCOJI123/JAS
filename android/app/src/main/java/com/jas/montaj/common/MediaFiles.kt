package com.jas.montaj.common

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File
import java.io.IOException
import java.util.UUID

object MediaFiles {
    /** Tanlangan faylni ilova keshiga nusxalaydi: ruxsat muddati tugasa ham ishlayveradi. */
    fun copyToCache(context: Context, uri: Uri, fallbackExtension: String): File {
        val mime = context.contentResolver.getType(uri)
        val extension = mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: fallbackExtension
        val file = File(context.cacheDir, "${UUID.randomUUID()}.$extension")
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Faylni ochib bo'lmadi")
        input.use { source -> file.outputStream().use { source.copyTo(it) } }
        return file
    }

    fun displayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val name = cursor.getString(0)
                if (!name.isNullOrBlank()) return name.substringBeforeLast('.')
            }
        }
        return "Musiqa"
    }

    data class VideoInfo(
        val durationSec: Double,
        /** Burilish hisobga olingan o'lcham. */
        val width: Int,
        val height: Int,
        val hasAudio: Boolean,
    )

    fun videoInfo(file: File): VideoInfo = MediaMetadataRetriever().use { retriever ->
        retriever.setDataSource(file.absolutePath)
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
        val rotated = rotation == 90 || rotation == 270
        VideoInfo(
            durationSec = durationMs / 1000.0,
            width = if (rotated) height else width,
            height = if (rotated) width else height,
            hasAudio = hasAudio,
        )
    }

    fun audioDuration(file: File): Double = MediaMetadataRetriever().use { retriever ->
        retriever.setDataSource(file.absolutePath)
        (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) / 1000.0
    }

    fun thumbnail(file: File, maxSize: Int = 320): Bitmap? = try {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(file.absolutePath)
            retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, maxSize, maxSize)
        }
    } catch (error: RuntimeException) {
        null
    }

    /** Katta rasmlarni tezlik uchun kichraytirib, PNG qilib saqlaydi. */
    fun prepareImage(context: Context, uri: Uri, maxDimension: Int = 2048): File {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxDimension || bounds.outHeight / (sample * 2) >= maxDimension) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("Rasmni o'qib bo'lmadi")
        val file = File(context.cacheDir, "${UUID.randomUUID()}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }
}

object GallerySaver {
    /** Tayyor videoni Movies/Montaj papkasiga saqlaydi (Android 10+ da ruxsat kerak emas). */
    fun saveVideo(context: Context, file: File): Uri {
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
