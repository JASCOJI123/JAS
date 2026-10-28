package com.jas.montaj.common

import android.content.Context
import android.net.Uri
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface ExportState {
    data object Idle : ExportState
    data class Exporting(val progress: Float) : ExportState
    data class Finished(val uri: Uri) : ExportState
}

object ExportRunner {
    /** Kompozitsiyani .mp4 ga yozadi, galereyaga saqlaydi va uning manzilini qaytaradi. */
    suspend fun exportToGallery(context: Context, composition: Composition, onProgress: (Float) -> Unit): Uri {
        val file = export(context, composition, onProgress)
        return withContext(Dispatchers.IO) {
            try {
                GallerySaver.saveVideo(context, file)
            } finally {
                file.delete()
            }
        }
    }

    private suspend fun export(context: Context, composition: Composition, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.Main) {
            val output = File(context.cacheDir, "export_${System.currentTimeMillis()}.mp4")
            val done = CompletableDeferred<Unit>()
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        done.complete(Unit)
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        done.completeExceptionally(exportException)
                    }
                })
                .build()

            transformer.start(composition, output.absolutePath)
            try {
                coroutineScope {
                    val poller = launch {
                        val holder = ProgressHolder()
                        while (isActive) {
                            if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                                onProgress(holder.progress / 100f)
                            }
                            delay(200)
                        }
                    }
                    done.await()
                    poller.cancel()
                }
                onProgress(1f)
                output
            } catch (error: Throwable) {
                transformer.cancel()
                output.delete()
                throw error
            }
        }
}
