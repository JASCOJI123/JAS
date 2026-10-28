import AVFoundation
import CoreImage
import UIKit

/// Matn qatlamlarini bir marta kadr o'lchamidagi shaffof rasmga chizadi.
@MainActor
enum OverlayRenderer {
    static func render(_ overlay: TextOverlay, canvasSize: CGSize, videoDuration: CMTime) -> RenderedOverlay? {
        let text = overlay.text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return nil }

        let start = CMTime(seconds: max(overlay.start, 0), preferredTimescale: 600)
        let end = min(CMTime(seconds: overlay.end, preferredTimescale: 600), videoDuration)
        guard end > start else { return nil }

        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = false
        let image = UIGraphicsImageRenderer(size: canvasSize, format: format).image { _ in
            draw(text: text, style: overlay, canvasSize: canvasSize)
        }
        guard let cgImage = image.cgImage else { return nil }
        return RenderedOverlay(image: CIImage(cgImage: cgImage), timeRange: CMTimeRange(start: start, end: end))
    }

    private static func draw(text: String, style: TextOverlay, canvasSize: CGSize) {
        let paragraph = NSMutableParagraphStyle()
        paragraph.alignment = .center

        var attributes: [NSAttributedString.Key: Any] = [
            .font: UIFont.systemFont(ofSize: style.fontSize, weight: .heavy),
            .foregroundColor: style.color.uiColor,
            .paragraphStyle: paragraph,
        ]
        if !style.hasBackground {
            let shadow = NSShadow()
            shadow.shadowColor = UIColor.black.withAlphaComponent(0.7)
            shadow.shadowBlurRadius = 10
            shadow.shadowOffset = CGSize(width: 0, height: 3)
            attributes[.shadow] = shadow
        }

        let string = NSAttributedString(string: text, attributes: attributes)
        let options: NSStringDrawingOptions = [.usesLineFragmentOrigin, .usesFontLeading]
        let maxSize = CGSize(width: canvasSize.width * 0.86, height: canvasSize.height)
        let bounds = string.boundingRect(with: maxSize, options: options, context: nil).integral

        let centerY = canvasSize.height * style.position.verticalAnchor
        let textRect = CGRect(
            x: (canvasSize.width - bounds.width) / 2,
            y: min(max(centerY - bounds.height / 2, 0), canvasSize.height - bounds.height),
            width: bounds.width,
            height: bounds.height
        )

        if style.hasBackground {
            let padding = style.fontSize * 0.35
            let box = textRect.insetBy(dx: -padding, dy: -padding * 0.6)
            UIColor.black.withAlphaComponent(0.55).setFill()
            UIBezierPath(roundedRect: box, cornerRadius: padding).fill()
        }
        string.draw(with: textRect, options: options, context: nil)
    }
}
