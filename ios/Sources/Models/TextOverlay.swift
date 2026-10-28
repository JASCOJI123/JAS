import UIKit

struct TextOverlay: Identifiable, Equatable {
    let id: UUID
    var text: String
    /// Tayyor videodagi vaqt (soniya).
    var start: Double
    var end: Double
    var position: TextPosition
    var fontSize: Double
    var color: TextColor
    var hasBackground: Bool

    init(text: String, start: Double, end: Double) {
        self.id = UUID()
        self.text = text
        self.start = start
        self.end = end
        self.position = .bottom
        self.fontSize = 72
        self.color = .white
        self.hasBackground = true
    }
}

enum TextPosition: String, CaseIterable, Identifiable {
    case top, center, bottom

    var id: String { rawValue }

    var title: String {
        switch self {
        case .top: return "Tepa"
        case .center: return "O'rta"
        case .bottom: return "Past"
        }
    }

    /// Matn markazining kadr balandligiga nisbatan joyi (0 = tepa, 1 = past).
    var verticalAnchor: CGFloat {
        switch self {
        case .top: return 0.14
        case .center: return 0.5
        case .bottom: return 0.78
        }
    }
}

enum TextColor: String, CaseIterable, Identifiable {
    case white, yellow, red, cyan, black

    var id: String { rawValue }

    var uiColor: UIColor {
        switch self {
        case .white: return .white
        case .yellow: return UIColor(red: 1, green: 0.85, blue: 0.1, alpha: 1)
        case .red: return UIColor(red: 1, green: 0.25, blue: 0.25, alpha: 1)
        case .cyan: return UIColor(red: 0.2, green: 0.85, blue: 1, alpha: 1)
        case .black: return .black
        }
    }
}
