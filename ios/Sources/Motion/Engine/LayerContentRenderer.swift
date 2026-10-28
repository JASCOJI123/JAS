import CoreImage
import UIKit

/// Matn qatlamining joylashuvi: har bir harfning o'rni (animatsiya uchun).
struct TextLayout {
    struct Glyph {
        let string: String
        let rect: CGRect
    }

    let size: CGSize
    let glyphs: [Glyph]
    let attributes: [NSAttributedString.Key: Any]
}

enum TextRenderer {
    private static let maxWidth: CGFloat = 960

    static func attributes(for content: TextContent) -> [NSAttributedString.Key: Any] {
        let paragraph = NSMutableParagraphStyle()
        paragraph.alignment = .center
        var attributes: [NSAttributedString.Key: Any] = [
            .font: UIFont.systemFont(ofSize: content.fontSize, weight: content.isBold ? .heavy : .regular),
            .foregroundColor: content.color.uiColor,
            .paragraphStyle: paragraph,
        ]
        if content.hasShadow {
            let shadow = NSShadow()
            shadow.shadowColor = UIColor.black.withAlphaComponent(0.6)
            shadow.shadowBlurRadius = content.fontSize * 0.12
            shadow.shadowOffset = CGSize(width: 0, height: content.fontSize * 0.04)
            attributes[.shadow] = shadow
        }
        return attributes
    }

    static func layout(_ content: TextContent) -> TextLayout {
        let attributes = attributes(for: content)
        let text = content.text.isEmpty ? " " : content.text
        let storage = NSTextStorage(string: text, attributes: attributes)
        let manager = NSLayoutManager()
        let container = NSTextContainer(size: CGSize(width: maxWidth, height: .greatestFiniteMagnitude))
        container.lineFragmentPadding = 0
        manager.addTextContainer(container)
        storage.addLayoutManager(manager)
        manager.ensureLayout(for: container)

        let used = manager.usedRect(for: container)
        let padding = content.fontSize * 0.4
        var glyphs: [TextLayout.Glyph] = []
        let nsText = text as NSString
        nsText.enumerateSubstrings(in: NSRange(location: 0, length: nsText.length), options: .byComposedCharacterSequences) { substring, range, _, _ in
            guard let substring, !substring.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
            let glyphRange = manager.glyphRange(forCharacterRange: range, actualCharacterRange: nil)
            let rect = manager.boundingRect(forGlyphRange: glyphRange, in: container)
            glyphs.append(.init(string: substring, rect: rect.offsetBy(dx: padding - used.minX, dy: padding - used.minY)))
        }
        let size = CGSize(width: ceil(used.width + padding * 2), height: ceil(used.height + padding * 2))
        return TextLayout(size: size, glyphs: glyphs, attributes: attributes)
    }

    /// Animatsiya tugaganmi yoki umuman yo'qmi (unda rasmni keshlash mumkin).
    static func isStatic(_ content: TextContent, localTime: Double) -> Bool {
        switch content.animationStyle {
        case .none: return true
        case .wave: return false
        default: return localTime >= content.animationDuration
        }
    }

    static func render(_ content: TextContent, layout: TextLayout, localTime: Double) -> CIImage? {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = false
        let count = layout.glyphs.count
        let image = UIGraphicsImageRenderer(size: layout.size, format: format).image { context in
            let cg = context.cgContext
            for (index, glyph) in layout.glyphs.enumerated() {
                let state = glyphState(content, index: index, count: count, localTime: localTime)
                guard state.alpha > 0.001 else { continue }
                cg.saveGState()
                cg.setAlpha(state.alpha)
                cg.translateBy(x: glyph.rect.midX, y: glyph.rect.midY + state.offsetY)
                cg.scaleBy(x: state.scale, y: state.scale)
                cg.translateBy(x: -glyph.rect.midX, y: -glyph.rect.midY)
                NSAttributedString(string: glyph.string, attributes: layout.attributes).draw(at: glyph.rect.origin)
                cg.restoreGState()
            }
        }
        return image.cgImage.map { CIImage(cgImage: $0) }
    }

    private struct GlyphState {
        var alpha: CGFloat = 1
        var scale: CGFloat = 1
        var offsetY: CGFloat = 0
    }

