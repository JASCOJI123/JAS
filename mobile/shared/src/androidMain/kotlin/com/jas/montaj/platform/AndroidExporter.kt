package com.jas.montaj.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataOutputStream
import java.io.File

/**
 * Android eksporti: qora rasm "tashuvchi" vaqt o'qini beradi, har bir kadrni umumiy renderer chizadi
 * (BitmapOverlay), ovoz bo'laklari alohida ketma-ketliklarda aralashtiriladi (Media3 Transformer).
 */
internal object AndroidExporter {
    private const val SAMPLE_RATE = 44_100

    suspend fun export(context: Context, job: ExportJob, onProgress: (Float) -> Unit): ExportedVideo {
        val frames = AndroidMediaBackend.openFrameSource()
        try {
            val composition = composition(context, job, frames)
            val file = run(context, composition, onProgress)
            val uri = withContext(Dispatchers.IO) {
                try {
                    AndroidMediaBackend.saveToGallery(file)
                } finally {
                    file.delete()
                }
            }
            return ExportedVideo(uri.toString())
        } finally {
            frames.close()
        }
    }

    private fun composition(context: Context, job: ExportJob, frames: FrameSource): Composition {
        val carrier = carrierImage(context, job.width, job.height)
        val videoItem = EditedMediaItem.Builder(
            MediaItem.Builder()
                .setUri(Uri.fromFile(carrier))
                .setImageDurationMs((job.durationSec * 1000).toLong())
                .build(),
        ).setFrameRate(job.fps).build()

        val overlay = object : BitmapOverlay() {
            override fun getBitmap(presentationTimeUs: Long): Bitmap =
                job.renderFrame(presentationTimeUs / 1_000_000.0, frames).asAndroidBitmap()
        }

        val sequences = mutableListOf(EditedMediaItemSequence(listOf(videoItem)))
        sequences += audioSequences(context, job)

        return Composition.Builder(sequences)
            .setEffects(Effects(emptyList(), listOf(OverlayEffect(ImmutableList.of<TextureOverlay>(overlay)))))
            .experimentalSetForceAudioTrack(job.audio.isNotEmpty())
            .build()
    }

    /** Takrorlanuvchi bo'laklar o'z ketma-ketligida; qolganlari bir-birini qoplamaydigan "treklar"ga joylanadi. */
    private fun audioSequences(context: Context, job: ExportJob): List<EditedMediaItemSequence> {
        val result = mutableListOf<EditedMediaItemSequence>()
        job.audio.filter { it.loop }.forEach { segment ->
            result += EditedMediaItemSequence(listOf(audioItem(segment.copy(sourceEnd = segment.sourceEnd))), true)
        }
        val tracks = mutableListOf<MutableList<AudioSegment>>()
        job.audio.filterNot { it.loop }.sortedBy { it.startAt }.forEach { segment ->
            val track = tracks.firstOrNull { trackEnd(it) <= segment.startAt + 0.001 }
            if (track != null) track += segment else tracks += mutableListOf(segment)
        }
        tracks.forEach { track ->
            val items = mutableListOf<EditedMediaItem>()
            var cursor = 0.0
            track.forEach { segment ->
                val start = segment.startAt.coerceAtMost(job.durationSec)
                if (start - cursor > 0.01) items += silenceItem(context, start - cursor)
                val available = (job.durationSec - start) * segment.speed
                val end = minOf(segment.sourceEnd, segment.sourceStart + available)
                if (end - segment.sourceStart > 0.01) {
                    items += audioItem(segment.copy(sourceEnd = end))
                    cursor = start + (end - segment.sourceStart) / segment.speed
                }
            }
            if (items.isNotEmpty()) result += EditedMediaItemSequence(items)
        }
        return result
    }

    private fun trackEnd(track: List<AudioSegment>): Double =
        track.maxOf { it.startAt + (it.sourceEnd - it.sourceStart) / it.speed }

    private fun audioItem(segment: AudioSegment): EditedMediaItem {
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.fromFile(File(segment.path)))
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs((segment.sourceStart * 1000).toLong())
                    .setEndPositionMs((segment.sourceEnd * 1000).toLong())
                    .build(),
            )
            .build()
        val processors = mutableListOf<AudioProcessor>()
        if (segment.speed != 1.0) {
            processors += SonicAudioProcessor().apply {
                setSpeed(segment.speed.toFloat())
                setPitch(1f)
            }
        }
        processors += volumeProcessor(segment.volume)
        return EditedMediaItem.Builder(mediaItem)
            .setRemoveVideo(true)
            .setEffects(Effects(processors, emptyList()))
            .build()
    }

    private fun volumeProcessor(volume: Float) = ChannelMixingAudioProcessor().apply {
        putChannelMixingMatrix(ChannelMixingMatrix.create(1, 2).scaleBy(volume))
        putChannelMixingMatrix(ChannelMixingMatrix.create(2, 2).scaleBy(volume))
    }

    /** Bo'shliqni to'ldiruvchi jim WAV (bo'laklar kerakli soniyadan boshlanishi uchun). */
    private fun silenceItem(context: Context, seconds: Double): EditedMediaItem {
        val millis = (seconds * 1000).toLong()
        val file = File(context.cacheDir, "silence_$millis.wav")
        if (!file.exists()) writeSilence(file, millis)
        return EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(file))).build()
    }

    private fun writeSilence(file: File, millis: Long) {
        val channels = 2
        val bytesPerSample = 2
        val samples = SAMPLE_RATE * millis / 1000
        val dataSize = (samples * channels * bytesPerSample).toInt()
        DataOutputStream(file.outputStream().buffered()).use { out ->
            fun intLe(value: Int) = out.write(byteArrayOf(value.toByte(), (value shr 8).toByte(), (value shr 16).toByte(), (value shr 24).toByte()))
            fun shortLe(value: Int) = out.write(byteArrayOf(value.toByte(), (value shr 8).toByte()))
            out.writeBytes("RIFF"); intLe(36 + dataSize); out.writeBytes("WAVE")
            out.writeBytes("fmt "); intLe(16); shortLe(1); shortLe(channels); intLe(SAMPLE_RATE)
            intLe(SAMPLE_RATE * channels * bytesPerSample); shortLe(channels * bytesPerSample); shortLe(16)
            out.writeBytes("data"); intLe(dataSize)
            out.write(ByteArray(dataSize))
        }
    }

    private fun carrierImage(context: Context, width: Int, height: Int): File {
        val file = File(context.cacheDir, "carrier_${width}x$height.png")
        if (file.exists()) return file
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    private suspend fun run(context: Context, composition: Composition, onProgress: (Float) -> Unit): File =
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
                            if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress / 100f)
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
