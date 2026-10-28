import AVFoundation
import CoreImage

/// Har bir kadrni chizadi: 9:16 ga moslash (xira fon bilan), filtr va matnlar.
/// Ko'rish (preview) va eksport uchun bir xil ishlatiladi.
final class MontajCompositor: NSObject, AVVideoCompositing {
    private let renderQueue = DispatchQueue(label: "montaj.compositor")
    private let ciContext = CIContext(options: [.cacheIntermediates: false])

    let sourcePixelBufferAttributes: [String: any Sendable]? = [
        kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA,
    ]

    let requiredPixelBufferAttributesForRenderContext: [String: any Sendable] = [
        kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA,
    ]

    func renderContextChanged(_ newRenderContext: AVVideoCompositionRenderContext) {}

    func startRequest(_ request: AVAsynchronousVideoCompositionRequest) {
        renderQueue.async { [ciContext] in
            guard let instruction = request.videoCompositionInstruction as? MontajInstruction,
                  let output = request.renderContext.newPixelBuffer()
            else {
                request.finish(with: MontajError.compositionFailed)
                return
            }
            let source = request.sourceFrame(byTrackID: instruction.sourceTrackID).map { CIImage(cvPixelBuffer: $0) }
            let frame = FrameRenderer.compose(
                source: source,
                orientation: instruction.orientation,
                style: instruction.style,
                time: request.compositionTime
            )
            ciContext.render(frame, to: output)
            request.finish(withComposedVideoFrame: output)
        }
    }

    func cancelAllPendingVideoCompositionRequests() {}
}

enum FrameRenderer {
    static func compose(source: CIImage?, orientation: CGImagePropertyOrientation, style: RenderStyle, time: CMTime) -> CIImage {
        let canvas = CGRect(origin: .zero, size: style.renderSize)
        var frame = CIImage(color: .black).cropped(to: canvas)

        if let source {
            let oriented = source.oriented(orientation)
            let normalized = oriented.transformed(by: CGAffineTransform(translationX: -oriented.extent.minX, y: -oriented.extent.minY))
            let fitted = normalized.placed(in: canvas, fill: false)
            if fitted.extent.width < canvas.width - 1 || fitted.extent.height < canvas.height - 1 {
                frame = blurredBackground(normalized, canvas: canvas).composited(over: frame)
            }
            frame = fitted.composited(over: frame)
        }

        frame = style.filter.apply(to: frame).cropped(to: canvas)

        for overlay in style.overlays where overlay.timeRange.containsTime(time) {
            frame = overlay.image.composited(over: frame)
        }
        return frame.cropped(to: canvas)
    }

    /// Gorizontal videolar uchun bo'sh joyni to'ldiruvchi xira fon.
    /// Tezlik uchun kichraytirilgan nusxada xiralashtiriladi.
    private static func blurredBackground(_ image: CIImage, canvas: CGRect) -> CIImage {
        let downscale: CGFloat = 0.25
        let small = CGRect(x: 0, y: 0, width: canvas.width * downscale, height: canvas.height * downscale)
        let blurred = image.placed(in: small, fill: true)
            .clampedToExtent()
            .applyingGaussianBlur(sigma: 12)
            .cropped(to: small)
            .transformed(by: CGAffineTransform(scaleX: 1 / downscale, y: 1 / downscale))
        return blurred.cropped(to: canvas)
    }
}

extension CIImage {
    /// Rasmni to'rtburchak markaziga joylaydi: fill = to'ldirib kesish, aks holda to'liq sig'dirish.
    func placed(in rect: CGRect, fill: Bool) -> CIImage {
        guard extent.width > 0, extent.height > 0 else { return self }
        let scaleX = rect.width / extent.width
        let scaleY = rect.height / extent.height
        let scale = fill ? max(scaleX, scaleY) : min(scaleX, scaleY)
        let scaled = transformed(by: CGAffineTransform(scaleX: scale, y: scale))
        let offsetX = rect.midX - scaled.extent.midX
        let offsetY = rect.midY - scaled.extent.midY
        return scaled.transformed(by: CGAffineTransform(translationX: offsetX, y: offsetY))
    }
}
