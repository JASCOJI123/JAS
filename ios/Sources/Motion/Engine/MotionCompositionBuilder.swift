import AVFoundation

/// Motion loyihasidan AVFoundation kompozitsiyasini quradi.
/// Matn/shakl qatlamlari uchun ham vaqt o'qi kerak — uni qora "tashuvchi" video beradi.
@MainActor
enum MotionCompositionBuilder {
    private static let timescale: CMTimeScale = 600

    static func build(_ project: MotionProject, state: MotionRenderState) async throws -> BuiltComposition {
        let duration = time(project.duration)
        let composition = AVMutableComposition()

        let carrierAsset = AVURLAsset(url: try await CarrierVideo.url())
        guard let carrierSource = try await carrierAsset.loadTracks(withMediaType: .video).first,
              let carrierTrack = composition.addMutableTrack(withMediaType: .video, preferredTrackID: kCMPersistentTrackID_Invalid)
        else { throw MontajError.compositionFailed }
        let carrierRange = try await carrierSource.load(.timeRange)
        try carrierTrack.insertTimeRange(carrierRange, of: carrierSource, at: .zero)
        composition.scaleTimeRange(CMTimeRange(start: .zero, duration: carrierRange.duration), toDuration: duration)

        var videoTrackIDs: [MotionLayer.ID: CMPersistentTrackID] = [:]
        var audioParameters: [AVMutableAudioMixInputParameters] = []

        for layer in project.layers {
            guard let source = layer.videoSource else { continue }
            let start = time(min(layer.inPoint, project.duration))
            let end = time(min(layer.outPoint, project.duration, layer.inPoint + source.duration))
            guard end > start else { continue }

            let asset = AVURLAsset(url: source.url)
            guard let sourceVideo = try await asset.loadTracks(withMediaType: .video).first,
                  let videoTrack = composition.addMutableTrack(withMediaType: .video, preferredTrackID: kCMPersistentTrackID_Invalid)
            else { continue }
            let sourceRange = CMTimeRange(start: .zero, duration: end - start)
            let videoTrackRange = try await sourceVideo.load(.timeRange)
            let videoRange = sourceRange.intersection(videoTrackRange)
            guard !videoRange.isEmpty else { continue }
            try videoTrack.insertTimeRange(videoRange, of: sourceVideo, at: start)
            videoTrackIDs[layer.id] = videoTrack.trackID

            if let sourceAudio = try await asset.loadTracks(withMediaType: .audio).first,
               let audioTrack = composition.addMutableTrack(withMediaType: .audio, preferredTrackID: kCMPersistentTrackID_Invalid) {
                let audioTrackRange = try await sourceAudio.load(.timeRange)
                let audioRange = sourceRange.intersection(audioTrackRange)
                if !audioRange.isEmpty {
                    try audioTrack.insertTimeRange(audioRange, of: sourceAudio, at: start)
                    let parameters = AVMutableAudioMixInputParameters(track: audioTrack)
                    parameters.setVolume(layer.isVisible ? source.volume : 0, at: .zero)
                    audioParameters.append(parameters)
                } else {
                    composition.removeTrack(audioTrack)
                }
            }
        }

        let audioMix = AVMutableAudioMix()
        audioMix.inputParameters = audioParameters

        let videoComposition = AVMutableVideoComposition()
        videoComposition.customVideoCompositorClass = MotionCompositor.self
        videoComposition.renderSize = project.canvasSize.cgSize
        videoComposition.frameDuration = CompositionBuilder.frameDuration
        videoComposition.instructions = [
            MotionInstruction(
                timeRange: CMTimeRange(start: .zero, duration: composition.duration),
                state: state,
                carrierTrackID: carrierTrack.trackID,
                videoTrackIDs: videoTrackIDs
            ),
        ]

        return BuiltComposition(composition: composition, videoComposition: videoComposition, audioMix: audioMix)
    }

    /// Kompozitsiyani qayta qurishni talab qiladigan o'zgarishlar (media va vaqt).
    /// Qolgan hamma narsa (transform, effekt, matn...) kadrni qayta chizish bilan yangilanadi.
    static func mediaSignature(of project: MotionProject) -> [AnyHashable] {
        var signature: [AnyHashable] = [project.duration, project.canvasSize]
        for layer in project.layers {
            guard let source = layer.videoSource else { continue }
            signature.append([AnyHashable(layer.id), source, layer.inPoint, layer.outPoint, layer.isVisible])
        }
        return signature
    }

    private static func time(_ seconds: Double) -> CMTime {
        CMTime(seconds: seconds, preferredTimescale: timescale)
    }
}

/// 1 soniyalik kichkina qora video: kompozitsiyaga uzunlik beradi, kompozitor uni chizmaydi.
@MainActor
enum CarrierVideo {
    private static var pending: Task<URL, Error>?

    static func url() async throws -> URL {
        let destination = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("montaj-carrier-v1.mp4")
        if FileManager.default.fileExists(atPath: destination.path) { return destination }
        if let pending { return try await pending.value }

        let task = Task { try await write(to: destination) }
        pending = task
        defer { pending = nil }
        return try await task.value
    }

    private static func write(to destination: URL) async throws -> URL {
        let side = 64
        let temporary = TemporaryFiles.makeURL(pathExtension: "mp4")
        let writer = try AVAssetWriter(outputURL: temporary, fileType: .mp4)
        let input = AVAssetWriterInput(mediaType: .video, outputSettings: [
            AVVideoCodecKey: AVVideoCodecType.h264,
            AVVideoWidthKey: side,
            AVVideoHeightKey: side,
        ])
        input.expectsMediaDataInRealTime = false
        let adaptor = AVAssetWriterInputPixelBufferAdaptor(assetWriterInput: input, sourcePixelBufferAttributes: [
            kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA,
            kCVPixelBufferWidthKey as String: side,
            kCVPixelBufferHeightKey as String: side,
        ])
        writer.add(input)
        guard writer.startWriting() else { throw writer.error ?? MontajError.compositionFailed }
        writer.startSession(atSourceTime: .zero)

        var buffer: CVPixelBuffer?
        CVPixelBufferCreate(nil, side, side, kCVPixelFormatType_32BGRA, nil, &buffer)
        guard let buffer else { throw MontajError.compositionFailed }
        CVPixelBufferLockBaseAddress(buffer, [])
        if let base = CVPixelBufferGetBaseAddress(buffer) {
            memset(base, 0, CVPixelBufferGetDataSize(buffer))
        }
        CVPixelBufferUnlockBaseAddress(buffer, [])

        for frame in [CMTime.zero, CMTime(value: 1, timescale: 2)] {
            while !input.isReadyForMoreMediaData {
                try await Task.sleep(nanoseconds: 10_000_000)
            }
            adaptor.append(buffer, withPresentationTime: frame)
        }
        input.markAsFinished()
        writer.endSession(atSourceTime: CMTime(value: 1, timescale: 1))
        await writer.finishWriting()
        guard writer.status == .completed else { throw writer.error ?? MontajError.compositionFailed }

        try? FileManager.default.removeItem(at: destination)
        try FileManager.default.moveItem(at: temporary, to: destination)
        return destination
    }
}
