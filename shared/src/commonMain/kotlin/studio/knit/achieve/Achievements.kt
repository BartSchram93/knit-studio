package studio.knit.achieve

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.pow

/*
 * Achievements: encouragement, never pressure.
 *
 * Everything is judged on [KnitFacts], so achievements are earned retroactively the first time
 * and never need anything recorded on their own. Most come in tiers (bronze, silver, gold, and
 * sometimes a fourth); hidden ones show only a hint until found. A few unlock a reward: an extra
 * accent colour or confetti style.
 */

enum class AchievementCategory(val label: String) {
    ROWS("Rows & stitches"), TIME("Time"), HABIT("Keeping at it"), PROJECTS("Projects"),
    TECHNIQUE("Techniques"), MAKING("Making & exploring"), HIDDEN("Hidden"),
}

/** Something an achievement unlocks, named by the setting it enables. */
sealed interface Reward {
    val label: String
    data class Accent(val name: String, override val label: String) : Reward
    data class Confetti(val name: String, override val label: String) : Reward
}

data class AchievementDef(
    val id: String,
    val title: String,
    val emoji: String,
    val category: AchievementCategory,
    /** Goal per tier, increasing. One goal means a single achievement without tiers. */
    val goals: List<Long>,
    /** What one tier asks for, e.g. "Knit {n} rows". */
    val describe: (Long) -> String,
    val value: (KnitFacts) -> Long,
    val hidden: Boolean = false,
    val hint: String = "",
    /** Reward and the tier (0-based) that unlocks it. */
    val reward: Pair<Int, Reward>? = null,
    /** How a value is shown: "37" or "1 h 05 min". */
    val format: (Long) -> String = { formatCount(it) },
) {
    val tiers: Int get() = goals.size
}

/** Bronze, silver, gold, and "diamond" for a fourth tier. */
val TIER_NAMES = listOf("Bronze", "Silver", "Gold", "Diamond")

private fun bool(b: Boolean) = if (b) 1L else 0L
private fun hours(s: Long) = s / 3600

fun formatCount(n: Long): String = when {
    n >= 1_000_000 -> "${(n / 100_000) / 10.0}".removeSuffix(".0") + " million"
    n >= 10_000 -> n.toString().reversed().chunked(3).joinToString(" ").reversed()
    else -> n.toString()
}

object Achievements {

