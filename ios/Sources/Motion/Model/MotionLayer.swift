import CoreGraphics
import CoreImage
import SwiftUI
import UIKit

struct RGBAColor: Hashable {
    var red: Double
    var green: Double
    var blue: Double
    var alpha: Double

    static let white = RGBAColor(red: 1, green: 1, blue: 1, alpha: 1)
    static let black = RGBAColor(red: 0, green: 0, blue: 0, alpha: 1)
    static let orange = RGBAColor(red: 1, green: 0.37, blue: 0.23, alpha: 1)

    init(red: Double, green: Double, blue: Double, alpha: Double) {
        self.red = red
        self.green = green
        self.blue = blue
        self.alpha = alpha
    }

    init(_ color: Color) {
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        UIColor(color).getRed(&r, green: &g, blue: &b, alpha: &a)
        self.init(red: Double(r), green: Double(g), blue: Double(b), alpha: Double(a))
    }

    var uiColor: UIColor { UIColor(red: red, green: green, blue: blue, alpha: alpha) }
    var color: Color { Color(uiColor) }
    var ciColor: CIColor { CIColor(red: red, green: green, blue: blue, alpha: alpha) }
}

struct VideoSource: Hashable {
    let url: URL
    let duration: Double
    /// Burilish hisobga olingan o'lcham.
    let size: Size2
    let orientation: CGImagePropertyOrientation
    var volume: Float = 1
}

struct ImageSource: Hashable {
    let url: URL
    let size: Size2
}

enum TextAnimation: String, CaseIterable, Identifiable, Hashable {
    case none, typewriter, fade, slideUp, pop, wave

    var id: String { rawValue }

    var title: String {
        switch self {
        case .none: return "Yo'q"
        case .typewriter: return "Yozuv mashinkasi"
        case .fade: return "Harfma-harf paydo bo'lish"
        case .slideUp: return "Pastdan chiqish"
        case .pop: return "Sakrab chiqish"
        case .wave: return "To'lqin"
        }
    }
}

struct TextContent: Hashable {
    var text: String = "Matn"
    var fontSize: Double = 110
    var isBold: Bool = true
    var color: RGBAColor = .white
    var hasShadow: Bool = true
    var animationStyle: TextAnimation = .pop
    /// Barcha harflar chiqib bo'lishi uchun ketadigan vaqt (soniya).
    var animationDuration: Double = 1.2
}

enum ShapeKind: String, CaseIterable, Identifiable, Hashable {
    case rectangle, roundedRectangle, ellipse, triangle, star

    var id: String { rawValue }

    var title: String {
        switch self {
        case .rectangle: return "To'rtburchak"
        case .roundedRectangle: return "Yumaloq burchak"
        case .ellipse: return "Ellips"
        case .triangle: return "Uchburchak"
        case .star: return "Yulduz"
        }
    }

    var systemImage: String {
        switch self {
        case .rectangle: return "rectangle.fill"
        case .roundedRectangle: return "app.fill"
        case .ellipse: return "circle.fill"
        case .triangle: return "triangle.fill"
        case .star: return "star.fill"
        }
    }
}

struct ShapeContent: Hashable {
    var kind: ShapeKind
    var width: Double = 500
    var height: Double = 500
    var fill: RGBAColor = .orange
    var strokeColor: RGBAColor = .white
    var strokeWidth: Double = 0
}

enum LayerContent: Hashable {
    case video(VideoSource)
    case image(ImageSource)
    case text(TextContent)
    case shape(ShapeContent)

    var systemImage: String {
        switch self {
        case .video: return "film"
        case .image: return "photo"
        case .text: return "textformat"
        case .shape(let shape): return shape.kind.systemImage
        }
    }
}

enum BlendMode: String, CaseIterable, Identifiable, Hashable {
    case normal, add, screen, multiply, overlay, softLight, lighten, darken, difference

    var id: String { rawValue }

    var title: String {
        switch self {
        case .normal: return "Normal"
        case .add: return "Add"
        case .screen: return "Screen"
        case .multiply: return "Multiply"
        case .overlay: return "Overlay"
        case .softLight: return "Soft Light"
        case .lighten: return "Lighten"
        case .darken: return "Darken"
        case .difference: return "Difference"
        }
    }

    /// Core Image filtri nomi (`normal` oddiy ustma-ust qo'yish).
    var ciFilterName: String? {
        switch self {
        case .normal: return nil
        case .add: return "CIAdditionCompositing"
        case .screen: return "CIScreenBlendMode"
        case .multiply: return "CIMultiplyBlendMode"
        case .overlay: return "CIOverlayBlendMode"
        case .softLight: return "CISoftLightBlendMode"
        case .lighten: return "CILightenBlendMode"
        case .darken: return "CIDarkenBlendMode"
        case .difference: return "CIDifferenceBlendMode"
        }
    }
}

