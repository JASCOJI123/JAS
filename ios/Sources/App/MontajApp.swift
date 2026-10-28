import AVFoundation
import SwiftUI

@main
struct MontajApp: App {
    init() {
        // Ovoz telefon "jim" rejimida ham eshitilishi uchun.
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .moviePlayback)
    }

    var body: some Scene {
        WindowGroup {
            EditorView()
                .preferredColorScheme(.dark)
        }
    }
}
