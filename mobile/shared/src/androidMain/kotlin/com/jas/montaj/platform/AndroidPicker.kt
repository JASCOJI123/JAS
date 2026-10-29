package com.jas.montaj.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.jas.montaj.core.newId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

@Composable
actual fun rememberMediaPicker(kind: PickKind, multiple: Boolean, onPicked: (List<String>) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val callback = rememberUpdatedState(onPicked)
    val deliver: (List<Uri>) -> Unit = { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val paths = withContext(Dispatchers.IO) {
                    uris.mapNotNull { uri -> runCatching { importToMediaDir(AndroidPlatform.context, uri, kind) }.getOrNull() }
                }
                callback.value(paths)
            }
        }
    }

    return when (kind) {
        PickKind.AUDIO -> {
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> deliver(listOfNotNull(uri)) }
            { launcher.launch("audio/*") }
        }
        else -> {
            val type = if (kind == PickKind.VIDEO) ActivityResultContracts.PickVisualMedia.VideoOnly else ActivityResultContracts.PickVisualMedia.ImageOnly
            if (multiple) {
                val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris -> deliver(uris) }
                { launcher.launch(PickVisualMediaRequest(type)) }
            } else {
                val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> deliver(listOfNotNull(uri)) }
                { launcher.launch(PickVisualMediaRequest(type)) }
            }
        }
    }
}

/** Faylni ilovaning doimiy papkasiga nusxalaydi (rasmlar tezlik uchun 2048 px gacha kichraytiriladi). */
private fun importToMediaDir(context: Context, uri: Uri, kind: PickKind): String {
    if (kind == PickKind.IMAGE) return importImage(context, uri)
    val mime = context.contentResolver.getType(uri)
    val fallback = if (kind == PickKind.VIDEO) "mp4" else "m4a"
    val extension = mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: fallback
    val file = File(AndroidPlatform.mediaDir, "${newId()}.$extension")
    val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Faylni ochib bo'lmadi")
    input.use { source -> file.outputStream().use { source.copyTo(it) } }
    return file.absolutePath
}

private fun importImage(context: Context, uri: Uri, maxDimension: Int = 2048): String {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxDimension || bounds.outHeight / (sample * 2) >= maxDimension) sample *= 2
    val bitmap = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: throw IOException("Rasmni o'qib bo'lmadi")
    val file = File(AndroidPlatform.mediaDir, "${newId()}.png")
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bitmap.recycle()
    return file.absolutePath
}
