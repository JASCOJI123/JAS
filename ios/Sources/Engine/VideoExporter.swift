import AVFoundation
import Photos

enum VideoExporter {
    /// Kompozitsiyani .mp4 faylga yozadi va jarayon foizini qaytarib turadi.
    @MainActor
    static func export(_ built: BuiltComposition, onProgress: @escaping @MainActor (Double) -> Void) async throws -> URL {
        guard let session = AVAssetExportSession(asset: built.composition, presetName: AVAssetExportPresetHighestQuality) else {
            throw MontajError.exportFailed
        }
        let outputURL = TemporaryFiles.makeURL(pathExtension: "mp4")
        session.outputURL = outputURL
        session.outputFileType = .mp4
        session.videoComposition = built.videoComposition
        session.audioMix = built.audioMix
        session.audioTimePitchAlgorithm = .spectral
        session.shouldOptimizeForNetworkUse = true

        let progressTask = Task { @MainActor in
            while !Task.isCancelled {
                onProgress(Double(session.progress))
                try? await Task.sleep(nanoseconds: 200_000_000)
            }
        }
        defer { progressTask.cancel() }

        await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
            session.exportAsynchronously {
                continuation.resume()
            }
        }

        switch session.status {
        case .completed:
            onProgress(1)
            return outputURL
        case .cancelled:
            throw CancellationError()
        default:
            throw session.error ?? MontajError.exportFailed
        }
    }
}

enum PhotoLibrarySaver {
    static func saveVideo(at url: URL) async throws {
        let status = await PHPhotoLibrary.requestAuthorization(for: .addOnly)
        guard status == .authorized || status == .limited else {
            throw MontajError.photoAccessDenied
        }
        try await PHPhotoLibrary.shared().performChanges {
            _ = PHAssetChangeRequest.creationRequestForAssetFromVideo(atFileURL: url)
        }
    }
}
