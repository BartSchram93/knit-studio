import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
        .onChange(of: scenePhase) { phase in
            // Write pending edits when the app leaves the foreground.
            if phase != .active {
                MainViewControllerKt.flushPendingChanges()
            }
        }
    }
}
