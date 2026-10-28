import AVFoundation
import PhotosUI
import SwiftUI

@MainActor
final class MotionEditorViewModel: ObservableObject {
    @Published var project = MotionProject() { didSet { projectDidChange(from: oldValue) } }
    @Published var selectedLayerID: MotionLayer.ID?
    @Published var errorMessage: String?
    @Published private(set) var currentTime: Double = 0
    @Published private(set) var isPlaying = false
    @Published private(set) var isImporting = false
    @Published private(set) var exportState: ExportState = .idle

    let player = AVPlayer()
    private let renderState = MotionRenderState(project: MotionProject())
    private var timeObserver: Any?
    private var rebuildTask: Task<Void, Never>?
    private var isRefreshScheduled = false

    init() {
        player.actionAtItemEnd = .pause
        timeObserver = player.addPeriodicTimeObserver(forInterval: CMTime(value: 1, timescale: 30), queue: .main) { [weak self] time in
            Task { @MainActor in self?.handleTick(time) }
        }
        scheduleRebuild()
    }

    var selectedLayer: MotionLayer? {
        project.layers.first { $0.id == selectedLayerID }
    }

    // MARK: - Ijro

    func togglePlayback() {
        if isPlaying {
            player.pause()
            isPlaying = false
        } else {
            if currentTime >= project.duration - 0.05 { seek(to: 0) }
            player.play()
            isPlaying = true
        }
    }

    func seek(to time: Double) {
        let clamped = min(max(time, 0), project.duration)
        currentTime = clamped
        player.seek(to: CMTime(seconds: clamped, preferredTimescale: 600), toleranceBefore: .zero, toleranceAfter: .zero)
    }

    private func handleTick(_ time: CMTime) {
        guard time.isValid else { return }
        isPlaying = player.rate > 0
        if isPlaying || abs(time.seconds - currentTime) > 0.5 {
            currentTime = min(max(time.seconds, 0), project.duration)
        }
    }

    // MARK: - Qatlamlar

    func layerBinding(_ id: MotionLayer.ID) -> Binding<MotionLayer>? {
        guard let current = project.layers.first(where: { $0.id == id }) else { return nil }
        return Binding(
            get: { [weak self] in self?.project.layers.first(where: { $0.id == id }) ?? current },
            set: { [weak self] updated in self?.updateLayer(id) { $0 = updated } }
        )
    }

    func updateLayer(_ id: MotionLayer.ID, _ change: (inout MotionLayer) -> Void) {
        guard let index = project.layers.firstIndex(where: { $0.id == id }) else { return }
        change(&project.layers[index])
    }

    func addText() {
        append(MotionLayer(
            name: nextName("Matn"),
            content: .text(TextContent()),
            inPoint: 0,
            outPoint: project.duration,
            position: project.canvasCenter,
            scale: 1
        ))
    }

    func addShape(_ kind: ShapeKind) {
        append(MotionLayer(
            name: nextName(kind.title),
            content: .shape(ShapeContent(kind: kind)),
            inPoint: 0,
            outPoint: project.duration,
            position: project.canvasCenter,
            scale: 1
        ))
    }

    func addSolid() {
        let shape = ShapeContent(kind: .rectangle, width: project.canvasSize.width, height: project.canvasSize.height, fill: .orange)
        append(MotionLayer(
            name: nextName("Solid"),
            content: .shape(shape),
            inPoint: 0,
            outPoint: project.duration,
            position: project.canvasCenter,
            scale: 1
        ))
    }

    func addImage(_ item: PhotosPickerItem) async {
        isImporting = true
        defer { isImporting = false }
        do {
            guard let data = try await item.loadTransferable(type: Data.self) else { return }
            let source = try ImageLoader.prepare(data)
            append(MotionLayer(
                name: nextName("Rasm"),
                content: .image(source),
                inPoint: 0,
                outPoint: project.duration,
                position: project.canvasCenter,
                scale: fitScale(for: source.size)
            ))
        } catch {
            errorMessage = "Rasmni qo'shib bo'lmadi: \(error.localizedDescription)"
        }
    }

