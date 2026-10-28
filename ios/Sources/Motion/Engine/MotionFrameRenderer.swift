import CoreImage
import CoreImage.CIFilterBuiltins

/// Bitta kadrni chizadi: qatlamlarni pastdan tepaga, maska, effekt, transform va blend mode bilan.
/// Faqat kompozitorning navbatida ishlatiladi, shuning uchun keshlar qulfsiz.
final class MotionFrameRenderer {
    private var imageCache: [URL: CIImage] = [:]
    private var shapeCache: [ShapeContent: CIImage] = [:]
    private var textImageCache: [TextContent: CIImage] = [:]
    private var textLayoutCache: [TextContent: TextLayout] = [:]

    func render(_ project: MotionProject, time: Double, videoFrames: [MotionLayer.ID: CIImage]) -> CIImage {
        let canvas = CGRect(origin: .zero, size: project.canvasSize.cgSize)
        var result = CIImage(color: project.background.ciColor).cropped(to: canvas)

        for layer in project.layers where layer.isActive(at: time) {
            guard var image = content(for: layer, localTime: time - layer.inPoint, videoFrames: videoFrames) else { continue }
            image = image.transformed(by: CGAffineTransform(translationX: -image.extent.minX, y: -image.extent.minY))
            let contentSize = image.extent.size

            if let mask = layer.mask {
                image = MaskRenderer.apply(mask, to: image, contentSize: contentSize)
            }
            for effect in layer.effects where effect.isEnabled {
                image = EffectRenderer.apply(effect, to: image, contentSize: contentSize, time: time)
            }

            image = image.transformed(by: transform(for: layer, contentSize: contentSize, canvasHeight: canvas.height, time: time))

            let opacity = min(max(layer.opacity.value(at: time), 0), 1)
            guard opacity > 0.001 else { continue }
            if opacity < 0.999 {
                let fade = CIFilter.colorMatrix()
                fade.inputImage = image
                fade.aVector = CIVector(x: 0, y: 0, z: 0, w: opacity)
                image = fade.outputImage ?? image
            }

            if let filterName = layer.blendMode.ciFilterName {
                result = image.applyingFilter(filterName, parameters: [kCIInputBackgroundImageKey: result])
            } else {
                result = image.composited(over: result)
            }
            result = result.cropped(to: canvas)
        }
        trimCachesIfNeeded()
        return result
    }

    private func transform(for layer: MotionLayer, contentSize: CGSize, canvasHeight: CGFloat, time: Double) -> CGAffineTransform {
        let position = layer.position.value(at: time)
        let shake = EffectRenderer.shakeOffset(for: layer.effects, time: time)
        let scale = layer.scale.value(at: time)
        let radians = layer.rotation.value(at: time) * .pi / 180
        // Pozitsiya tepa-chapdan, Core Image esa past-chapdan: Y o'qini aylantiramiz.
        return CGAffineTransform(translationX: -contentSize.width / 2, y: -contentSize.height / 2)
            .concatenating(CGAffineTransform(scaleX: scale, y: scale))
            .concatenating(CGAffineTransform(rotationAngle: -radians))
            .concatenating(CGAffineTransform(translationX: position.x + shake.x, y: canvasHeight - (position.y + shake.y)))
    }

    private func content(for layer: MotionLayer, localTime: Double, videoFrames: [MotionLayer.ID: CIImage]) -> CIImage? {
        switch layer.content {
        case .video(let source):
            return videoFrames[layer.id]?.oriented(source.orientation)
        case .image(let source):
            if let cached = imageCache[source.url] { return cached }
            let image = CIImage(contentsOf: source.url)
            imageCache[source.url] = image
            return image
        case .shape(let shape):
            if let cached = shapeCache[shape] { return cached }
            let image = ShapeRenderer.render(shape)
            shapeCache[shape] = image
            return image
        case .text(let text):
            let layout = textLayout(for: text)
            guard TextRenderer.isStatic(text, localTime: localTime) else {
                return TextRenderer.render(text, layout: layout, localTime: localTime)
            }
            if let cached = textImageCache[text] { return cached }
            let image = TextRenderer.render(text, layout: layout, localTime: .greatestFiniteMagnitude)
            textImageCache[text] = image
            return image
        }
    }

    private func textLayout(for text: TextContent) -> TextLayout {
        if let cached = textLayoutCache[text] { return cached }
        let layout = TextRenderer.layout(text)
        textLayoutCache[text] = layout
        return layout
    }

    /// Tahrirlash paytida eski variantlar to'planib qolmasligi uchun.
    private func trimCachesIfNeeded() {
        if shapeCache.count > 40 { shapeCache.removeAll() }
        if textImageCache.count > 40 { textImageCache.removeAll() }
        if textLayoutCache.count > 40 { textLayoutCache.removeAll() }
        if imageCache.count > 20 { imageCache.removeAll() }
    }
}
