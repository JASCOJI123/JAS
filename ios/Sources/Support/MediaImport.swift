import AVFoundation
import CoreTransferable
import UIKit
import UniformTypeIdentifiers

/// Galereyadan tanlangan video: ilovaning vaqtinchalik papkasiga nusxalanadi.
struct PickedMovie: Transferable {
    let url: URL

    static var transferRepresentation: some TransferRepresentation {
        FileRepresentation(contentType: .movie) { movie in
            SentTransferredFile(movie.url)
        } importing: { received in
            let destination = TemporaryFiles.makeURL(pathExtension: received.file.pathExtension)
            try FileManager.default.copyItem(at: received.file, to: destination)
            return PickedMovie(url: destination)
        }
    }
}

enum TemporaryFiles {
    static func makeURL(pathExtension: String) -> URL {
        let fileExtension = pathExtension.isEmpty ? "mov" : pathExtension
        return FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString)
            .appendingPathExtension(fileExtension)
    }

    /// Fayllar ilovasidan tanlangan faylni (security-scoped) vaqtinchalik papkaga nusxalaydi.
    static func copySecurityScoped(_ url: URL) throws -> URL {
        let accessing = url.startAccessingSecurityScopedResource()
        defer {
            if accessing { url.stopAccessingSecurityScopedResource() }
        }
        let destination = makeURL(pathExtension: url.pathExtension)
        try FileManager.default.copyItem(at: url, to: destination)
        return destination
    }
}

enum ThumbnailGenerator {
    static func thumbnail(for url: URL, at seconds: Double = 0) async -> UIImage? {
        let generator = AVAssetImageGenerator(asset: AVURLAsset(url: url))
        generator.appliesPreferredTrackTransform = true
        generator.maximumSize = CGSize(width: 320, height: 320)
        do {
            let result = try await generator.image(at: CMTime(seconds: seconds, preferredTimescale: 600))
            return UIImage(cgImage: result.image)
        } catch {
            return nil
        }
    }
}
