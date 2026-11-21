package studio.knit.ui.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import studio.knit.achieve.AchievementState
import studio.knit.achieve.Achievements
import studio.knit.achieve.FactsBuilder
import studio.knit.achieve.KnitFacts
import studio.knit.achieve.TIER_NAMES
import studio.knit.achieve.checkAchievements
import studio.knit.data.KnitRepository
import studio.knit.data.Settings
import studio.knit.model.Pattern
import studio.knit.stats.dayKey
import studio.knit.stitch.StitchLibrary
import studio.knit.util.nowMillis

/** A moment to celebrate: an achievement, a new level, halfway, a record. */
data class Cheer(val emoji: String, val title: String, val message: String, val big: Boolean, val id: Long = nowMillis() + counter++) {
    private companion object { var counter = 0L }
}

/**
 * Keeps achievements up to date: recomputes the facts shortly after anything changes, stores
 * what was earned, and announces what is new. The first check after installing quietly awards
 * everything knitted before.
 */
class AchievementTracker(
    private val repo: KnitRepository,
    private val scope: CoroutineScope,
    private val patterns: StateFlow<List<Pattern>?>,
    private val library: StateFlow<StitchLibrary>,
    private val settings: StateFlow<Settings>,
) {
    private val _facts = MutableStateFlow<KnitFacts?>(null)
    /** The latest facts, for the home screen, the achievements screen and rewards. */
    val facts: StateFlow<KnitFacts?> = _facts

    private val _cheers = MutableSharedFlow<Cheer>(extraBufferCapacity = 16)
    val cheers: SharedFlow<Cheer> = _cheers

    private var recordCheeredOn: String? = null

    fun cheer(c: Cheer) { if (settings.value.achievements) _cheers.tryEmit(c) }

    @OptIn(FlowPreview::class)
    fun start() {
        scope.launch {
            combine(
                patterns.filterNotNull(), library, repo.yarnStash, repo.needleStash,
                settings.map { it.accent }.distinctUntilChanged(),
            ) { ps, lib, yarns, needles, accent -> Input(ps, lib, yarns, needles, accent.name) }
                .debounce(600)
                .collect { update(it) }
        }
    }

    private data class Input(val patterns: List<Pattern>, val library: StitchLibrary, val yarns: List<studio.knit.model.Yarn>, val needles: List<studio.knit.model.Needle>, val accent: String)

    private suspend fun update(input: Input) {
        val stored = AchievementState.fromJson(settings.value.achievementState)
        val tried = stored.accentsTried + input.accent
        val now = nowMillis()
        val today = LocalDate.parse(dayKey(now))
        val facts = withContext(Dispatchers.Default) {
            FactsBuilder.build(input.patterns, input.library, input.yarns, input.needles, tried.size, today)
        }
        val previous = _facts.value
        _facts.value = facts
        val check = checkAchievements(stored.copy(accentsTried = tried), facts, now)
        if (check.state != stored) repo.updateSettings { it.copy(achievementState = check.state.toJson()) }

        for (u in check.newUnlocks) {
            val reward = u.def.reward?.takeIf { it.first == u.tier - 1 }?.second
            cheer(Cheer(u.def.emoji, u.title, reward?.let { "Unlocked: ${it.label}" } ?: u.def.describe(u.def.goals[u.tier - 1]), u.big))
        }
        check.levelUp?.let { l -> cheer(Cheer("⭐", "Level $l · ${Achievements.titleOf(l)}", "You reached a new knitting level", big = true)) }

        // A new personal record for rows in a day, once a day, once it means something.
        val todayKey = dayKey(now)
        val todayRows = input.patterns.sumOf { it.progress?.days?.get(todayKey)?.rows ?: 0 }
        if (previous != null && previous.maxRowsInDay >= 20 && facts.maxRowsInDay > previous.maxRowsInDay &&
            todayRows == facts.maxRowsInDay && recordCheeredOn != todayKey
        ) {
            recordCheeredOn = todayKey
            cheer(Cheer("🏆", "New record!", "$todayRows rows today, more than ever before", big = false))
        }
    }
}

/** "Silver" for tier 2. */
fun tierName(tier: Int): String = TIER_NAMES[(tier - 1).coerceIn(0, TIER_NAMES.size - 1)]
