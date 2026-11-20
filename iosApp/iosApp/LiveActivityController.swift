import ActivityKit
import ComposeApp
import Foundation

/// Shows the row being knitted as a Live Activity on the lock screen (iOS 17 and later, where
/// its Back / Next buttons work). Driven from the shared Kotlin code through LockScreenPresenter.
@available(iOS 17.0, *)
final class LiveActivityController: NSObject, LockScreenPresenter {
    private var current: Activity<KnitActivityAttributes>?

    func show(patternId: String, patternName: String, part: String, row: String, instruction: String,
              detail: String, fraction: Double, finished: Bool, canGoBack: Bool) {
        let state = KnitActivityAttributes.ContentState(
            part: part, row: row, instruction: instruction, detail: detail,
            fraction: fraction, finished: finished, canGoBack: canGoBack)
        let content = ActivityContent(state: state, staleDate: nil)
        let existing = current ?? Activity<KnitActivityAttributes>.activities.first { $0.attributes.patternId == patternId }
        if let activity = existing, activity.activityState == .active {
            current = activity
            Task { await activity.update(content) }
            return
        }
        // One knitting activity at a time.
        for old in Activity<KnitActivityAttributes>.activities {
            Task { await old.end(nil, dismissalPolicy: .immediate) }
        }
        guard ActivityAuthorizationInfo().areActivitiesEnabled else { return }
        current = try? Activity.request(
            attributes: KnitActivityAttributes(patternId: patternId, patternName: patternName),
            content: content)
    }

    func clear() {
        current = nil
        for activity in Activity<KnitActivityAttributes>.activities {
            Task { await activity.end(nil, dismissalPolicy: .immediate) }
        }
    }
}

enum LockScreenSetup {
    /// Connects the Kotlin app to the Live Activity, and the lock-screen buttons to the app.
    static func install() {
        guard #available(iOS 17.0, *) else { return }
        MainViewControllerKt.setLockScreenPresenter(presenter: LiveActivityController())
        KnitIntentHandler.step = { patternId, delta in
            await withCheckedContinuation { (done: CheckedContinuation<Void, Never>) in
                DispatchQueue.main.async {
                    MainViewControllerKt.stepFromLockScreen(patternId: patternId, delta: Int32(delta)) {
                        done.resume()
                    }
                }
            }
        }
    }
}
