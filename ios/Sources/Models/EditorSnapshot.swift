import Foundation

struct MusicTrack: Equatable {
    let url: URL
    let name: String
    let duration: Double
}

/// Loyihaning ma'lum bir paytdagi to'liq holati: kompozitsiya shundan quriladi.
struct EditorSnapshot {
    var clips: [Clip]
    var overlays: [TextOverlay]
    var filter: VideoFilter
    var music: MusicTrack?
    var originalVolume: Float
    var musicVolume: Float
}
