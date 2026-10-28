import AVFoundation
import CoreImage

/// Tahrirlanayotgan loyihaning joriy holati. Kompozitor har kadrda shu yerdan o'qiydi,
/// shuning uchun xususiyat o'zgarganda kompozitsiyani qayta qurish shart emas.
final class MotionRenderState: @unchecked Sendable {
    private let lock = NSLock()
    private var storedProject: MotionProject

    init(project: MotionProject) {
        self.storedProject = project
    }

    var project: MotionProject {
        get {
            lock.lock()
            defer { lock.unlock() }
            return storedProject
        }
        set {
            lock.lock()
            storedProject = newValue
            lock.unlock()
        }
    }
}

final class MotionInstruction: NSObject, AVVideoCompositionInstructionProtocol {
    let timeRange: CMTimeRange
    let enablePostProcessing = false
    let containsTweening = true
    let requiredSourceTrackIDs: [NSValue]?
    let passthroughTrackID: CMPersistentTrackID = kCMPersistentTrackID_Invalid

    let state: MotionRenderState
    /// Video qatlam → kompozitsiyadagi trek.
    let videoTrackIDs: [MotionLayer.ID: CMPersistentTrackID]

    init(timeRange: CMTimeRange, state: MotionRenderState, carrierTrackID: CMPersistentTrackID, videoTrackIDs: [MotionLayer.ID: CMPersistentTrackID]) {
        self.timeRange = timeRange
        self.state = state
        self.videoTrackIDs = videoTrackIDs
        self.requiredSourceTrackIDs = ([carrierTrackID] + videoTrackIDs.values).map { NSNumber(value: $0) }
        super.init()
    }
}

final class MotionCompositor: NSObject, AVVideoCompositing {
    private let renderQueue = DispatchQueue(label: "montaj.motion.compositor")
    private let ciContext = CIContext(options: [.cacheIntermediates: false])
    private let renderer = MotionFrameRenderer()

    let sourcePixelBufferAttributes: [String: any Sendable]? = [
        kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA,
    ]

    let requiredPixelBufferAttributesForRenderContext: [String: any Sendable] = [
        kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA,
    ]

    func renderContextChanged(_ newRenderContext: AVVideoCompositionRenderContext) {}

    func startRequest(_ request: AVAsynchronousVideoCompositionRequest) {
        renderQueue.async { [ciContext, renderer] in
            guard let instruction = request.videoCompositionInstruction as? MotionInstruction,
                  let output = request.renderContext.newPixelBuffer()
            else {
                request.finish(with: MontajError.compositionFailed)
                return
            }
            var frames: [MotionLayer.ID: CIImage] = [:]
            for (layerID, trackID) in instruction.videoTrackIDs {
                if let buffer = request.sourceFrame(byTrackID: trackID) {
                    frames[layerID] = CIImage(cvPixelBuffer: buffer)
                }
            }
            let image = renderer.render(instruction.state.project, time: request.compositionTime.seconds, videoFrames: frames)
            ciContext.render(image, to: output)
            request.finish(withComposedVideoFrame: output)
        }
    }

    func cancelAllPendingVideoCompositionRequests() {}
}
