import CoreImage
import SwiftUI

struct FilterPanel: View {
    @ObservedObject var viewModel: EditorViewModel

    private let columns = [GridItem(.adaptive(minimum: 76), spacing: 12)]

    var body: some View {
        LazyVGrid(columns: columns, spacing: 14) {
            ForEach(VideoFilter.allCases) { filter in
                FilterCell(
                    filter: filter,
                    source: viewModel.clips.first.flatMap { viewModel.thumbnails[$0.id] },
                    isSelected: viewModel.filter == filter
                )
                .onTapGesture { viewModel.filter = filter }
            }
        }
    }
}

private struct FilterCell: View {
    let filter: VideoFilter
    let source: UIImage?
    let isSelected: Bool
    @State private var preview: UIImage?

    var body: some View {
        VStack(spacing: 6) {
            Group {
                if let preview {
                    Image(uiImage: preview).resizable().scaledToFill()
                } else {
                    Color.gray.opacity(0.3)
                }
            }
            .frame(width: 76, height: 100)
            .clipShape(RoundedRectangle(cornerRadius: 10))
            .overlay(
                RoundedRectangle(cornerRadius: 10)
                    .stroke(isSelected ? Color.accentColor : .clear, lineWidth: 3)
            )
            Text(filter.title)
                .font(.caption)
                .foregroundStyle(isSelected ? Color.accentColor : .primary)
        }
        .task(id: source) {
            preview = source.flatMap { FilterPreview.render($0, filter: filter) }
        }
    }
}

private enum FilterPreview {
    static let context = CIContext()

    static func render(_ image: UIImage, filter: VideoFilter) -> UIImage? {
        guard let cgImage = image.cgImage else { return image }
        let input = CIImage(cgImage: cgImage)
        let output = filter.apply(to: input).cropped(to: input.extent)
        guard let rendered = context.createCGImage(output, from: input.extent) else { return image }
        return UIImage(cgImage: rendered)
    }
}
