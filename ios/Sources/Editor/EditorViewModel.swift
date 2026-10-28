import AVFoundation
import PhotosUI
import SwiftUI

enum ExportState: Equatable {
    case idle
    case exporting(progress: Double)
    case finished(URL)
}

@MainActor
final class EditorViewModel: ObservableObject {
    @Published var clips: [Clip] = [] { didSet { scheduleRebuild() } }
    @Published var overlays: [TextOverlay] = [] { didSet { scheduleRebuild() } }
    @Published var filter: VideoFilter = .none { didSet { scheduleRebuild() } }
    @Published var music: MusicTrack? { didSet { scheduleRebuild() } }
    @Published var originalVolume: Float = 1 { didSet { scheduleRebuild() } }
    @Published var musicVolume: Float = 0.6 { didSet { scheduleRebuild() } }

    @Published var selectedClipID: Clip.ID?
    @Published var errorMessage: String?
    @Published private(set) var thumbnails: [Clip.ID: UIImage] = [:]
    @Published private(set) var totalDuration: Double = 0
    @Published private(set) var isImporting = false
    @Published private(set) var exportState: ExportState = .idle

    let player = AVPlayer()
    private var rebuildTask: Task<Void, Never>?

    var snapshot: EditorSnapshot {
        EditorSnapshot(
            clips: clips,
            overlays: overlays,
            filter: filter,
            music: music,
            originalVolume: originalVolume,
            musicVolume: musicVolume
        )
    }

    // MARK: - Kliplar

    func importVideos(_ items: [PhotosPickerItem]) async {
        isImporting = true
        defer { isImporting = false }

        for item in items {
            do {
                guard let movie = try await item.loadTransferable(type: PickedMovie.self) else { continue }
                let duration = try await AVURLAsset(url: movie.url).load(.duration).seconds
                guard duration.isFinite, duration >= Clip.minimumLength else { continue }
                let clip = Clip(url: movie.url, duration: duration)
                thumbnails[clip.id] = await ThumbnailGenerator.thumbnail(for: movie.url)
                clips.append(clip)
                if selectedClipID == nil { selectedClipID = clip.id }
            } catch {
                errorMessage = "Videoni yuklab bo'lmadi: \(error.localizedDescription)"
            }
        }
    }

    func binding(forClip id: Clip.ID) -> Binding<Clip>? {
        guard let current = clips.first(where: { $0.id == id }) else { return nil }
        return Binding(
            get: { [weak self] in self?.clips.first(where: { $0.id == id }) ?? current },
            set: { [weak self] updated in
                guard let self, let index = self.clips.firstIndex(where: { $0.id == id }) else { return }
                self.clips[index] = updated
            }
        )
    }

    func moveClip(_ id: Clip.ID, by offset: Int) {
        guard let index = clips.firstIndex(where: { $0.id == id }) else { return }
        let target = index + offset
        guard clips.indices.contains(target) else { return }
        clips.swapAt(index, target)
    }

    func deleteClip(_ id: Clip.ID) {
        if selectedClipID == id { selectedClipID = nil }
        clips.removeAll { $0.id == id }
        thumbnails[id] = nil
        selectedClipID = selectedClipID ?? clips.first?.id
    }

    // MARK: - Musiqa

    func importMusic(from url: URL) async {
        do {
            let localURL = try TemporaryFiles.copySecurityScoped(url)
            let asset = AVURLAsset(url: localURL)
            let audioTracks = try await asset.loadTracks(withMediaType: .audio)
            guard !audioTracks.isEmpty else { throw MontajError.noAudioTrack }
            let duration = try await asset.load(.duration).seconds
            music = MusicTrack(url: localURL, name: url.deletingPathExtension().lastPathComponent, duration: duration)
        } catch {
            errorMessage = "Musiqani qo'shib bo'lmadi: \(error.localizedDescription)"
        }
    }

    // MARK: - Matnlar

    func addOverlay() -> TextOverlay.ID {
        let start = min(player.currentTime().seconds.finiteOrZero, max(totalDuration - 0.5, 0))
        let overlay = TextOverlay(text: "Matn", start: start, end: min(start + 3, max(totalDuration, start + 0.5)))
        overlays.append(overlay)
        return overlay.id
    }

    func binding(forOverlay id: TextOverlay.ID) -> Binding<TextOverlay>? {
        guard let current = overlays.first(where: { $0.id == id }) else { return nil }
        return Binding(
            get: { [weak self] in self?.overlays.first(where: { $0.id == id }) ?? current },
            set: { [weak self] updated in
                guard let self, let index = self.overlays.firstIndex(where: { $0.id == id }) else { return }
                self.overlays[index] = updated
            }
        )
    }

    func deleteOverlay(_ id: TextOverlay.ID) {
        overlays.removeAll { $0.id == id }
    }

    // MARK: - Ko'rish

    private func scheduleRebuild() {
        rebuildTask?.cancel()
        rebuildTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 250_000_000)
            guard !Task.isCancelled else { return }
            await self?.rebuildPreview()
        }
    }

    private func rebuildPreview() async {
        guard !clips.isEmpty else {
            player.replaceCurrentItem(with: nil)
            totalDuration = 0
            return
        }
        do {
            let built = try await CompositionBuilder.build(snapshot)
            guard !Task.isCancelled else { return }

            let resumeTime = min(player.currentTime(), built.duration)
            let wasPlaying = player.rate > 0
            let item = AVPlayerItem(asset: built.composition)
            item.videoComposition = built.videoComposition
            item.audioMix = built.audioMix
            item.audioTimePitchAlgorithm = .spectral
            player.replaceCurrentItem(with: item)
            if resumeTime.isValid, resumeTime > .zero {
                await player.seek(to: resumeTime, toleranceBefore: .zero, toleranceAfter: .zero)
            }
            if wasPlaying { player.play() }
            totalDuration = built.duration.seconds
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    // MARK: - Eksport

    func export() async {
        guard !clips.isEmpty else {
            errorMessage = MontajError.noClips.localizedDescription
            return
        }
        player.pause()
        exportState = .exporting(progress: 0)
        do {
            let built = try await CompositionBuilder.build(snapshot)
            let url = try await VideoExporter.export(built) { [weak self] progress in
                self?.exportState = .exporting(progress: progress)
            }
            try await PhotoLibrarySaver.saveVideo(at: url)
            exportState = .finished(url)
        } catch {
            exportState = .idle
            errorMessage = error.localizedDescription
        }
    }

    func dismissExportResult() {
        exportState = .idle
    }
}

private extension Double {
    var finiteOrZero: Double { isFinite ? self : 0 }
}
