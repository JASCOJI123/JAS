import SwiftUI

struct ClipsPanel: View {
    @ObservedObject var viewModel: EditorViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    ForEach(Array(viewModel.clips.enumerated()), id: \.element.id) { index, clip in
                        ClipCard(
                            number: index + 1,
                            clip: clip,
                            thumbnail: viewModel.thumbnails[clip.id],
                            isSelected: viewModel.selectedClipID == clip.id
                        )
                        .onTapGesture { viewModel.selectedClipID = clip.id }
                    }
                }
                .padding(.vertical, 4)
            }

            if let id = viewModel.selectedClipID,
               let clip = viewModel.binding(forClip: id),
               let index = viewModel.clips.firstIndex(where: { $0.id == id }) {
                ClipEditor(
                    clip: clip,
                    canMoveLeft: index > 0,
                    canMoveRight: index < viewModel.clips.count - 1,
                    onMoveLeft: { viewModel.moveClip(id, by: -1) },
                    onMoveRight: { viewModel.moveClip(id, by: 1) },
                    onDelete: { viewModel.deleteClip(id) }
                )
            } else {
                Text("Tahrirlash uchun klipni tanlang")
                    .foregroundStyle(.secondary)
            }
        }
    }
}

private struct ClipCard: View {
    let number: Int
    let clip: Clip
    let thumbnail: UIImage?
    let isSelected: Bool

    var body: some View {
        ZStack(alignment: .bottomLeading) {
            Group {
                if let thumbnail {
                    Image(uiImage: thumbnail).resizable().scaledToFill()
                } else {
                    Color.gray.opacity(0.3)
                }
            }
            .frame(width: 72, height: 110)
            .clipped()

            VStack(alignment: .leading, spacing: 0) {
                Text("\(number)").fontWeight(.bold)
                Text(Format.seconds(clip.outputDuration))
            }
            .font(.caption2)
            .padding(4)
            .background(.black.opacity(0.55), in: RoundedRectangle(cornerRadius: 4))
            .padding(4)
        }
        .clipShape(RoundedRectangle(cornerRadius: 10))
        .overlay(
            RoundedRectangle(cornerRadius: 10)
                .stroke(isSelected ? Color.accentColor : .clear, lineWidth: 3)
        )
    }
}

private struct ClipEditor: View {
    @Binding var clip: Clip
    let canMoveLeft: Bool
    let canMoveRight: Bool
    let onMoveLeft: () -> Void
    let onMoveRight: () -> Void
    let onDelete: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Qirqish")
                .font(.headline)
            slider(
                title: "Boshi",
                value: Binding(
                    get: { clip.trimStart },
                    set: { clip.trimStart = min($0, clip.trimEnd - Clip.minimumLength) }
                )
            )
            slider(
                title: "Oxiri",
                value: Binding(
                    get: { clip.trimEnd },
                    set: { clip.trimEnd = max($0, clip.trimStart + Clip.minimumLength) }
                )
            )

            Text("Tezlik")
                .font(.headline)
            Picker("Tezlik", selection: $clip.speed) {
                ForEach(Clip.speedOptions, id: \.self) { Text(Format.speed($0)).tag($0) }
            }
            .pickerStyle(.segmented)

            HStack {
                Button(action: onMoveLeft) { Label("Chapga", systemImage: "arrow.left") }
                    .disabled(!canMoveLeft)
                Button(action: onMoveRight) { Label("O'ngga", systemImage: "arrow.right") }
                    .disabled(!canMoveRight)
                Spacer()
                Button(role: .destructive, action: onDelete) { Label("O'chirish", systemImage: "trash") }
            }
            .buttonStyle(.bordered)
            .font(.subheadline)
        }
    }

    private func slider(title: String, value: Binding<Double>) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text("\(title): \(Format.seconds(value.wrappedValue))")
                .font(.subheadline)
                .foregroundStyle(.secondary)
            Slider(value: value, in: 0...clip.duration)
        }
    }
}
