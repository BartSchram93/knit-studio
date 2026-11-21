import ActivityKit
import SwiftUI
import WidgetKit

@main
struct KnitWidgetsBundle: WidgetBundle {
    var body: some Widget {
        KnitLiveActivity()
    }
}

private let accent = Color(red: 0.70, green: 0.33, blue: 0.24)

struct KnitLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: KnitActivityAttributes.self) { context in
            LockScreenView(context: context)
                .padding(14)
                .activityBackgroundTint(Color(.systemBackground).opacity(0.92))
                .activitySystemActionForegroundColor(accent)
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Text(context.state.row).font(.caption.weight(.semibold)).foregroundStyle(accent)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    Text(context.attributes.patternName).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text(context.state.instruction).font(.subheadline).lineLimit(3)
                        StepButtons(context: context)
                    }
                }
            } compactLeading: {
                Image(systemName: "list.number").foregroundStyle(accent)
            } compactTrailing: {
                Text(shortRow(context.state.row)).font(.caption.monospacedDigit()).foregroundStyle(accent)
            } minimal: {
                Text(shortRow(context.state.row)).font(.caption2.monospacedDigit()).foregroundStyle(accent)
            }
        }
    }
}

/// "Row 12 of 80 (WS)" → "ROW 12/80".
private func rowBadge(_ row: String) -> String {
    let parts = row.split(separator: " ")
    guard parts.count >= 4, parts[2] == "of" else { return row.uppercased() }
    return "\(parts[0].uppercased()) \(parts[1])/\(parts[3])"
}

/// "Row 12 of 80 (WS)" → "WS".
private func rowSide(_ row: String) -> String? {
    if row.hasSuffix("(RS)") { return "RS" }
    if row.hasSuffix("(WS)") { return "WS" }
    return nil
}

/// "Row 12 of 80 (WS)" → "12".
private func shortRow(_ row: String) -> String {
    row.split(separator: " ").first(where: { Int($0) != nil }).map(String.init) ?? "✓"
}

struct LockScreenView: View {
    let context: ActivityViewContext<KnitActivityAttributes>

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 8) {
                Text(rowBadge(context.state.row))
                    .font(.caption.weight(.bold)).foregroundStyle(.white)
                    .padding(.horizontal, 9).padding(.vertical, 3)
                    .background(Capsule().fill(accent))
                if let side = rowSide(context.state.row) {
                    Text(side).font(.subheadline.weight(.bold))
                }
                Spacer()
                Text(context.state.today.isEmpty ? context.attributes.patternName : context.state.today)
                    .font(.caption).foregroundStyle(.secondary).lineLimit(1)
            }
            Text([context.state.part, context.state.detail].filter { !$0.isEmpty }.joined(separator: " · "))
                .font(.caption.weight(.semibold)).foregroundStyle(.secondary).lineLimit(1)
            Text(context.state.instruction).font(.headline).lineLimit(5)
            if context.state.fraction >= 0 {
                ProgressView(value: min(max(context.state.fraction, 0), 1)).tint(accent)
            }
            StepButtons(context: context)
        }
    }
}

struct StepButtons: View {
    let context: ActivityViewContext<KnitActivityAttributes>

    var body: some View {
        HStack(spacing: 10) {
            Button(intent: StepKnittingIntent(patternId: context.attributes.patternId, delta: -1)) {
                Label("Back", systemImage: "chevron.left").frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
            .disabled(!context.state.canGoBack)

            Button(intent: StepKnittingIntent(patternId: context.attributes.patternId, delta: 1)) {
                Label("Next row", systemImage: "checkmark").frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .tint(accent)
            .disabled(context.state.finished)
        }
        .font(.subheadline.weight(.semibold))
    }
}
