import Foundation

enum Format {
    /// 75.4 -> "1:15"
    static func clock(_ seconds: Double) -> String {
        let total = Int(seconds.rounded())
        return String(format: "%d:%02d", total / 60, total % 60)
    }

    /// 3.25 -> "3.3 s"
    static func seconds(_ seconds: Double) -> String {
        String(format: "%.1f s", seconds)
    }

    /// 0.5 -> "0.5x", 2 -> "2x"
    static func speed(_ speed: Double) -> String {
        String(format: "%gx", speed)
    }
}
