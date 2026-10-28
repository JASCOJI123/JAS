import AVFoundation

struct BuiltComposition {
    let composition: AVComposition
    let videoComposition: AVVideoComposition
    let audioMix: AVAudioMix

    var duration: CMTime { composition.duration }
}

/// Loyiha holatidan (klip, tezlik, musiqa, matn, filtr) AVFoundation kompozitsiyasini quradi.
@MainActor
enum CompositionBuilder {
    /// Vertikal 9:16 (Reels / TikTok / Shorts).
    static let renderSize = CGSize(width: 1080, height: 1920)
    static let frameDuration = CMTime(value: 1, timescale: 30)
    private static let timescale: CMTimeScale = 600
    private static let musicFadeOut: Double = 1.5

    private struct Segment {
        let timeRange: CMTimeRange
        let orientation: CGImagePropertyOrientation
    }

    static func build(_ snapshot: EditorSnapshot) async throws -> BuiltComposition {
        let composition = AVMutableComposition()
        guard let videoTrack = composition.addMutableTrack(withMediaType: .video, preferredTrackID: kCMPersistentTrackID_Invalid),
              let originalAudioTrack = composition.addMutableTrack(withMediaType: .audio, preferredTrackID: kCMPersistentTrackID_Invalid)
        else { throw MontajError.compositionFailed }

        var segments: [Segment] = []
        var cursor = CMTime.zero
        var hasOriginalAudio = false

        for clip in snapshot.clips {
            let asset = AVURLAsset(url: clip.url)
            guard let sourceVideo = try await asset.loadTracks(withMediaType: .video).first else {
                throw MontajError.noVideoTrack
            }
            let (transform, videoTrackRange) = try await sourceVideo.load(.preferredTransform, .timeRange)
            let requested = CMTimeRange(start: time(clip.trimStart), end: time(clip.trimEnd))
            let sourceRange = requested.intersection(videoTrackRange)
            guard !sourceRange.isEmpty else { continue }

            try videoTrack.insertTimeRange(sourceRange, of: sourceVideo, at: cursor)

            if let sourceAudio = try await asset.loadTracks(withMediaType: .audio).first {
                let audioTrackRange = try await sourceAudio.load(.timeRange)
                let audioRange = sourceRange.intersection(audioTrackRange)
                if !audioRange.isEmpty {
                    try originalAudioTrack.insertTimeRange(audioRange, of: sourceAudio, at: cursor + (audioRange.start - sourceRange.start))
                    hasOriginalAudio = true
                }
            }

            let outputDuration = CMTimeMultiplyByFloat64(sourceRange.duration, multiplier: 1 / clip.speed)
            if clip.speed != 1 {
                composition.scaleTimeRange(CMTimeRange(start: cursor, duration: sourceRange.duration), toDuration: outputDuration)
            }
            segments.append(Segment(timeRange: CMTimeRange(start: cursor, duration: outputDuration), orientation: transform.imageOrientation))
            cursor = cursor + outputDuration
        }

        guard !segments.isEmpty else { throw MontajError.noClips }
        if !hasOriginalAudio {
            composition.removeTrack(originalAudioTrack)
        }

        // Ko'rsatmalar kompozitsiyani bo'shliqsiz, oxirigacha qoplashi shart:
        // tezlik o'zgarishidagi yaxlitlash farqini oxirgi segmentga qo'shamiz.
        let totalDuration = composition.duration
        if let last = segments.popLast() {
            segments.append(Segment(timeRange: CMTimeRange(start: last.timeRange.start, end: totalDuration), orientation: last.orientation))
        }

        let musicTrack = try await insertMusic(snapshot.music, into: composition, duration: totalDuration)

        let audioMix = AVMutableAudioMix()
        var parameters: [AVMutableAudioMixInputParameters] = []
        if hasOriginalAudio {
            let original = AVMutableAudioMixInputParameters(track: originalAudioTrack)
            original.setVolume(snapshot.originalVolume, at: .zero)
            parameters.append(original)
        }
        if let musicTrack {
            let music = AVMutableAudioMixInputParameters(track: musicTrack)
            music.setVolume(snapshot.musicVolume, at: .zero)
            let fade = min(time(musicFadeOut), totalDuration)
            music.setVolumeRamp(fromStartVolume: snapshot.musicVolume, toEndVolume: 0, timeRange: CMTimeRange(start: totalDuration - fade, duration: fade))
            parameters.append(music)
        }
        audioMix.inputParameters = parameters

        let overlays = snapshot.overlays.compactMap {
            OverlayRenderer.render($0, canvasSize: renderSize, videoDuration: totalDuration)
        }
        let style = RenderStyle(renderSize: renderSize, filter: snapshot.filter, overlays: overlays)

        let videoComposition = AVMutableVideoComposition()
        videoComposition.customVideoCompositorClass = MontajCompositor.self
        videoComposition.renderSize = renderSize
        videoComposition.frameDuration = frameDuration
        videoComposition.instructions = segments.map { segment -> AVVideoCompositionInstructionProtocol in
            MontajInstruction(timeRange: segment.timeRange, sourceTrackID: videoTrack.trackID, orientation: segment.orientation, style: style)
        }

        return BuiltComposition(composition: composition, videoComposition: videoComposition, audioMix: audioMix)
    }

    /// Musiqani video uzunligigacha (kerak bo'lsa takrorlab) qo'yadi.
    private static func insertMusic(_ music: MusicTrack?, into composition: AVMutableComposition, duration: CMTime) async throws -> AVMutableCompositionTrack? {
        guard let music,
              let source = try await AVURLAsset(url: music.url).loadTracks(withMediaType: .audio).first
        else { return nil }

        let sourceRange = try await source.load(.timeRange)
        guard sourceRange.duration.seconds > 0.5,
              let track = composition.addMutableTrack(withMediaType: .audio, preferredTrackID: kCMPersistentTrackID_Invalid)
        else { return nil }

        var position = CMTime.zero
        while position < duration {
            let length = min(sourceRange.duration, duration - position)
            try track.insertTimeRange(CMTimeRange(start: sourceRange.start, duration: length), of: source, at: position)
            position = position + length
        }
        return track
    }

    private static func time(_ seconds: Double) -> CMTime {
        CMTime(seconds: seconds, preferredTimescale: timescale)
    }
}
