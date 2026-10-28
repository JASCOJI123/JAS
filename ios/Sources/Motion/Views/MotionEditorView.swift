import AVFoundation
import PhotosUI
import SwiftUI

struct MotionEditorView: View {
    @StateObject private var viewModel = MotionEditorViewModel()
    @State private var isShowingSettings = false
    @State private var imageItem: PhotosPickerItem?
    @State private var videoItem: PhotosPickerItem?
    @State private var isPickingImage = false
    @State private var isPickingVideo = false

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                MotionPreview(viewModel: viewModel)
                    .frame(height: 330)
                    .frame(maxWidth: .infinity)
                    .background(Color(white: 0.08))
                TransportBar(viewModel: viewModel)
                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        LayerTimeline(viewModel: viewModel)
                        if let id = viewModel.selectedLayerID, let layer = viewModel.layerBinding(id) {
                            LayerInspector(viewModel: viewModel, layer: layer)
                        } else if viewModel.project.layers.isEmpty {
                            emptyHint
                        }
                    }
                    .padding(16)
                }
            }
            .navigationTitle("Motion")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button { isShowingSettings = true } label: { Image(systemName: "gearshape") }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    HStack(spacing: 14) {
                        addMenu
                        Button("Saqlash") { Task { await viewModel.export() } }
                            .fontWeight(.semibold)
                            .disabled(viewModel.exportState != .idle)
                    }
                }
            }
            .sheet(isPresented: $isShowingSettings) {
                ProjectSettingsSheet(project: $viewModel.project)
            }
            .photosPicker(isPresented: $isPickingImage, selection: $imageItem, matching: .images)
            .photosPicker(isPresented: $isPickingVideo, selection: $videoItem, matching: .videos, preferredItemEncoding: .current)
            .onChange(of: imageItem) { item in
                guard let item else { return }
                Task {
                    await viewModel.addImage(item)
                    imageItem = nil
                }
            }
            .onChange(of: videoItem) { item in
                guard let item else { return }
                Task {
                    await viewModel.addVideo(item)
                    videoItem = nil
                }
            }
        }
        .overlay { ExportOverlay(state: viewModel.exportState, onDismiss: { viewModel.dismissExportResult() }) }
        .overlay {
            if viewModel.isImporting {
                ProgressView("Yuklanmoqda…")
                    .padding(24)
                    .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 16))
            }
        }
        .alert("Xatolik", isPresented: Binding(
            get: { viewModel.errorMessage != nil },
            set: { if !$0 { viewModel.errorMessage = nil } }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(viewModel.errorMessage ?? "")
        }
    }

    private var addMenu: some View {
        Menu {
            Button { viewModel.addText() } label: { Label("Matn", systemImage: "textformat") }
            Button { isPickingVideo = true } label: { Label("Video", systemImage: "film") }
            Button { isPickingImage = true } label: { Label("Rasm", systemImage: "photo") }
            Button { viewModel.addSolid() } label: { Label("Solid (rangli fon)", systemImage: "square.fill") }
            Menu("Shakl") {
                ForEach(ShapeKind.allCases) { kind in
                    Button { viewModel.addShape(kind) } label: { Label(kind.title, systemImage: kind.systemImage) }
                }
            }
        } label: {
            Image(systemName: "plus.circle.fill").font(.title3)
        }
    }

    private var emptyHint: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Qatlam qo'shing").font(.headline)
            Text("Yuqoridagi ⊕ tugmasi orqali matn, video, rasm yoki shakl qo'shing. Xususiyat yonidagi ⏱ tugmasi keyframe qo'yadi: vaqtni surib, qiymatni o'zgartiring — animatsiya tayyor.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
    }
}

// MARK: - Ko'rish oynasi

private struct MotionPreview: View {
    @ObservedObject var viewModel: MotionEditorViewModel
    @State private var dragStart: Vec2?
    @State private var scaleStart: Double?
    @State private var rotationStart: Double?

    var body: some View {
        GeometryReader { geometry in
            let pointsToPixels = viewModel.project.canvasSize.width / max(Double(geometry.size.width), 1)
            ZStack {
                PlayerLayerView(player: viewModel.player)
                if let layer = viewModel.selectedLayer, layer.isActive(at: viewModel.currentTime) {
                    selectionOutline(for: layer, pointsToPixels: pointsToPixels)
                }
            }
            .contentShape(Rectangle())
            .gesture(
                dragGesture(pointsToPixels: pointsToPixels)
                    .simultaneously(with: magnificationGesture)
                    .simultaneously(with: rotationGesture)
            )
        }
        .aspectRatio(CGFloat(viewModel.project.canvasSize.width / viewModel.project.canvasSize.height), contentMode: .fit)
        .clipped()
    }

