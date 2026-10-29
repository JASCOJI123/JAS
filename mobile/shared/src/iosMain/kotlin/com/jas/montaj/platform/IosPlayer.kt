@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.jas.montaj.platform

import platform.AVFoundation.*
import platform.CoreGraphics.*
import platform.CoreImage.*
import platform.CoreMedia.*
import platform.UIKit.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.interpretObjCPointer
import kotlinx.cinterop.objcPtr

/** Montaj ko'rish oynasi: kliplar bitta AVMutableComposition'ga yig'ilib, AVPlayer'da ijro etiladi. */
internal class IosPreviewPlayer : PreviewPlayer {
    val player = AVPlayer()
    private var duration = 0.0

    override val isPlaying: Boolean get() = player.rate > 0f

    override fun currentTime(): Double = CMTimeGetSeconds(player.currentTime()).takeIf { !it.isNaN() } ?: 0.0

    override fun setTimeline(timeline: PreviewTimeline, resumeAt: Double) {
        val wasPlaying = isPlaying
        val built = CompositionFactory.build(timeline.clips.map { AudioClipRange(it.path, it.start, it.end, it.speed) }, timeline)
        duration = built.durationSec
        val item = AVPlayerItem(asset = built.composition)
        item.audioMix = built.audioMix
        timeline.colorMatrix?.let { item.videoComposition = colorMatrixComposition(built.composition, it) }
        player.replaceCurrentItemWithPlayerItem(item)
        seek(resumeAt)
        if (wasPlaying) play()
    }

    override fun play() {
        if (currentTime() >= duration - 0.05) seek(0.0)
        player.play()
    }

    override fun pause() = player.pause()

    override fun seek(timeSec: Double) {
        player.seekToTime(time(timeSec), toleranceBefore = time(0.0), toleranceAfter = time(0.0))
    }

    override fun release() {
        player.pause()
        player.replaceCurrentItemWithPlayerItem(null)
    }

    /** Umumiy 4x5 matritsani CIColorMatrix bilan qo'llaydi. */
    private fun colorMatrixComposition(composition: AVMutableComposition, m: FloatArray): AVVideoComposition =
        AVVideoComposition.videoCompositionWithAsset(composition) { request: AVAsynchronousCIImageFilteringRequest? ->
            if (request != null) {
                val source = request.sourceImage
                val filter = CIFilter.filterWithName("CIColorMatrix")
                val output: CIImage? = filter?.let {
                    it.setValue(source, forKey = "inputImage")
                    it.setValue(vector(m, 0), forKey = "inputRVector")
                    it.setValue(vector(m, 1), forKey = "inputGVector")
                    it.setValue(vector(m, 2), forKey = "inputBVector")
                    it.setValue(vector(m, 3), forKey = "inputAVector")
                    it.setValue(
                        CIVector.vectorWithX(m[4] / 255.0, Y = m[9] / 255.0, Z = m[14] / 255.0, W = m[19] / 255.0),
                        forKey = "inputBiasVector",
                    )
                    it.outputImage
                }
                // AVFoundation CIImage'ni "oldindan e'lon qilingan" tur sifatida ko'radi — ko'rsatkich orqali o'tkazamiz.
                val result = output?.let { interpretObjCPointer<objcnames.classes.CIImage>(it.objcPtr()) } ?: source
                request.finishWithImage(result, context = null)
            }
        }

    private fun vector(m: FloatArray, row: Int) =
        CIVector.vectorWithX(m[row * 5].toDouble(), Y = m[row * 5 + 1].toDouble(), Z = m[row * 5 + 2].toDouble(), W = m[row * 5 + 3].toDouble())
}

internal fun time(seconds: Double): CValue<CMTime> = CMTimeMakeWithSeconds(seconds, 600)

internal class AudioClipRange(val path: String, val start: Double, val end: Double, val speed: Double)

internal class BuiltComposition(val composition: AVMutableComposition, val audioMix: AVMutableAudioMix, val durationSec: Double)

