import CoreImage
import CoreImage.CIFilterBuiltins

enum EffectRenderer {
    /// Qatlamning o'z koordinatalaridagi rasmiga effekt qo'llaydi. Shake bu yerda emas — u transformga ta'sir qiladi.
    static func apply(_ effect: LayerEffect, to image: CIImage, contentSize: CGSize, time: Double) -> CIImage {
        let amount = effect.amount.value(at: time)
        switch effect.kind {
        case .blur:
            guard amount > 0.1 else { return image }
            return image.applyingGaussianBlur(sigma: amount)
        case .glow:
            guard amount > 0.1 else { return image }
            let halo = image.applyingGaussianBlur(sigma: amount)
            return halo.applyingFilter("CIAdditionCompositing", parameters: [kCIInputBackgroundImageKey: image])
        case .chromatic:
            guard amount > 0.1 else { return image }
            let red = channel(image, red: 1, green: 0, blue: 0).transformed(by: CGAffineTransform(translationX: amount, y: 0))
            let green = channel(image, red: 0, green: 1, blue: 0)
            let blue = channel(image, red: 0, green: 0, blue: 1).transformed(by: CGAffineTransform(translationX: -amount, y: 0))
            return red
                .applyingFilter("CIMaximumCompositing", parameters: [kCIInputBackgroundImageKey: green])
                .applyingFilter("CIMaximumCompositing", parameters: [kCIInputBackgroundImageKey: blue])
        case .shake:
            return image
        case .brightness:
            let filter = CIFilter.colorControls()
            filter.inputImage = image
            filter.brightness = Float(amount)
            return filter.outputImage ?? image
        case .saturation:
            let filter = CIFilter.colorControls()
            filter.inputImage = image
            filter.saturation = Float(amount)
            return filter.outputImage ?? image
        case .hue:
            let filter = CIFilter.hueAdjust()
            filter.inputImage = image
            filter.angle = Float(amount * .pi / 180)
            return filter.outputImage ?? image
        case .vignette:
            let filter = CIFilter.vignetteEffect()
            filter.inputImage = image
            filter.center = CGPoint(x: contentSize.width / 2, y: contentSize.height / 2)
            filter.radius = Float(max(contentSize.width, contentSize.height) * 0.55)
            filter.intensity = Float(amount)
            return filter.outputImage?.cropped(to: image.extent) ?? image
        }
    }

    /// Shake effekti uchun tasodifiyga o'xshash, lekin har safar bir xil siljish.
    static func shakeOffset(for effects: [LayerEffect], time: Double) -> Vec2 {
        var offset = Vec2(x: 0, y: 0)
        for effect in effects where effect.isEnabled && effect.kind == .shake {
            let amplitude = effect.amount.value(at: time)
            offset.x += amplitude * (sin(time * 71.3) * 0.6 + sin(time * 23.7 + 1.3) * 0.4)
            offset.y += amplitude * (sin(time * 59.1 + 2.1) * 0.6 + sin(time * 31.3 + 0.7) * 0.4)
        }
        return offset
    }

    private static func channel(_ image: CIImage, red: CGFloat, green: CGFloat, blue: CGFloat) -> CIImage {
        let filter = CIFilter.colorMatrix()
        filter.inputImage = image
        filter.rVector = CIVector(x: red, y: 0, z: 0, w: 0)
        filter.gVector = CIVector(x: 0, y: green, z: 0, w: 0)
        filter.bVector = CIVector(x: 0, y: 0, z: blue, w: 0)
        filter.aVector = CIVector(x: 0, y: 0, z: 0, w: 1)
        return filter.outputImage ?? image
    }
}

enum MaskRenderer {
    static func apply(_ mask: LayerMask, to image: CIImage, contentSize: CGSize) -> CIImage {
        let bounds = CGRect(origin: .zero, size: contentSize)
        // Maska tepa-chapdan berilgan, Core Image esa past-chapdan hisoblaydi.
        let rect = CGRect(
            x: mask.x * contentSize.width,
            y: (1 - mask.y - mask.height) * contentSize.height,
            width: max(mask.width * contentSize.width, 1),
            height: max(mask.height * contentSize.height, 1)
        )

        var matte: CIImage
        switch mask.shape {
        case .rectangle:
            matte = CIImage(color: .white).cropped(to: rect)
            if mask.feather > 0.5 {
                matte = matte.applyingGaussianBlur(sigma: mask.feather / 2)
            }
        case .ellipse:
            let unit: CGFloat = 1000
            let feather = min(mask.feather / max(min(rect.width, rect.height) / 2, 1), 1) * unit
            let gradient = CIFilter.radialGradient()
            gradient.center = .zero
            gradient.radius0 = Float(max(unit - feather, 0))
            gradient.radius1 = Float(unit)
            gradient.color0 = .white
            gradient.color1 = .clear
            matte = (gradient.outputImage ?? CIImage.empty())
                .cropped(to: CGRect(x: -unit, y: -unit, width: unit * 2, height: unit * 2))
                .transformed(by: CGAffineTransform(scaleX: rect.width / 2 / unit, y: rect.height / 2 / unit))
                .transformed(by: CGAffineTransform(translationX: rect.midX, y: rect.midY))
        }

        if mask.isInverted {
            matte = CIImage(color: .white).cropped(to: bounds)
                .applyingFilter("CISourceOutCompositing", parameters: [kCIInputBackgroundImageKey: matte])
        }

        return image.applyingFilter("CIBlendWithAlphaMask", parameters: [
            kCIInputBackgroundImageKey: CIImage.empty(),
            kCIInputMaskImageKey: matte.cropped(to: bounds),
        ])
    }
}
