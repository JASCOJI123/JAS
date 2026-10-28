import AVFoundation
import CoreImage

/// Oldindan chizilgan matn qatlami va u ko'rinadigan vaqt oralig'i.
struct RenderedOverlay {
    let image: CIImage
    let timeRange: CMTimeRange
}

/// Barcha kadrlar uchun umumiy sozlamalar: o'lcham, filtr, matnlar.
final class RenderStyle {
    let renderSize: CGSize
    let filter: VideoFilter
    let overlays: [RenderedOverlay]

    init(renderSize: CGSize, filter: VideoFilter, overlays: [RenderedOverlay]) {
        self.renderSize = renderSize
        self.filter = filter
        self.overlays = overlays
    }
}

/// Bitta klip segmenti uchun kompozitor ko'rsatmasi.
final class MontajInstruction: NSObject, AVVideoCompositionInstructionProtocol {
    let timeRange: CMTimeRange
    let enablePostProcessing = false
    let containsTweening = true
    let requiredSourceTrackIDs: [NSValue]?
    let passthroughTrackID: CMPersistentTrackID = kCMPersistentTrackID_Invalid

    let sourceTrackID: CMPersistentTrackID
    let orientation: CGImagePropertyOrientation
    let style: RenderStyle

    init(timeRange: CMTimeRange, sourceTrackID: CMPersistentTrackID, orientation: CGImagePropertyOrientation, style: RenderStyle) {
        self.timeRange = timeRange
        self.sourceTrackID = sourceTrackID
        self.requiredSourceTrackIDs = [NSNumber(value: sourceTrackID)]
        self.orientation = orientation
        self.style = style
        super.init()
    }
}

extension CGAffineTransform {
    /// Video trekining preferredTransform qiymatini rasm yo'nalishiga aylantiradi.
    var imageOrientation: CGImagePropertyOrientation {
        switch (a.rounded(), b.rounded(), c.rounded(), d.rounded()) {
        case (0, 1, -1, 0): return .right
        case (0, -1, 1, 0): return .left
        case (-1, 0, 0, -1): return .down
        default: return .up
        }
    }
}
