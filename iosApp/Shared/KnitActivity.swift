import Foundation
import ActivityKit
import AppIntents

/// The Live Activity shown on the lock screen while Knit mode is open.
/// Compiled into both the app and the widget extension.
struct KnitActivityAttributes: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        var part: String
        /// "Row 12 of 80 (WS)"
        var row: String
        var instruction: String
        var detail: String
        /// 0…1, or -1 when the part has no end (a repeating chart).
        var fraction: Double
        var finished: Bool
        var canGoBack: Bool
    }

    var patternId: String
    var patternName: String
}

/// Where the app receives Back / Next. Set by the app at launch; never used in the extension,
/// because a LiveActivityIntent always runs in the app's own process.
enum KnitIntentHandler {
    static var step: ((String, Int) async -> Void)?
}

/// Back / Next on the lock screen. Runs in the app (launched in the background if needed), so it
/// changes the knitting progress directly; the app then updates the Live Activity.
@available(iOS 17.0, *)
struct StepKnittingIntent: LiveActivityIntent {
    static var title: LocalizedStringResource = "Step knitting row"
    static var isDiscoverable: Bool = false

    @Parameter(title: "Pattern") var patternId: String
    @Parameter(title: "Rows") var delta: Int

    init() {}

    init(patternId: String, delta: Int) {
        self.patternId = patternId
        self.delta = delta
    }

    func perform() async throws -> some IntentResult {
        await KnitIntentHandler.step?(patternId, delta)
        return .result()
    }
}
