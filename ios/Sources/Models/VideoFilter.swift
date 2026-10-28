import CoreImage
import CoreImage.CIFilterBuiltins

enum VideoFilter: String, CaseIterable, Identifiable {
    case none, vivid, warm, cool, fade, instant, chrome, mono, noir

    var id: String { rawValue }

    var title: String {
        switch self {
        case .none: return "Asl"
        case .vivid: return "Yorqin"
        case .warm: return "Iliq"
        case .cool: return "Sovuq"
        case .fade: return "Xira"
        case .instant: return "Retro"
        case .chrome: return "Xrom"
        case .mono: return "Oq-qora"
        case .noir: return "Nuar"
        }
    }

    func apply(to image: CIImage) -> CIImage {
        switch self {
        case .none:
            return image
        case .vivid:
            let filter = CIFilter.colorControls()
            filter.inputImage = image
            filter.saturation = 1.35
            filter.contrast = 1.08
            return filter.outputImage ?? image
        case .warm:
            return colorMatrix(image, red: 1.08, blue: 0.88)
        case .cool:
            return colorMatrix(image, red: 0.9, blue: 1.1)
        case .fade:
            return output(CIFilter.photoEffectFade(), image)
        case .instant:
            return output(CIFilter.photoEffectInstant(), image)
        case .chrome:
            return output(CIFilter.photoEffectChrome(), image)
        case .mono:
            return output(CIFilter.photoEffectMono(), image)
        case .noir:
            return output(CIFilter.photoEffectNoir(), image)
        }
    }

    private func output(_ filter: CIFilter & CIPhotoEffect, _ image: CIImage) -> CIImage {
        filter.inputImage = image
        return filter.outputImage ?? image
    }

    private func colorMatrix(_ image: CIImage, red: CGFloat, blue: CGFloat) -> CIImage {
        let filter = CIFilter.colorMatrix()
        filter.inputImage = image
        filter.rVector = CIVector(x: red, y: 0, z: 0, w: 0)
        filter.gVector = CIVector(x: 0, y: 1, z: 0, w: 0)
        filter.bVector = CIVector(x: 0, y: 0, z: blue, w: 0)
        filter.aVector = CIVector(x: 0, y: 0, z: 0, w: 1)
        filter.biasVector = CIVector(x: 0, y: 0, z: 0, w: 0)
        return filter.outputImage ?? image
    }
}
