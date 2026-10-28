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
            TabView {
                EditorView()
                    .tabItem { Label("Montaj", systemImage: "film.stack") }
                MotionEditorView()
                    .tabItem { Label("Motion", systemImage: "sparkles") }
            }
            .preferredColorScheme(.dark)
        }
    }
}
