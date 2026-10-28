import SwiftUI

/// After Effects'dagi kabi qatlamlar ro'yxati: eng yuqoridagi qatlam birinchi.
struct LayerTimeline: View {
    @ObservedObject var viewModel: MotionEditorViewModel

    var body: some View {
        VStack(spacing: 6) {
            ForEach(viewModel.project.layers.reversed()) { layer in
                LayerRow(
                    layer: layer,
                    duration: viewModel.project.duration,
                    currentTime: viewModel.currentTime,
                    isSelected: viewModel.selectedLayerID == layer.id,
                    onToggleVisibility: { viewModel.updateLayer(layer.id) { $0.isVisible.toggle() } }
                )
                .onTapGesture {
                    viewModel.selectedLayerID = viewModel.selectedLayerID == layer.id ? nil : layer.id
                }
            }
        }
    }
}

private struct LayerRow: View {
    let layer: MotionLayer
    let duration: Double
    let currentTime: Double
    let isSelected: Bool
    let onToggleVisibility: () -> Void

    var body: some View {
        HStack(spacing: 10) {
            Button(action: onToggleVisibility) {
                Image(systemName: layer.isVisible ? "eye" : "eye.slash")
                    .foregroundStyle(layer.isVisible ? .primary : .secondary)
                    .frame(width: 24)
            }
            .buttonStyle(.plain)

            Image(systemName: layer.content.systemImage)
                .frame(width: 20)
                .foregroundStyle(Color.accentColor)

            Text(layer.name)
                .font(.subheadline)
                .lineLimit(1)
                .frame(width: 90, alignment: .leading)

            GeometryReader { geometry in
                let width = geometry.size.width
                let safeDuration = max(duration, 0.1)
                ZStack(alignment: .leading) {
                    Capsule()
                        .fill(Color.gray.opacity(0.15))
                    Capsule()
                        .fill(Color.accentColor.opacity(layer.isVisible ? 0.45 : 0.15))
                        .frame(width: max(CGFloat((min(layer.outPoint, duration) - layer.inPoint) / safeDuration) * width, 4))
                        .offset(x: CGFloat(layer.inPoint / safeDuration) * width)
                    ForEach(Array(Set(layer.allKeyframeTimes)).sorted(), id: \.self) { time in
                        Image(systemName: "diamond.fill")
                            .font(.system(size: 8))
                            .foregroundStyle(.yellow)
                            .offset(x: CGFloat(time / safeDuration) * width - 4)
                    }
                    Rectangle()
                        .fill(Color.red)
                        .frame(width: 1.5)
                        .offset(x: CGFloat(currentTime / safeDuration) * width)
                }
            }
            .frame(height: 18)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 10)
        .background(
            RoundedRectangle(cornerRadius: 10)
                .fill(isSelected ? Color.accentColor.opacity(0.18) : Color.gray.opacity(0.1))
        )
        .contentShape(Rectangle())
    }
}
