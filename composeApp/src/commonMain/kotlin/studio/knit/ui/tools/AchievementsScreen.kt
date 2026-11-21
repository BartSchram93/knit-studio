package studio.knit.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import studio.knit.achieve.AchievementCategory
import studio.knit.achieve.AchievementDef
import studio.knit.achieve.AchievementState
import studio.knit.achieve.Achievements
import studio.knit.achieve.KnitFacts
import studio.knit.achieve.MONTH_NAMES
import studio.knit.achieve.formatCount
import studio.knit.achieve.yearReview
import studio.knit.stats.formatDuration
import studio.knit.ui.app.LocalApp
import studio.knit.ui.app.LocalNav
import studio.knit.ui.app.ProgressBar
import studio.knit.ui.app.tierName
import studio.knit.ui.components.EmptyState
import studio.knit.ui.components.Group
import studio.knit.ui.components.Icons
import studio.knit.ui.components.KnitTexture
import studio.knit.ui.components.ListRow
import studio.knit.ui.components.Page
import studio.knit.ui.components.PagePadding
import studio.knit.ui.components.RowDivider
import studio.knit.ui.components.SectionFooter
import studio.knit.ui.components.SectionHeader
import studio.knit.ui.components.Sheet
import studio.knit.ui.components.pressable
import studio.knit.ui.theme.Knit
import studio.knit.util.nowMillis

/** Bronze, silver, gold and diamond ring colours. */
private val TIER_COLOURS = listOf(Color(0xFFB87333), Color(0xFF9EA7B0), Color(0xFFD4A017), Color(0xFF6FB7D9))

private fun tierColour(tier: Int): Color = TIER_COLOURS[(tier - 1).coerceIn(0, TIER_COLOURS.size - 1)]