    func addVideo(_ item: PhotosPickerItem) async {
        isImporting = true
        defer { isImporting = false }
        do {
            guard let movie = try await item.loadTransferable(type: PickedMovie.self) else { return }
            let asset = AVURLAsset(url: movie.url)
            let duration = try await asset.load(.duration).seconds
            guard let track = try await asset.loadTracks(withMediaType: .video).first else {
                throw MontajError.noVideoTrack
            }
            let (naturalSize, transform) = try await track.load(.naturalSize, .preferredTransform)
            let oriented = CGRect(origin: .zero, size: naturalSize).applying(transform)
            let size = Size2(width: abs(oriented.width), height: abs(oriented.height))
            let source = VideoSource(url: movie.url, duration: duration, size: size, orientation: transform.imageOrientation)

            var updated = project
            if updated.layers.isEmpty {
                updated.duration = min(max(duration, 1), 60)
            }
            let start = updated.layers.isEmpty ? 0 : min(currentTime, max(updated.duration - 0.5, 0))
            let layer = MotionLayer(
                name: nextName("Video"),
                content: .video(source),
                inPoint: start,
                outPoint: min(start + duration, updated.duration),
                position: updated.canvasCenter,
                scale: fitScale(for: size)
            )
            updated.layers.append(layer)
            project = updated
            selectedLayerID = layer.id
        } catch {
            errorMessage = "Videoni qo'shib bo'lmadi: \(error.localizedDescription)"
        }
    }

    func duplicateLayer(_ id: MotionLayer.ID) {
        guard let index = project.layers.firstIndex(where: { $0.id == id }) else { return }
        var copy = project.layers[index]
        copy.id = UUID()
        copy.name += " nusxa"
        project.layers.insert(copy, at: index + 1)
        selectedLayerID = copy.id
    }

    func deleteLayer(_ id: MotionLayer.ID) {
        if selectedLayerID == id { selectedLayerID = nil }
        project.layers.removeAll { $0.id == id }
    }

    /// +1 — yuqoriga (oldinga), −1 — pastga (orqaga).
    func moveLayer(_ id: MotionLayer.ID, by offset: Int) {
        guard let index = project.layers.firstIndex(where: { $0.id == id }) else { return }
        let target = index + offset
        guard project.layers.indices.contains(target) else { return }
        project.layers.swapAt(index, target)
    }

    func contentSize(of layer: MotionLayer) -> CGSize {
        switch layer.content {
        case .video(let source): return source.size.cgSize
        case .image(let source): return source.size.cgSize
        case .shape(let shape): return CGSize(width: shape.width, height: shape.height)
        case .text(let text): return TextRenderer.layout(text).size
        }
    }

    private func append(_ layer: MotionLayer) {
        project.layers.append(layer)
        selectedLayerID = layer.id
    }

    private func nextName(_ prefix: String) -> String {
        let count = project.layers.filter { $0.name.hasPrefix(prefix) }.count
        return "\(prefix) \(count + 1)"
    }

    private func fitScale(for size: Size2) -> Double {
        guard size.width > 0, size.height > 0 else { return 1 }
        return min(project.canvasSize.width / size.width, project.canvasSize.height / size.height)
    }

    // MARK: - Ko'rish

    private func projectDidChange(from old: MotionProject) {
        renderState.project = project
        if player.currentItem == nil
            || MotionCompositionBuilder.mediaSignature(of: old) != MotionCompositionBuilder.mediaSignature(of: project) {
            scheduleRebuild()
        } else {
            scheduleRefresh()
        }
    }

    /// Kompozitsiyani qayta qurmasdan joriy kadrni qayta chizdiradi.
    private func scheduleRefresh() {
        guard !isRefreshScheduled else { return }
        isRefreshScheduled = true
        Task { [weak self] in self?.refreshFrame() }
    }

    private func refreshFrame() {
        isRefreshScheduled = false
        guard let item = player.currentItem,
              let copy = item.videoComposition?.mutableCopy() as? AVMutableVideoComposition
        else { return }
        item.videoComposition = copy
        if !isPlaying {
            player.seek(to: CMTime(seconds: currentTime, preferredTimescale: 600), toleranceBefore: .zero, toleranceAfter: .zero)
        }
    }

    private func scheduleRebuild() {
        rebuildTask?.cancel()
        rebuildTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 150_000_000)
            guard !Task.isCancelled else { return }
            await self?.rebuildPreview()
        }
    }

    private func rebuildPreview() async {
        do {
            let built = try await MotionCompositionBuilder.build(project, state: renderState)
            guard !Task.isCancelled else { return }
            let item = AVPlayerItem(asset: built.composition)
            item.videoComposition = built.videoComposition
            item.audioMix = built.audioMix
            player.replaceCurrentItem(with: item)
            currentTime = min(currentTime, project.duration)
            await player.seek(to: CMTime(seconds: currentTime, preferredTimescale: 600), toleranceBefore: .zero, toleranceAfter: .zero)
            if isPlaying { player.play() }
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    // MARK: - Eksport

    func export() async {
        player.pause()
        isPlaying = false
        exportState = .exporting(progress: 0)
        do {
            // Eksport paytidagi tahrirlar natijaga ta'sir qilmasligi uchun alohida holat.
            let state = MotionRenderState(project: project)
            let built = try await MotionCompositionBuilder.build(project, state: state)
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