enum EffectKind: String, CaseIterable, Identifiable, Hashable {
    case blur, glow, chromatic, shake, brightness, saturation, hue, vignette

    var id: String { rawValue }

    var title: String {
        switch self {
        case .blur: return "Blur"
        case .glow: return "Glow"
        case .chromatic: return "Chromatic aberration"
        case .shake: return "Shake"
        case .brightness: return "Yorqinlik"
        case .saturation: return "To'yinganlik"
        case .hue: return "Rang tusi (Hue)"
        case .vignette: return "Vignette"
        }
    }

    var range: ClosedRange<Double> {
        switch self {
        case .blur: return 0...60
        case .glow: return 0...60
        case .chromatic: return 0...40
        case .shake: return 0...80
        case .brightness: return -0.5...0.5
        case .saturation: return 0...2
        case .hue: return -180...180
        case .vignette: return 0...3
        }
    }

    var defaultAmount: Double {
        switch self {
        case .blur: return 12
        case .glow: return 20
        case .chromatic: return 10
        case .shake: return 20
        case .brightness: return 0.15
        case .saturation: return 1.5
        case .hue: return 60
        case .vignette: return 1.2
        }
    }
}

struct LayerEffect: Identifiable, Hashable {
    let id: UUID
    var kind: EffectKind
    var amount: Animatable<Double>
    var isEnabled: Bool = true

    init(kind: EffectKind) {
        self.id = UUID()
        self.kind = kind
        self.amount = Animatable(kind.defaultAmount)
    }
}

enum MaskShape: String, CaseIterable, Identifiable, Hashable {
    case rectangle, ellipse

    var id: String { rawValue }
    var title: String { self == .rectangle ? "To'rtburchak" : "Ellips" }
}

/// Qatlam ichidagi maska. Koordinatalar 0...1 (qatlam o'lchamiga nisbatan, tepa-chapdan).
struct LayerMask: Hashable {
    var shape: MaskShape = .ellipse
    var x: Double = 0.1
    var y: Double = 0.1
    var width: Double = 0.8
    var height: Double = 0.8
    var feather: Double = 20
    var isInverted: Bool = false
}

struct MotionLayer: Identifiable, Hashable {
    /// Nusxa olishda yangilanadi.
    var id: UUID
    var name: String
    var content: LayerContent
    var inPoint: Double
    var outPoint: Double
    var isVisible: Bool = true

    /// Kadrdagi markaz nuqtasi (piksel, tepa-chapdan).
    var position: Animatable<Vec2>
    /// 1 = 100%.
    var scale: Animatable<Double>
    /// Gradus, soat yo'nalishida.
    var rotation: Animatable<Double>
    /// 0...1.
    var opacity: Animatable<Double>

    var blendMode: BlendMode = .normal
    var effects: [LayerEffect] = []
    var mask: LayerMask?

    init(name: String, content: LayerContent, inPoint: Double, outPoint: Double, position: Vec2, scale: Double) {
        self.id = UUID()
        self.name = name
        self.content = content
        self.inPoint = inPoint
        self.outPoint = outPoint
        self.position = Animatable(position)
        self.scale = Animatable(scale)
        self.rotation = Animatable(0)
        self.opacity = Animatable(1)
    }

    func isActive(at time: Double) -> Bool {
        isVisible && time >= inPoint && time < outPoint
    }

    /// Timeline'da ko'rsatish uchun barcha keyframe vaqtlari.
    var allKeyframeTimes: [Double] {
        position.keyframeTimes + scale.keyframeTimes + rotation.keyframeTimes + opacity.keyframeTimes
            + effects.flatMap(\.amount.keyframeTimes)
    }

    var videoSource: VideoSource? {
        if case .video(let source) = content { return source }
        return nil
    }
}

struct MotionProject: Hashable {
    var canvasSize = Size2(width: 1080, height: 1920)
    var duration: Double = 10
    var background: RGBAColor = .black
    /// 0-indeks — eng pastki qatlam.
    var layers: [MotionLayer] = []

    var canvasCenter: Vec2 { Vec2(x: canvasSize.width / 2, y: canvasSize.height / 2) }
}

/// Hashable o'lcham (CGSize SDK'da Hashable emas).
struct Size2: Hashable {
    var width: Double
    var height: Double

    init(width: Double, height: Double) {
        self.width = width
        self.height = height
    }

    init(_ size: CGSize) {
        self.init(width: Double(size.width), height: Double(size.height))
    }

    var cgSize: CGSize { CGSize(width: width, height: height) }
}