    val all: List<AchievementDef> = listOf(
        // --- Rows & stitches ------------------------------------------------------------------
        AchievementDef("first-row", "First stitch", "🧶", AchievementCategory.ROWS, listOf(1),
            { "Knit your first row in the app" }, { it.totalRows }),
        AchievementDef("rows", "Row counter", "🔢", AchievementCategory.ROWS, listOf(100, 1_000, 10_000),
            { "Knit ${formatCount(it)} rows" }, { it.totalRows },
            reward = 1 to Reward.Confetti("YARN", "Yarn-ball confetti")),
        AchievementDef("stitches", "Stitch counter", "🪡", AchievementCategory.ROWS, listOf(10_000, 100_000, 1_000_000),
            { "Knit ${formatCount(it)} stitches" }, { it.totalStitches }),
        AchievementDef("big-day", "Productive day", "⚡", AchievementCategory.ROWS, listOf(50, 100, 250),
            { "Knit $it rows in one day" }, { it.maxRowsInDay.toLong() }),

        // --- Time -------------------------------------------------------------------------------
        AchievementDef("hours", "Hours of knitting", "⏳", AchievementCategory.TIME, listOf(1, 25, 100, 500),
            { "Knit for $it ${if (it == 1L) "hour" else "hours"}" }, { hours(it.totalSeconds) },
            reward = 2 to Reward.Accent("BERRY", "Berry colour")),
        AchievementDef("session", "In the flow", "🌊", AchievementCategory.TIME, listOf(1, 2, 3),
            { "Knit for $it ${if (it == 1L) "hour" else "hours"} without a long break" }, { hours(it.longestSession) }),

        // --- Keeping at it ------------------------------------------------------------------------
        AchievementDef("weeks", "Knitting weeks", "📅", AchievementCategory.HABIT, listOf(2, 8, 26, 52),
            { "Knit on 3 days of a week, $it weeks" }, { it.knitWeeks.toLong() }),
        AchievementDef("streak", "On a roll", "🔥", AchievementCategory.HABIT, listOf(3, 7, 30),
            { "Knit $it days in a row (one rest day a week is fine)" }, { it.bestStreak.toLong() },
            reward = 1 to Reward.Confetti("LEAVES", "Leaf confetti")),

        // --- Projects ---------------------------------------------------------------------------
        AchievementDef("finisher", "Finisher", "🏁", AchievementCategory.PROJECTS, listOf(1, 5, 15, 30),
            { "Finish $it ${if (it == 1L) "project" else "projects"}" }, { it.projectsFinished.toLong() },
            reward = 1 to Reward.Accent("GOLD", "Gold colour")),
        AchievementDef("parts", "Piece by piece", "🧩", AchievementCategory.PROJECTS, listOf(1, 10, 50),
            { "Finish $it ${if (it == 1L) "part" else "parts"} of a pattern" }, { it.partsFinished.toLong() }),
        AchievementDef("wardrobe", "Wardrobe", "👗", AchievementCategory.PROJECTS, listOf(5),
            { "Finish a hat, scarf, socks, sweater and gloves" },
            { f -> WARDROBE.count { it in f.finishedCategories }.toLong() }, format = { "$it of 5" }),
        AchievementDef("quick", "Quick knit", "🐇", AchievementCategory.PROJECTS, listOf(1),
            { "Finish a project within 7 days of starting" }, { bool(it.quickFinish) }, format = { if (it > 0) "Done" else "Not yet" }),
        AchievementDef("long-haul", "Long haul", "🐢", AchievementCategory.PROJECTS, listOf(1),
            { "Finish a project that took more than 3 months" }, { bool(it.longHaulFinish) }, format = { if (it > 0) "Done" else "Not yet" }),

        // --- Techniques -------------------------------------------------------------------------
        AchievementDef("cables", "Cable crossing", "🥨", AchievementCategory.TECHNIQUE, listOf(1, 100, 1_000),
            { if (it == 1L) "Knit your first cable" else "Knit ${formatCount(it)} cables" }, { it.cables }),
        AchievementDef("lace", "Lace maker", "🕸", AchievementCategory.TECHNIQUE, listOf(50, 500, 5_000),
            { "Knit ${formatCount(it)} yarn overs" }, { it.yarnOvers },
            reward = 1 to Reward.Confetti("STARS", "Star confetti")),
        AchievementDef("colourwork", "Colourwork", "🎨", AchievementCategory.TECHNIQUE, listOf(2, 3, 4),
            { "Knit a row with $it colours" }, { it.maxColoursInRow.toLong() }),
        AchievementDef("shaping", "Shaping", "📐", AchievementCategory.TECHNIQUE, listOf(100, 1_000),
            { "Knit ${formatCount(it)} increases and ${formatCount(it)} decreases" }, { minOf(it.increases, it.decreases) }),
        AchievementDef("round", "In the round", "⭕", AchievementCategory.TECHNIQUE, listOf(1),
            { "Finish a project knitted in the round" }, { bool(it.roundFinished) }, format = { if (it > 0) "Done" else "Not yet" }),

        // --- Making & exploring -------------------------------------------------------------------
        AchievementDef("designer", "Designer", "✏️", AchievementCategory.MAKING, listOf(1, 10),
            { if (it == 1L) "Draw a chart" else "Draw $it charts" }, { it.charts.toLong() }),
        AchievementDef("writer", "Pattern writer", "📝", AchievementCategory.MAKING, listOf(1, 10),
            { if (it == 1L) "Write out a section of rows" else "Write $it sections of rows" }, { it.writtenSections.toLong() }),
        AchievementDef("pdf", "PDF reader", "📄", AchievementCategory.MAKING, listOf(1),
            { "Read rows from a PDF" }, { it.pdfSections.toLong() }),
        AchievementDef("own-stitch", "My own stitch", "✨", AchievementCategory.MAKING, listOf(1),
            { "Create a stitch of your own" }, { it.customStitches.toLong() }),
        AchievementDef("renamed", "Call it what you like", "🏷", AchievementCategory.MAKING, listOf(1),
            { "Give a stitch your own name" }, { it.renamedStitches.toLong() }),
        AchievementDef("stash", "Yarn collector", "🧺", AchievementCategory.MAKING, listOf(10, 25, 50),
            { "Keep $it yarns in your stash" }, { it.yarns.toLong() }),
        AchievementDef("photo", "Picture perfect", "📷", AchievementCategory.MAKING, listOf(1),
            { "Add a photo of a yarn" }, { it.yarnPhotos.toLong() }),
        AchievementDef("needles", "Needle drawer", "🥢", AchievementCategory.MAKING, listOf(20),
            { "Keep $it needles in your stash" }, { it.needles.toLong() }),

        // --- Hidden -----------------------------------------------------------------------------
        AchievementDef("night-owl", "Night owl", "🦉", AchievementCategory.HIDDEN, listOf(1),
            { "Knit between midnight and 4 am" }, { it.nightRows.toLong() }, hidden = true, hint = "When everyone is asleep…",
            reward = 0 to Reward.Accent("MIDNIGHT", "Midnight colour")),
        AchievementDef("early-bird", "Early bird", "🐦", AchievementCategory.HIDDEN, listOf(1),
            { "Knit between 4 and 7 am" }, { it.earlyRows.toLong() }, hidden = true, hint = "Before the coffee"),
        AchievementDef("second-sock", "Second sock syndrome", "🧦", AchievementCategory.HIDDEN, listOf(2),
            { "Finish two pairs of socks" }, { it.socksFinished.toLong() }, hidden = true, hint = "There should be another one"),
        AchievementDef("frogger", "Frog and finish", "🐸", AchievementCategory.HIDDEN, listOf(1),
            { "Take out 20 rows of a project and still finish it" }, { bool(it.undoneThenFinished) }, hidden = true, hint = "Sometimes it has to come out"),
        AchievementDef("anniversary", "Knitting anniversary", "🎂", AchievementCategory.HIDDEN, listOf(1),
            { "Knit one year after your first row in the app" }, { bool(it.anniversary) }, hidden = true, hint = "A year together"),
        AchievementDef("rainbow", "Rainbow", "🌈", AchievementCategory.HIDDEN, listOf(6),
            { "Try every colour in Settings" }, { it.accentsTried.toLong() }, hidden = true, hint = "All the colours of the yarn"),
        AchievementDef("comeback", "Welcome back", "🏠", AchievementCategory.HIDDEN, listOf(1),
            { "Pick up the needles again after a month away" }, { bool(it.comeback) }, hidden = true, hint = "We missed you"),
        AchievementDef("perfect", "Perfect count", "💯", AchievementCategory.HIDDEN, listOf(1),
            { "Finish a pattern of 100+ rows where every count adds up" }, { bool(it.perfectFinish) }, hidden = true, hint = "Everything adds up"),
        AchievementDef("midnight", "Midnight row", "🕰", AchievementCategory.HIDDEN, listOf(1),
            { "Finish a row at the stroke of midnight" }, { bool(it.midnight) }, hidden = true, hint = "Right on time"),
        AchievementDef("christmas", "Christmas knitting", "🎄", AchievementCategory.HIDDEN, listOf(1),
            { "Knit on 24, 25 or 26 December" }, { bool(it.christmas) }, hidden = true, hint = "Ho ho ho"),
        AchievementDef("kip", "Knit in public", "🌍", AchievementCategory.HIDDEN, listOf(1),
            { "Knit on World Wide Knit in Public Day (second Saturday of June)" }, { bool(it.knitInPublicDay) }, hidden = true, hint = "Out in the open"),
        AchievementDef("slow", "Slow knitting", "🐌", AchievementCategory.HIDDEN, listOf(1),
            { "Finish a project that took more than a year" }, { bool(it.slowFinish) }, hidden = true, hint = "Every stitch counts"),
    )