    private static func glyphState(_ content: TextContent, index: Int, count: Int, localTime: Double) -> GlyphState {
        let total = max(content.animationDuration, 0.05)
        let glyphDuration = total * 0.35
        let stagger = count > 1 ? (total - glyphDuration) / Double(count - 1) : 0
        let progress = min(max((localTime - Double(index) * stagger) / glyphDuration, 0), 1)

        switch content.animationStyle {
        case .none:
            return GlyphState()
        case .typewriter:
            let appearAt = Double(index) * total / Double(max(count, 1))
            return GlyphState(alpha: localTime >= appearAt ? 1 : 0)
        case .fade:
            return GlyphState(alpha: Easing.easeOut.apply(progress))
        case .slideUp:
            let eased = Easing.easeOut.apply(progress)
            return GlyphState(alpha: eased, offsetY: (1 - eased) * content.fontSize * 0.8)
        case .pop:
            return GlyphState(alpha: min(progress * 3, 1), scale: max(Easing.back.apply(progress), 0))
        case .wave:
            return GlyphState(offsetY: sin(localTime * 6 - Double(index) * 0.5) * content.fontSize * 0.12)
        }
    }
}

enum ShapeRenderer {
    static func render(_ shape: ShapeContent) -> CIImage? {
        let inset = CGFloat(shape.strokeWidth / 2)
        let size = CGSize(width: max(shape.width, 1), height: max(shape.height, 1))
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = false
        let image = UIGraphicsImageRenderer(size: size, format: format).image { _ in
            let rect = CGRect(origin: .zero, size: size).insetBy(dx: inset, dy: inset)
            let path = self.path(for: shape.kind, in: rect)
            shape.fill.uiColor.setFill()
            path.fill()
            if shape.strokeWidth > 0 {
                path.lineWidth = CGFloat(shape.strokeWidth)
                shape.strokeColor.uiColor.setStroke()
                path.stroke()
            }
        }
        return image.cgImage.map { CIImage(cgImage: $0) }
    }

    private static func path(for kind: ShapeKind, in rect: CGRect) -> UIBezierPath {
        switch kind {
        case .rectangle:
            return UIBezierPath(rect: rect)
        case .roundedRectangle:
            return UIBezierPath(roundedRect: rect, cornerRadius: min(rect.width, rect.height) * 0.2)
        case .ellipse:
            return UIBezierPath(ovalIn: rect)
        case .triangle:
            let path = UIBezierPath()
            path.move(to: CGPoint(x: rect.midX, y: rect.minY))
            path.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY))
            path.addLine(to: CGPoint(x: rect.minX, y: rect.maxY))
            path.close()
            return path
        case .star:
            let path = UIBezierPath()
            let center = CGPoint(x: rect.midX, y: rect.midY)
            let outer = min(rect.width, rect.height) / 2
            let inner = outer * 0.45
            for point in 0..<10 {
                let radius = point.isMultiple(of: 2) ? outer : inner
                let angle = CGFloat(point) * .pi / 5 - .pi / 2
                let vertex = CGPoint(x: center.x + cos(angle) * radius, y: center.y + sin(angle) * radius)
                if point == 0 { path.move(to: vertex) } else { path.addLine(to: vertex) }
            }
            path.close()
            return path
        }
    }
}

enum ImageLoader {
    /// Katta rasmlarni ishlash tezligi uchun kichraytirib, PNG qilib saqlaydi.
    static func prepare(_ data: Data, maxDimension: CGFloat = 2048) throws -> ImageSource {
        guard let image = UIImage(data: data) else { throw MontajError.compositionFailed }
        let scale = min(1, maxDimension / max(image.size.width, image.size.height))
        let size = CGSize(width: (image.size.width * scale).rounded(), height: (image.size.height * scale).rounded())
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = false
        let resized = UIGraphicsImageRenderer(size: size, format: format).image { _ in
            image.draw(in: CGRect(origin: .zero, size: size))
        }
        guard let png = resized.pngData() else { throw MontajError.compositionFailed }
        let url = TemporaryFiles.makeURL(pathExtension: "png")
        try png.write(to: url)
        return ImageSource(url: url, size: Size2(size))
    }
}
