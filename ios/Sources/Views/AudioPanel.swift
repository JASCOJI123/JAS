import SwiftUI
import UniformTypeIdentifiers

struct AudioPanel: View {
    @ObservedObject var viewModel: EditorViewModel
    @State private var isPickingMusic = false

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            VStack(alignment: .leading, spacing: 6) {
                Text("Videoning asl ovozi").font(.headline)
                volumeRow(value: $viewModel.originalVolume)
            }

            Divider()

            VStack(alignment: .leading, spacing: 10) {
                Text("Fon musiqasi").font(.headline)
                if let music = viewModel.music {
                    HStack {
                        Image(systemName: "music.note")
                        VStack(alignment: .leading) {
                            Text(music.name).lineLimit(1)
                            Text(Format.clock(music.duration))
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                        Button(role: .destructive) { viewModel.music = nil } label: {
                            Image(systemName: "trash")
                        }
                    }
                    volumeRow(value: $viewModel.musicVolume)
                    Text("Musiqa video oxirida sekin pasayadi, qisqa bo'lsa takrorlanadi.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Button {
                    isPickingMusic = true
                } label: {
                    Label(viewModel.music == nil ? "Musiqa tanlash" : "Boshqa musiqa", systemImage: "plus")
                }
                .buttonStyle(.bordered)
                Text("Musiqa \"Fayllar\" ilovasidan tanlanadi (mp3, m4a, wav).")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .fileImporter(isPresented: $isPickingMusic, allowedContentTypes: [.audio], allowsMultipleSelection: false) { result in
            switch result {
            case .success(let urls):
                guard let url = urls.first else { return }
                Task { await viewModel.importMusic(from: url) }
            case .failure(let error):
                viewModel.errorMessage = error.localizedDescription
            }
        }
    }

    private func volumeRow(value: Binding<Float>) -> some View {
        HStack(spacing: 12) {
            Button {
                value.wrappedValue = value.wrappedValue > 0 ? 0 : 1
            } label: {
                Image(systemName: value.wrappedValue > 0 ? "speaker.wave.2.fill" : "speaker.slash.fill")
                    .frame(width: 28)
            }
            Slider(value: value, in: 0...1)
            Text("\(Int(value.wrappedValue * 100))%")
                .font(.caption.monospacedDigit())
                .frame(width: 40, alignment: .trailing)
        }
    }
}