    private val WARDROBE = listOf(
        studio.knit.model.Category.HAT, studio.knit.model.Category.SCARF, studio.knit.model.Category.SOCKS,
        studio.knit.model.Category.SWEATER, studio.knit.model.Category.GLOVES,
    )

    fun byId(id: String): AchievementDef? = all.firstOrNull { it.id == id }

    /** Tiers reached for [def] with [facts] (0 = none). */
    fun tierOf(def: AchievementDef, facts: KnitFacts): Int {
        val v = def.value(facts)
        return def.goals.count { v >= it }
    }

    /** Rewards unlocked by the tiers reached. */
    fun rewards(facts: KnitFacts): List<Reward> =
        all.mapNotNull { d -> d.reward?.takeIf { (tier, _) -> tierOf(d, facts) > tier }?.second }

    /** The achievement (and tier) closest to being reached, for "Next goal" on the home screen. */
    fun nextGoal(facts: KnitFacts): Goal? = all.filter { !it.hidden }.mapNotNull { d ->
        val reached = tierOf(d, facts)
        if (reached >= d.tiers) return@mapNotNull null
        val goal = d.goals[reached]
        val value = d.value(facts)
        val before = if (reached == 0) 0 else d.goals[reached - 1]
        // Only goals there is a start on, so "next" means something.
        if (value <= before && reached == 0 && value == 0L) return@mapNotNull null
        Goal(d, reached, value, goal, ((value - before).toDouble() / (goal - before)).coerceIn(0.0, 1.0))
    }.maxByOrNull { it.fraction }

