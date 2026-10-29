@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.jas.montaj.platform

import androidx.compose.ui.graphics.asSkiaBitmap
import com.jas.montaj.core.newId
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.plus
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import platform.AVFoundation.AVAssetExportPresetHighestQuality
import platform.AVFoundation.AVAssetExportSession
import platform.AVFoundation.AVAssetExportSessionStatusCompleted
import platform.AVFoundation.AVAssetTrack
import platform.AVFoundation.AVAssetWriter
import platform.AVFoundation.AVAssetWriterInput
import platform.AVFoundation.AVAssetWriterInputPixelBufferAdaptor
import platform.AVFoundation.AVAssetWriterStatusCompleted
import platform.AVFoundation.AVAudioTimePitchAlgorithmSpectral
import platform.AVFoundation.AVFileTypeMPEG4
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMutableAudioMix
import platform.AVFoundation.AVMutableAudioMixInputParameters
import platform.AVFoundation.AVMutableComposition
import platform.AVFoundation.AVVideoCodecKey
import platform.AVFoundation.AVVideoCodecTypeH264
import platform.AVFoundation.AVVideoHeightKey
import platform.AVFoundation.AVVideoWidthKey
import platform.AVFoundation.duration
import platform.AVFoundation.tracksWithMediaType
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMake
import platform.CoreMedia.CMTimeRangeMake
import platform.CoreMedia.kCMPersistentTrackID_Invalid
import platform.CoreVideo.CVPixelBufferGetBaseAddress
import platform.CoreVideo.CVPixelBufferGetBytesPerRow
import platform.CoreVideo.CVPixelBufferLockBaseAddress
import platform.CoreVideo.CVPixelBufferPoolCreatePixelBuffer
import platform.CoreVideo.CVPixelBufferRefVar
import platform.CoreVideo.CVPixelBufferRelease
import platform.CoreVideo.CVPixelBufferUnlockBaseAddress
import platform.CoreVideo.kCVPixelFormatType_32BGRA
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Photos.PHAccessLevelAddOnly
import platform.Photos.PHAssetChangeRequest
import platform.Photos.PHAuthorizationStatusAuthorized
import platform.Photos.PHAuthorizationStatusLimited
import platform.Photos.PHPhotoLibrary
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * iOS eksporti: kadrlarni umumiy renderer chizadi, AVAssetWriter videoga yozadi,
 * so'ng ovoz bo'laklari AVMutableComposition'da qo'shilib, galereyaga saqlanadi.
 */
internal object IosExporter {
    suspend fun export(job: ExportJob, onProgress: (Float) -> Unit): ExportedVideo {
        val videoOnly = "${IosMediaBackend.exportsDir}/${newId()}_video.mp4"
        val final = "${IosMediaBackend.exportsDir}/Montaj_${newId()}.mp4"
        val audioShare = if (job.audio.isEmpty()) 0f else 0.15f

        withContext(Dispatchers.Default) {
            writeVideo(job, videoOnly) { onProgressOnMain(onProgress, it * (1 - audioShare)) }
        }
        if (job.audio.isEmpty()) {
            NSFileManager.defaultManager.moveItemAtPath(videoOnly, toPath = final, error = null)
        } else {
            mixAudio(job, videoOnly, final)
            NSFileManager.defaultManager.removeItemAtPath(videoOnly, error = null)
        }
        saveToPhotos(final)
        onProgress(1f)
        return ExportedVideo(final)
    }

    private suspend fun onProgressOnMain(onProgress: (Float) -> Unit, value: Float) {
        withContext(Dispatchers.Main) { onProgress(value) }
    }

    private suspend fun writeVideo(job: ExportJob, path: String, onProgress: suspend (Float) -> Unit) {
        val writer = AVAssetWriter.assetWriterWithURL(NSURL.fileURLWithPath(path), fileType = AVFileTypeMPEG4, error = null)
            ?: error("Video yozuvchini ochib bo'lmadi")
        val input = AVAssetWriterInput.assetWriterInputWithMediaType(
            AVMediaTypeVideo,
            outputSettings = mapOf<Any?, Any?>(
                AVVideoCodecKey to AVVideoCodecTypeH264,
                AVVideoWidthKey to job.width,
                AVVideoHeightKey to job.height,
            ),
        )
        input.expectsMediaDataInRealTime = false
        val adaptor = AVAssetWriterInputPixelBufferAdaptor.assetWriterInputPixelBufferAdaptorWithAssetWriterInput(
            input,
            sourcePixelBufferAttributes = mapOf<Any?, Any?>(
                "PixelFormatType" to kCVPixelFormatType_32BGRA,
                "Width" to job.width,
                "Height" to job.height,
            ),
        )
        writer.addInput(input)
        check(writer.startWriting()) { "Video yozishni boshlab bo'lmadi: ${writer.error?.localizedDescription}" }
        writer.startSessionAtSourceTime(CMTimeMake(0, job.fps))

        val frames = IosMediaBackend.openFrameSource()
        val total = (job.durationSec * job.fps).toInt().coerceAtLeast(1)
        val info = ImageInfo(job.width, job.height, ColorType.BGRA_8888, ColorAlphaType.PREMUL)
        try {
            for (index in 0 until total) {
                while (!input.readyForMoreMediaData) delay(5)
                val image = job.renderFrame(index.toDouble() / job.fps, frames)
                val pixels = image.asSkiaBitmap().readPixels(info, job.width * 4, 0, 0) ?: continue
                val pool = adaptor.pixelBufferPool ?: error("Piksel buferi yo'q")
                memScoped {
                    val bufferVar = alloc<CVPixelBufferRefVar>()
                    CVPixelBufferPoolCreatePixelBuffer(null, pool, bufferVar.ptr)
                    val buffer = bufferVar.value ?: return@memScoped
                    CVPixelBufferLockBaseAddress(buffer, 0u)
                    val base = CVPixelBufferGetBaseAddress(buffer)?.reinterpret<kotlinx.cinterop.ByteVar>()
                    val bytesPerRow = CVPixelBufferGetBytesPerRow(buffer).toInt()
                    if (base != null) {
                        pixels.usePinned { pinned ->
                            for (row in 0 until job.height) {
                                memcpy(base + row * bytesPerRow, pinned.addressOf(row * job.width * 4), (job.width * 4).convert())
                            }
                        }
                    }
                    CVPixelBufferUnlockBaseAddress(buffer, 0u)
                    adaptor.appendPixelBuffer(buffer, withPresentationTime = CMTimeMake(index.toLong(), job.fps))
                    CVPixelBufferRelease(buffer)
                }
                if (index % 5 == 0) onProgress(index.toFloat() / total)
            }
        } finally {
            frames.close()
        }
        input.markAsFinished()
        suspendCancellableCoroutine { continuation ->
            writer.finishWritingWithCompletionHandler { continuation.resume(Unit) }
        }
        check(writer.status == AVAssetWriterStatusCompleted) { "Video yozilmadi: ${writer.error?.localizedDescription}" }
    }