    private func selectionOutline(for layer: MotionLayer, pointsToPixels: Double) -> some View {
        let time = viewModel.currentTime
        let size = viewModel.contentSize(of: layer)
        let scale = layer.scale.value(at: time)
        let position = layer.position.value(at: time)
        return Rectangle()
            .stroke(Color.accentColor, style: StrokeStyle(lineWidth: 1.5, dash: [6, 4]))
            .frame(
                width: CGFloat(Double(size.width) * scale / pointsToPixels),
                height: CGFloat(Double(size.height) * scale / pointsToPixels)
            )
            .rotationEffect(.degrees(layer.rotation.value(at: time)))
            .position(x: CGFloat(position.x / pointsToPixels), y: CGFloat(position.y / pointsToPixels))
            .allowsHitTesting(false)
    }

    private func dragGesture(pointsToPixels: Double) -> some Gesture {
        DragGesture(minimumDistance: 2)
            .onChanged { gesture in
                guard let layer = viewModel.selectedLayer else { return }
                let time = viewModel.currentTime
                let start = dragStart ?? layer.position.value(at: time)
                dragStart = start
                let position = Vec2(
                    x: start.x + Double(gesture.translation.width) * pointsToPixels,
                    y: start.y + Double(gesture.translation.height) * pointsToPixels
                )
                viewModel.updateLayer(layer.id) { $0.position.set(position, at: time) }
            }
            .onEnded { _ in dragStart = nil }
    }

    private var magnificationGesture: some Gesture {
        MagnificationGesture()
            .onChanged { magnitude in
                guard let layer = viewModel.selectedLayer else { return }
                let time = viewModel.currentTime
                let start = scaleStart ?? layer.scale.value(at: time)
                scaleStart = start
                viewModel.updateLayer(layer.id) { $0.scale.set(max(start * Double(magnitude), 0.02), at: time) }
            }
            .onEnded { _ in scaleStart = nil }
    }

    private var rotationGesture: some Gesture {
        RotationGesture()
            .onChanged { angle in
                guard let layer = viewModel.selectedLayer else { return }
                let time = viewModel.currentTime
                let start = rotationStart ?? layer.rotation.value(at: time)
                rotationStart = start
                viewModel.updateLayer(layer.id) { $0.rotation.set(start + angle.degrees, at: time) }
            }
            .onEnded { _ in rotationStart = nil }
    }
}

private struct PlayerLayerView: UIViewRepresentable {
    let player: AVPlayer

    func makeUIView(context: Context) -> PlayerUIView {
        let view = PlayerUIView()
        view.playerLayer.player = player
        view.playerLayer.videoGravity = .resizeAspect
        view.backgroundColor = .black
        return view
    }

    func updateUIView(_ uiView: PlayerUIView, context: Context) {}

    final class PlayerUIView: UIView {
        override class var layerClass: AnyClass { AVPlayerLayer.self }
        var playerLayer: AVPlayerLayer { layer as! AVPlayerLayer }
    }
}

// MARK: - Ijro paneli

private struct TransportBar: View {
    @ObservedObject var viewModel: MotionEditorViewModel

    var body: some View {
        HStack(spacing: 12) {
            Button { viewModel.togglePlayback() } label: {
                Image(systemName: viewModel.isPlaying ? "pause.fill" : "play.fill")
                    .font(.title3)
                    .frame(width: 32)
            }
            Slider(
                value: Binding(get: { viewModel.currentTime }, set: { viewModel.seek(to: $0) }),
                in: 0...max(viewModel.project.duration, 0.1)
            )
            Text("\(MotionFormat.time(viewModel.currentTime)) / \(MotionFormat.time(viewModel.project.duration))")
                .font(.caption.monospacedDigit())
                .foregroundStyle(.secondary)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
    }
}

// MARK: - Loyiha sozlamalari

private struct ProjectSettingsSheet: View {
    @Binding var project: MotionProject
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section("Davomiylik") {
                    Text(MotionFormat.time(project.duration))
                    Slider(value: $project.duration, in: 1...60, step: 0.5)
                }
                Section("Fon") {
                    ColorPicker("Fon rangi", selection: Binding(
                        get: { project.background.color },
                        set: { project.background = RGBAColor($0) }
                    ), supportsOpacity: false)
                }
                Section {
                    Text("Kadr: 1080×1920 (9:16), 30 kadr/soniya")
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Loyiha")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Tayyor") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium])
    }
}

enum MotionFormat {
    /// 3.25 -> "0:03.2"
    static func time(_ seconds: Double) -> String {
        let safe = max(seconds, 0)
        let minutes = Int(safe) / 60
        let rest = safe - Double(minutes * 60)
        return String(format: "%d:%04.1f", minutes, rest)
    }
}