    data class Goal(val def: AchievementDef, val tier: Int, val value: Long, val goal: Long, val fraction: Double)

    // --- Levels ------------------------------------------------------------------------------

    /** Experience: rows, minutes, finished parts and projects, and every tier earned. */
    fun xp(facts: KnitFacts): Long =
        facts.totalRows + 2 * (facts.totalSeconds / 60) + 50L * facts.partsFinished + 300L * facts.projectsFinished +
            100L * all.sumOf { tierOf(it, facts) }

    const val MAX_LEVEL = 40

    /** Experience needed to reach [level]. Early levels come quickly, later ones slower. */
    fun xpFor(level: Int): Long = if (level <= 1) 0 else (150 * (level - 1).toDouble().pow(1.7)).toLong()

    fun levelOf(xp: Long): Int = (1..MAX_LEVEL).last { xpFor(it) <= xp }

    private val TITLES = listOf(
        1 to "Slipknot", 3 to "Cast-on", 5 to "Garter knitter", 8 to "Stockinette steady", 12 to "Rib royalty",
        16 to "Cable crafter", 20 to "Lace maker", 25 to "Colourwork artist", 30 to "Pattern whisperer",
        35 to "Yarn sage", 40 to "Grand knitter",
    )

    fun titleOf(level: Int): String = TITLES.last { it.first <= level }.second

    data class Level(val level: Int, val title: String, val xp: Long, val fromXp: Long, val toXp: Long?) {
        val fraction: Float get() = if (toXp == null) 1f else ((xp - fromXp).toFloat() / (toXp - fromXp)).coerceIn(0f, 1f)
    }

    fun level(facts: KnitFacts): Level {
        val x = xp(facts)
        val l = levelOf(x)
        return Level(l, titleOf(l), x, xpFor(l), if (l < MAX_LEVEL) xpFor(l + 1) else null)
    }
}

/**
 * What has been earned and when, kept with the settings (and so in backups). Unlocks are keyed
 * "id:tier" (tier 1 = bronze) with the time they were first seen.
 */
@Serializable
data class AchievementState(
    val unlocked: Map<String, Long> = emptyMap(),
    val accentsTried: Set<String> = emptySet(),
    /** False until the first check, which awards earlier knitting quietly. */
    val initialized: Boolean = false,
    /** Highest level celebrated, so a level-up is celebrated once. */
    val levelSeen: Int = 1,
) {
    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
        fun fromJson(s: String): AchievementState = if (s.isBlank()) AchievementState() else runCatching { json.decodeFromString(serializer(), s) }.getOrDefault(AchievementState())
    }
}

/** A tier just earned. */
data class Unlock(val def: AchievementDef, val tier: Int) {
    val key: String get() = "${def.id}:$tier"
    /** Gold and above, finishing-related and hidden ones get confetti; others a quiet toast. */
    val big: Boolean get() = def.hidden || tier >= 3 || def.category == AchievementCategory.PROJECTS || def.reward?.first == tier - 1
    val title: String get() = if (def.tiers > 1) "${def.title} · ${TIER_NAMES[(tier - 1).coerceIn(0, 3)]}" else def.title
}

data class AchievementCheck(val state: AchievementState, val newUnlocks: List<Unlock>, val levelUp: Int?)

/** Compares [facts] with what was earned before: what is new, and the state to store. */
fun checkAchievements(state: AchievementState, facts: KnitFacts, now: Long): AchievementCheck {
    val reached = Achievements.all.flatMap { d -> (1..Achievements.tierOf(d, facts)).map { Unlock(d, it) } }
    val fresh = reached.filter { it.key !in state.unlocked }
    val level = Achievements.level(facts).level
    val next = state.copy(
        unlocked = state.unlocked + fresh.associate { it.key to now },
        initialized = true,
        levelSeen = maxOf(state.levelSeen, level),
    )
    // The very first check only catches up quietly with what was knitted before.
    if (!state.initialized) return AchievementCheck(next, emptyList(), null)
    return AchievementCheck(next, fresh, level.takeIf { it > state.levelSeen })
}
