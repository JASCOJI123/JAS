import AVKit
import PhotosUI
import SwiftUI

enum EditorPanel: String, CaseIterable, Identifiable {
    case clips, audio, text, filter

    var id: String { rawValue }

    var title: String {
        switch self {
        case .clips: return "Kliplar"
        case .audio: return "Ovoz"
        case .text: return "Matn"
        case .filter: return "Filtr"
        }
    }
}

struct EditorView: View {
    @StateObject private var viewModel = EditorViewModel()
    @State private var panel: EditorPanel = .clips
    @State private var pickerItems: [PhotosPickerItem] = []

    var body: some View {
        NavigationStack {
            Group {
                if viewModel.clips.isEmpty {
                    emptyState
                } else {
                    editor
                }
            }
            .navigationTitle("Montaj")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    videoPicker { Image(systemName: "plus") }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Saqlash") { Task { await viewModel.export() } }
                        .fontWeight(.semibold)
                        .disabled(viewModel.clips.isEmpty || viewModel.exportState != .idle)
                }
            }
        }
        .onChange(of: pickerItems) { items in
            guard !items.isEmpty else { return }
            Task {
                await viewModel.importVideos(items)
                pickerItems = []
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
        .alert("Xatolik", isPresented: errorBinding) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(viewModel.errorMessage ?? "")
        }
    }

    private var editor: some View {
        VStack(spacing: 0) {
            VideoPlayer(player: viewModel.player)
                .aspectRatio(9 / 16, contentMode: .fit)
                .frame(maxWidth: .infinity, maxHeight: 360)
                .background(Color.black)

            Text("Davomiylik: \(Format.clock(viewModel.totalDuration))")
                .font(.caption)
                .foregroundStyle(.secondary)
                .padding(.top, 6)

            Picker("Bo'lim", selection: $panel) {
                ForEach(EditorPanel.allCases) { Text($0.title).tag($0) }
            }
            .pickerStyle(.segmented)
            .padding(.horizontal, 16)
            .padding(.vertical, 10)

            ScrollView {
                Group {
                    switch panel {
                    case .clips: ClipsPanel(viewModel: viewModel)
                    case .audio: AudioPanel(viewModel: viewModel)
                    case .text: TextPanel(viewModel: viewModel)
                    case .filter: FilterPanel(viewModel: viewModel)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 24)
            }
        }
    }

    private var emptyState: some View {
        VStack(spacing: 20) {
            Image(systemName: "film.stack")
                .font(.system(size: 64))
                .foregroundStyle(Color.accentColor)
            Text("Montajni boshlash uchun video tanlang")
                .font(.headline)
                .multilineTextAlignment(.center)
            videoPicker {
                Label("Video qo'shish", systemImage: "plus")
                    .font(.headline)
                    .padding(.horizontal, 24)
                    .padding(.vertical, 14)
                    .background(Color.accentColor, in: Capsule())
                    .foregroundStyle(.white)
            }
        }
        .padding(32)
    }

    private func videoPicker<PickerLabel: View>(@ViewBuilder label: () -> PickerLabel) -> some View {
        PhotosPicker(
            selection: $pickerItems,
            selectionBehavior: .ordered,
            matching: .videos,
            preferredItemEncoding: .current,
            label: label
        )
    }

    private var errorBinding: Binding<Bool> {
        Binding(
            get: { viewModel.errorMessage != nil },
            set: { if !$0 { viewModel.errorMessage = nil } }
        )
    }
}

struct ExportOverlay: View {
    let state: ExportState
    let onDismiss: () -> Void

    var body: some View {
        switch state {
        case .idle:
            EmptyView()
        case .exporting(let progress):
            card {
                ProgressView(value: progress)
                    .progressViewStyle(.linear)
                Text("Saqlanmoqda… \(Int(progress * 100))%")
                    .font(.headline)
                Text("Ilovadan chiqmang")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        case .finished(let url):
            card {
                Image(systemName: "checkmark.circle.fill")
                    .font(.system(size: 48))
                    .foregroundStyle(.green)
                Text("Video galereyaga saqlandi")
                    .font(.headline)
                ShareLink(item: url) {
                    Label("Ulashish", systemImage: "square.and.arrow.up")
                }
                .buttonStyle(.borderedProminent)
                Button("Yopish", action: onDismiss)
            }
        }
    }

    private func card<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        ZStack {
            Color.black.opacity(0.6).ignoresSafeArea()
            VStack(spacing: 16, content: content)
                .padding(24)
                .frame(maxWidth: 300)
                .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 20))
        }
    }
}
