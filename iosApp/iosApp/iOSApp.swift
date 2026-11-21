import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    @Environment(\.scenePhase) private var scenePhase

    init() {
        // Also runs when iOS starts the app in the background for a lock-screen button.
        LockScreenSetup.install()
    }

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
