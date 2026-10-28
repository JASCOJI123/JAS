import Foundation

struct Clip: Identifiable, Equatable {
    static let minimumLength: Double = 0.3
    static let speedOptions: [Double] = [0.25, 0.5, 1, 1.5, 2, 3]

    let id: UUID
    let url: URL
    /// Manba videoning to'liq davomiyligi (soniya).
    let duration: Double
    var trimStart: Double
    var trimEnd: Double
    var speed: Double

    init(url: URL, duration: Double) {
        self.id = UUID()
        self.url = url
        self.duration = duration
        self.trimStart = 0
        self.trimEnd = duration
        self.speed = 1
    }

    var trimmedDuration: Double { max(trimEnd - trimStart, 0) }
    var outputDuration: Double { trimmedDuration / speed }
}