    /** Yozilgan video ustiga ovoz bo'laklarini (tezlik, takrorlash, balandlik bilan) qo'shadi. */
    private suspend fun mixAudio(job: ExportJob, videoPath: String, outputPath: String) {
        val composition = AVMutableComposition()
        val videoAsset = IosMediaBackend.asset(videoPath)
        val videoDuration = CMTimeGetSeconds(videoAsset.duration)
        val sourceVideo = videoAsset.tracksWithMediaType(AVMediaTypeVideo).firstOrNull() as? AVAssetTrack ?: error("Video trek yo'q")
        composition.addMutableTrackWithMediaType(AVMediaTypeVideo, preferredTrackID = kCMPersistentTrackID_Invalid)
            ?.insertTimeRange(CMTimeRangeMake(time(0.0), videoAsset.duration), ofTrack = sourceVideo, atTime = time(0.0), error = null)

        val parameters = mutableListOf<AVMutableAudioMixInputParameters>()
        for (segment in job.audio) {
            val track = if (segment.loop) {
                CompositionFactory.insertLooped(composition, segment.path, videoDuration)
            } else {
                val asset = IosMediaBackend.asset(segment.path)
                val source = asset.tracksWithMediaType(AVMediaTypeAudio).firstOrNull() as? AVAssetTrack ?: continue
                val available = (videoDuration - segment.startAt) * segment.speed
                val length = minOf(segment.sourceEnd - segment.sourceStart, available)
                if (length <= 0.01) continue
                composition.addMutableTrackWithMediaType(AVMediaTypeAudio, preferredTrackID = kCMPersistentTrackID_Invalid)?.also { track ->
                    track.insertTimeRange(CMTimeRangeMake(time(segment.sourceStart), time(length)), ofTrack = source, atTime = time(segment.startAt), error = null)
                    if (segment.speed != 1.0) {
                        track.scaleTimeRange(CMTimeRangeMake(time(segment.startAt), time(length)), toDuration = time(length / segment.speed))
                    }
                }
            } ?: continue
            parameters += AVMutableAudioMixInputParameters.audioMixInputParametersWithTrack(track).apply {
                setVolume(segment.volume, atTime = time(0.0))
            }
        }
        val mix = AVMutableAudioMix()
        mix.inputParameters = parameters

        val session = AVAssetExportSession(asset = composition, presetName = AVAssetExportPresetHighestQuality)
        session.outputURL = NSURL.fileURLWithPath(outputPath)
        session.outputFileType = AVFileTypeMPEG4
        session.audioMix = mix
        session.audioTimePitchAlgorithm = AVAudioTimePitchAlgorithmSpectral
        suspendCancellableCoroutine { continuation ->
            session.exportAsynchronouslyWithCompletionHandler {
                if (session.status == AVAssetExportSessionStatusCompleted) {
                    continuation.resume(Unit)
                } else {
                    continuation.resumeWithException(IllegalStateException(session.error?.localizedDescription ?: "Ovozni qo'shib bo'lmadi"))
                }
            }
        }
    }

    private suspend fun saveToPhotos(path: String) {
        val status = suspendCancellableCoroutine { continuation ->
            PHPhotoLibrary.requestAuthorizationForAccessLevel(PHAccessLevelAddOnly) { continuation.resume(it) }
        }
        if (status != PHAuthorizationStatusAuthorized && status != PHAuthorizationStatusLimited) {
            error("Galereyaga saqlash uchun Sozlamalarda ruxsat bering")
        }
        suspendCancellableCoroutine { continuation ->
            PHPhotoLibrary.sharedPhotoLibrary().performChanges({
                PHAssetChangeRequest.creationRequestForAssetFromVideoAtFileURL(NSURL.fileURLWithPath(path))
            }) { success, error ->
                if (success) continuation.resume(Unit) else continuation.resumeWithException(IllegalStateException(error?.localizedDescription ?: "Saqlab bo'lmadi"))
            }
        }
    }
}