/** The knitter's level, its title and progress to the next one. */
@Composable
fun LevelCard(facts: KnitFacts, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val level = remember(facts) { Achievements.level(facts) }
    val goal = remember(facts) { Achievements.nextGoal(facts) }
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Knit.colors.surface)
            .then(if (onClick != null) Modifier.pressable(onClick = onClick) else Modifier).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(Knit.colors.accent), contentAlignment = Alignment.Center) {
                Text("${level.level}", style = Knit.type.title2, color = Knit.colors.onAccent)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(level.title, style = Knit.type.title3, color = Knit.colors.label)
                Text(
                    level.toXp?.let { to -> "Level ${level.level} · ${formatCount(to - level.xp)} points to level ${level.level + 1}" } ?: "Highest level reached",
                    style = Knit.type.footnote, color = Knit.colors.secondaryLabel,
                )
            }
            if (facts.currentStreak >= 2) {
                Text("🔥 ${facts.currentStreak}", style = Knit.type.headline, color = Knit.colors.accent,
                    modifier = Modifier.semantics { contentDescription = "${facts.currentStreak} day streak" })
            }
        }
        Spacer(Modifier.height(10.dp))
        ProgressBar(level.fraction, Modifier.fillMaxWidth(), height = 6.dp)
        if (goal != null) {
            Spacer(Modifier.height(10.dp))
            val left = goal.goal - goal.value
            Text(
                "${goal.def.emoji}  Next: ${goal.def.title}${if (goal.def.tiers > 1) " · " + tierName(goal.tier + 1) else ""} — ${goal.def.format(left)} to go",
                style = Knit.type.subhead, color = Knit.colors.label, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A round badge: coloured when earned (ring in the tier colour), grey with progress when not. */
@Composable
fun AchievementBadge(def: AchievementDef, tier: Int, size: Dp = 64.dp) {
    val earned = tier > 0
    val secret = def.hidden && !earned
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(if (earned) Knit.colors.accentSoft else Knit.colors.fill)
            .then(if (earned) Modifier.border(3.dp, tierColour(tier), CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (secret) "?" else def.emoji, style = if (secret) Knit.type.title2.copy(color = Knit.colors.tertiaryLabel) else Knit.type.title2,
            modifier = Modifier.alpha(if (earned || secret) 1f else 0.35f))
    }
}

@Composable
fun AchievementsScreen() {
    val app = LocalApp.current
    val nav = LocalNav.current
    val facts by app.achievements.facts.collectAsState()
    val settings by app.settings.collectAsState()
    val state = remember(settings.achievementState) { AchievementState.fromJson(settings.achievementState) }
    var open by remember { mutableStateOf<AchievementDef?>(null) }
    Page("Achievements", onBack = nav::back, backLabel = "Back", largeTitle = true) {
        val f = facts
        if (f == null) {
            item { EmptyState(Icons.Sparkle, "Counting your knitting…", "One moment.") }
            return@Page
        }
        item { LevelCard(f, Modifier.padding(horizontal = PagePadding)) }
        item {
            val earned = Achievements.all.sumOf { Achievements.tierOf(it, f) }
            val total = Achievements.all.sumOf { it.tiers }
            SectionFooter("$earned of $total badges earned. Everything you knitted before counts too.")
        }
        item {
            Group(Modifier.padding(top = 10.dp)) {
                ListRow("Your knitting year", subtitle = "Hours, rows, finished projects and your busiest month", leading = { Text("📖", style = Knit.type.title3) }, chevron = true) { nav.tool("year") }
            }
        }
        for (category in AchievementCategory.entries) {
            val defs = Achievements.all.filter { it.category == category }
            item(key = "h-${category.name}") { SectionHeader(category.label) }
            item(key = "g-${category.name}") {
                BadgeGrid(defs, f) { open = it }
            }
            if (category == AchievementCategory.HIDDEN) {
                item { SectionFooter("Hidden achievements show only a hint until you find them.") }
            }
        }
    }
    open?.let { def -> AchievementSheet(def, facts ?: KnitFacts(), state) { open = null } }
}

@Composable
private fun BadgeGrid(defs: List<AchievementDef>, facts: KnitFacts, onOpen: (AchievementDef) -> Unit) {
    Column(Modifier.padding(horizontal = PagePadding).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Knit.colors.surface).padding(vertical = 14.dp)) {
        for (row in defs.chunked(3)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                for (def in row) {
                    val tier = Achievements.tierOf(def, facts)
                    val secret = def.hidden && tier == 0
                    Column(
                        Modifier.width(104.dp).pressable { onOpen(def) }.padding(vertical = 8.dp)
                            .semantics { contentDescription = if (secret) "Hidden achievement" else "${def.title}, ${if (tier > 0) tierName(tier) else "not yet earned"}" },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AchievementBadge(def, tier)
                        Spacer(Modifier.height(6.dp))
                        Text(if (secret) "???" else def.title, style = Knit.type.footnote, color = if (tier > 0) Knit.colors.label else Knit.colors.secondaryLabel,
                            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (!secret && def.tiers > 1) {
                            Text(if (tier > 0) tierName(tier) else "${tier}/${def.tiers}", style = Knit.type.caption,
                                color = if (tier > 0) tierColour(tier) else Knit.colors.tertiaryLabel)
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.width(104.dp)) }
            }
        }
    }
}

/** Details of one achievement: what each tier asks, progress, when it was earned, its reward. */
@Composable
private fun AchievementSheet(def: AchievementDef, facts: KnitFacts, state: AchievementState, onDismiss: () -> Unit) {
    val tier = Achievements.tierOf(def, facts)
    val secret = def.hidden && tier == 0
    val value = def.value(facts)
    Sheet(if (secret) "Hidden achievement" else def.title, onDismiss) {
        Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AchievementBadge(def, tier, 96.dp)
            Spacer(Modifier.height(10.dp))
            if (secret) {
                Text("“${def.hint}”", style = Knit.type.title3, color = Knit.colors.label, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
                Spacer(Modifier.height(6.dp))
                Text("Keep knitting to find out.", style = Knit.type.subhead, color = Knit.colors.secondaryLabel)
            }
        }
        if (!secret) {
            SectionHeader(if (def.tiers > 1) "Levels" else "How to earn it")
            Group {
                def.goals.forEachIndexed { i, goal ->
                    if (i > 0) RowDivider()
                    val reached = tier > i
                    val at = state.unlocked["${def.id}:${i + 1}"]
                    ListRow(
                        def.describe(goal),
                        subtitle = when {
                            reached && at != null -> "Earned ${studio.knit.ui.app.formatDate(at, nowMillis())}"
                            reached -> "Earned"
                            i == tier -> "${def.format(value)} of ${def.format(goal)}"
                            else -> null
                        },
                        leading = if (def.tiers > 1) ({ Box(Modifier.size(14.dp).clip(CircleShape).background(if (reached) tierColour(i + 1) else Knit.colors.fill)) }) else null,
                        trailing = if (reached) ({ studio.knit.ui.components.KIcon(Icons.Check, Knit.colors.success, 20.dp) }) else null,
                    )
                    if (i == tier) ProgressBar(((value.toDouble() - (def.goals.getOrNull(i - 1) ?: 0)) / (goal - (def.goals.getOrNull(i - 1) ?: 0))).toFloat().coerceIn(0f, 1f),
                        Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp).fillMaxWidth())
                }
            }
            def.reward?.let { (rewardTier, reward) ->
                SectionFooter(
                    (if (tier > rewardTier) "Unlocked: " else "Reward at ${if (def.tiers > 1) tierName(rewardTier + 1) else "this achievement"}: ") +
                        reward.label + " (Settings › Appearance)",
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** A year of knitting on one page. */
@Composable
fun YearReviewScreen() {
    val app = LocalApp.current
    val nav = LocalNav.current
    val patterns by app.patterns.collectAsState()
    val year = remember { studio.knit.stats.dayKey(nowMillis()).take(4).toInt() }
    val review = remember(patterns, year) { yearReview(patterns.orEmpty(), year) }
    Page("Your $year", onBack = nav::back, backLabel = "Back") {
        if (review.isEmpty) {
            item { EmptyState(Icons.Counter, "Nothing knitted yet this year", "Your year fills up as you knit.") }
            return@Page
        }
        item {
            Box(Modifier.padding(horizontal = PagePadding).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Knit.colors.accentSoft)) {
                KnitTexture(Modifier.matchParentSizeCompat())
                Column(Modifier.padding(20.dp)) {
                    Text(formatDuration(review.seconds), style = Knit.type.largeTitle, color = Knit.colors.label)
                    Text("of knitting on ${review.knitDays} ${if (review.knitDays == 1) "day" else "days"}", style = Knit.type.subhead, color = Knit.colors.secondaryLabel)
                    Spacer(Modifier.height(14.dp))
                    Text("${formatCount(review.rows)} rows", style = Knit.type.title2, color = Knit.colors.label)
                }
            }
        }
        item { SectionHeader("Highlights") }
        item {
            Group {
                ListRow("Finished projects", value = "${review.projectsFinished.size}",
                    subtitle = review.projectsFinished.take(5).joinToString(", ").ifBlank { null })
                review.busiestMonth?.let { (m, rows) -> RowDivider(); ListRow("Busiest month", value = MONTH_NAMES[m - 1], subtitle = "${formatCount(rows)} rows") }
                review.bestDay?.let { (d, rows) -> RowDivider(); ListRow("Best day", value = "$rows rows", subtitle = "${d.day} ${MONTH_NAMES[d.month.ordinal]}") }
                if (review.bestStreak > 1) { RowDivider(); ListRow("Longest streak", value = "${review.bestStreak} days") }
                review.favouritePattern?.let { (name, _) -> RowDivider(); ListRow("Most loved pattern", value = name) }
                review.favouriteCategory?.let { c -> RowDivider(); ListRow("Favourite kind of project", value = c.label) }
            }
        }
        item { SectionFooter("Counted from the knitting the app has logged this year.") }
    }
}

/** fillMaxSize inside a Box without needing BoxScope here. */
private fun Modifier.matchParentSizeCompat(): Modifier = this.then(Modifier.fillMaxWidth().height(150.dp))
