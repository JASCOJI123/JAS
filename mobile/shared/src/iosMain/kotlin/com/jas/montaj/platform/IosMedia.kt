@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.jas.montaj.platform

import platform.AVFoundation.*
import platform.CoreGraphics.*
import platform.CoreMedia.*
import platform.Foundation.*
import platform.UIKit.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import kotlin.math.abs

actual fun mediaBackend(): MediaBackend = IosMediaBackend

object IosMediaBackend : MediaBackend {
    /** Tanlangan va yozilgan media fayllar shu yerda doimiy saqlanadi. */
    val mediaDir: String by lazy { documentsSubdir("media") }
    val exportsDir: String by lazy { documentsSubdir("exports") }

    override fun videoInfo(path: String): VideoInfo {
        val asset = asset(path)
        val track = asset.tracksWithMediaType(AVMediaTypeVideo).firstOrNull() as? AVAssetTrack
        var width = 0
        var height = 0
        if (track != null) {
            CGSizeApplyAffineTransform(track.naturalSize, track.preferredTransform).useContents {
                width = abs(this.width).toInt()
                height = abs(this.height).toInt()
            }
        }
        return VideoInfo(
            durationSec = CMTimeGetSeconds(asset.duration),
            width = width,
            height = height,
            hasAudio = asset.tracksWithMediaType(AVMediaTypeAudio).isNotEmpty(),
        )
    }

    override fun audioDuration(path: String): Double = CMTimeGetSeconds(asset(path).duration)

    override fun loadImage(path: String): ImageBitmap? {
        val image = UIImage.imageWithContentsOfFile(path) ?: return null
        val cgImage = image.CGImage ?: return null
        return cgImageToImageBitmap(cgImage)
    }

    override fun thumbnail(path: String, maxSide: Int): ImageBitmap? {
        val generator = AVAssetImageGenerator(asset = asset(path))
        generator.appliesPreferredTrackTransform = true
        generator.maximumSize = CGSizeMake(maxSide.toDouble(), maxSide.toDouble())
        val cgImage = generator.copyCGImageAtTime(CMTimeMake(0, 600), actualTime = null, error = null) ?: return null
        return try {
            cgImageToImageBitmap(cgImage)
        } finally {
            CGImageRelease(cgImage)
        }
    }

    override fun openFrameSource(): FrameSource = IosFrameSource()

    override fun createPlayer(): PreviewPlayer = IosPreviewPlayer()

    override suspend fun export(job: ExportJob, onProgress: (Float) -> Unit): ExportedVideo = IosExporter.export(job, onProgress)

    override fun share(video: ExportedVideo) {
        val controller = UIActivityViewController(activityItems = listOf(NSURL.fileURLWithPath(video.handle)), applicationActivities = null)
        topViewController()?.presentViewController(controller, animated = true, completion = null)
    }

    fun asset(path: String): AVURLAsset = AVURLAsset(uRL = NSURL.fileURLWithPath(path), options = null)

    private fun documentsSubdir(name: String): String {
        val documents = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).first() as String
        val path = "$documents/$name"
        NSFileManager.defaultManager.createDirectoryAtPath(path, withIntermediateDirectories = true, attributes = null, error = null)
        return path
    }
}

/** Ekrandagi eng yuqori view controller (tanlash va ulashish oynalarini ochish uchun). */
fun topViewController(): UIViewController? {
    var controller = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (controller?.presentedViewController != null) controller = controller.presentedViewController
    return controller
}

/** CGImage'ni Compose (Skia) rasmiga aylantiradi. */
fun cgImageToImageBitmap(cgImage: CGImageRef): ImageBitmap {
    val width = CGImageGetWidth(cgImage).toInt()
    val height = CGImageGetHeight(cgImage).toInt()
    val bytes = ByteArray(width * height * 4)
    bytes.usePinned { pinned ->
        val colorSpace = CGColorSpaceCreateDeviceRGB()
        val context = CGBitmapContextCreate(
            pinned.addressOf(0),
            width.convert(),
            height.convert(),
            8u,
            (width * 4).convert(),
            colorSpace,
            CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value or kCGBitmapByteOrder32Big,
        )
        CGContextDrawImage(context, CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()), cgImage)
        CGContextRelease(context)
        CGColorSpaceRelease(colorSpace)
    }
    val info = ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.PREMUL)
    return Image.makeRaster(info, bytes, width * 4).toComposeImageBitmap()
}

/** Video kadrlarini olish (AVAssetImageGenerator, burilish hisobga olinadi). */
private class IosFrameSource : FrameSource {
    private val generators = HashMap<String, Pair<AVAssetImageGenerator, Boolean>>()
    private val lastFrames = HashMap<String, Triple<Double, Int, ImageBitmap>>()

    override fun frame(path: String, timeSec: Double, maxSide: Int, exact: Boolean): ImageBitmap? {
        lastFrames[path]?.let { (time, side, image) ->
            if (side == maxSide && abs(time - timeSec) < 0.015) return image
        }
        val generator = generators[path]?.takeIf { it.second == exact }?.first ?: AVAssetImageGenerator(asset = IosMediaBackend.asset(path)).also {
            it.appliesPreferredTrackTransform = true
            if (exact) {
                it.requestedTimeToleranceBefore = CMTimeMake(0, 600)
                it.requestedTimeToleranceAfter = CMTimeMake(0, 600)
            }
            generators[path] = it to exact
        }
        generator.maximumSize = CGSizeMake(maxSide.toDouble(), maxSide.toDouble())
        val cgImage = generator.copyCGImageAtTime(CMTimeMakeWithSeconds(timeSec, 600), actualTime = null, error = null)
            ?: return lastFrames[path]?.third
        val image = try {
            cgImageToImageBitmap(cgImage)
        } finally {
            CGImageRelease(cgImage)
        }
        lastFrames[path] = Triple(timeSec, maxSide, image)
        return image
    }

    override fun close() {
        generators.clear()
        lastFrames.clear()
    }
}