/** Ko'rish oynasi uchun AVFoundation kompozitsiyasi (iOS Swift versiyasidagi mantiq). */
internal object CompositionFactory {
    fun build(clips: List<AudioClipRange>, timeline: PreviewTimeline): BuiltComposition {
        val composition = AVMutableComposition()
        val videoTrack = composition.addMutableTrackWithMediaType(AVMediaTypeVideo, preferredTrackID = kCMPersistentTrackID_Invalid)
        val audioTrack = composition.addMutableTrackWithMediaType(AVMediaTypeAudio, preferredTrackID = kCMPersistentTrackID_Invalid)
        var cursor = 0.0
        var firstTransformSet = false

        for (clip in clips) {
            val asset = IosMediaBackend.asset(clip.path)
            val sourceVideo = asset.tracksWithMediaType(AVMediaTypeVideo).firstOrNull() as? AVAssetTrack ?: continue
            val length = clip.end - clip.start
            if (length <= 0) continue
            val range = CMTimeRangeMake(time(clip.start), time(length))
            videoTrack?.insertTimeRange(range, ofTrack = sourceVideo, atTime = time(cursor), error = null)
            if (!firstTransformSet) {
                videoTrack?.preferredTransform = sourceVideo.preferredTransform
                firstTransformSet = true
            }
            (asset.tracksWithMediaType(AVMediaTypeAudio).firstOrNull() as? AVAssetTrack)?.let { sourceAudio ->
                audioTrack?.insertTimeRange(range, ofTrack = sourceAudio, atTime = time(cursor), error = null)
            }
            val output = length / clip.speed
            if (clip.speed != 1.0) composition.scaleTimeRange(CMTimeRangeMake(time(cursor), time(length)), toDuration = time(output))
            cursor += output
        }

        val parameters = mutableListOf<AVMutableAudioMixInputParameters>()
        audioTrack?.let { track ->
            parameters += AVMutableAudioMixInputParameters.audioMixInputParametersWithTrack(track).apply {
                setVolume(timeline.originalVolume, atTime = time(0.0))
            }
        }
        val musicPath = timeline.musicPath
        if (musicPath != null && timeline.musicDuration > 0.5 && cursor > 0) {
            insertLooped(composition, musicPath, cursor)?.let { track ->
                parameters += AVMutableAudioMixInputParameters.audioMixInputParametersWithTrack(track).apply {
                    setVolume(timeline.musicVolume, atTime = time(0.0))
                }
            }
        }
        val mix = AVMutableAudioMix()
        mix.setInputParameters(parameters)
        return BuiltComposition(composition, mix, cursor)
    }

    fun insertLooped(composition: AVMutableComposition, path: String, until: Double): AVMutableCompositionTrack? {
        val asset = IosMediaBackend.asset(path)
        val source = asset.tracksWithMediaType(AVMediaTypeAudio).firstOrNull() as? AVAssetTrack ?: return null
        val sourceDuration = CMTimeGetSeconds(asset.duration)
        if (sourceDuration <= 0.1) return null
        val track = composition.addMutableTrackWithMediaType(AVMediaTypeAudio, preferredTrackID = kCMPersistentTrackID_Invalid) ?: return null
        var position = 0.0
        while (position < until) {
            val length = minOf(sourceDuration, until - position)
            track.insertTimeRange(CMTimeRangeMake(time(0.0), time(length)), ofTrack = source, atTime = time(position), error = null)
            position += length
        }
        return track
    }
}

/** AVPlayerLayer'ni o'z o'lchamiga moslab turuvchi view. */
private class PlayerContainerView(player: AVPlayer) : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    private val playerLayer = AVPlayerLayer.playerLayerWithPlayer(player)

    init {
        backgroundColor = UIColor.blackColor
        playerLayer.videoGravity = AVLayerVideoGravityResizeAspect
        layer.addSublayer(playerLayer)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        playerLayer.frame = bounds
    }
}

@Composable
actual fun PlayerSurface(player: PreviewPlayer, modifier: Modifier) {
    val avPlayer = (player as IosPreviewPlayer).player
    UIKitView(factory = { PlayerContainerView(avPlayer) }, modifier = modifier)
}
