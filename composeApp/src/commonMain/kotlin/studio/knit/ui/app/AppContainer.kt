package studio.knit.ui.app

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import studio.knit.data.KnitRepository
import studio.knit.data.Settings
import studio.knit.model.Pattern
import studio.knit.stitch.StitchLibrary

/**
 * Application-wide state: the repository, the user's settings and stitch library, and the
 * editors of the patterns currently open. Created once per process by each platform.
 */
class AppContainer(
    val repo: KnitRepository,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    initialSettings: Settings = Settings(),
) {
    val scope = CoroutineScope(SupervisorJob() + dispatcher)

    val settings: StateFlow<Settings> = repo.settings.stateIn(scope, SharingStarted.Eagerly, initialSettings)
    val library: StateFlow<StitchLibrary> = repo.library.stateIn(scope, SharingStarted.Eagerly, StitchLibrary())
    val patterns: StateFlow<List<Pattern>?> = repo.patterns.stateIn<List<Pattern>?>(scope, SharingStarted.Eagerly, null)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    /** Short confirmations and errors shown as a transient banner. */
    val messages: SharedFlow<String> = _messages
    fun message(text: String) { _messages.tryEmit(text) }

    private val editors = HashMap<String, PatternEditor>()

    /** The single live editor for a pattern; every screen showing that pattern shares it. */
    fun editor(id: String): PatternEditor = editors.getOrPut(id) { PatternEditor(id, repo, scope) }

    /** Saves pending changes and forgets the editor when the pattern is closed. */
    fun release(id: String) { editors.remove(id)?.flush() }

    fun flushAll() { editors.values.forEach { it.flush() } }
}

val LocalApp = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }

/**
 * Holds one pattern while it is being edited and saves automatically shortly after each change.
 * Edits are applied to memory immediately, so the UI never waits for the database.
 */
class PatternEditor(val id: String, private val repo: KnitRepository, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow<Pattern?>(null)
    val state: StateFlow<Pattern?> = _state

    private val _loaded = MutableStateFlow(false)
    /** True once the first load finished; a null [state] then means the pattern does not exist. */
    val loaded: StateFlow<Boolean> = _loaded

    private var saveJob: Job? = null
    private var dirty = false

    init {
        scope.launch {
            _state.value = repo.getPattern(id)
            _loaded.value = true
        }
    }

    val current: Pattern? get() = _state.value

    /** Picks up changes made elsewhere (e.g. from the library list) when nothing is pending here. */
    fun refresh() {
        if (dirty) return
        scope.launch {
            val fresh = repo.getPattern(id)
            if (!dirty) _state.value = fresh
        }
    }

    fun update(transform: (Pattern) -> Pattern) {
        val cur = _state.value ?: return
        val next = transform(cur)
        if (next == cur) return
        _state.value = next
        dirty = true
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(SAVE_DELAY_MS)
            saveNow()
        }
    }

    private suspend fun saveNow() {
        val p = _state.value ?: return
        if (!dirty) return
        dirty = false
        try {
            repo.save(p)
        } catch (e: Exception) {
            dirty = true
            throw e
        }
    }

    /** Writes any pending change right away. */
    fun flush() {
        saveJob?.cancel()
        if (dirty) scope.launch { saveNow() }
    }

    suspend fun flushAndWait() {
        saveJob?.cancel()
        saveNow()
    }

    private companion object {
        const val SAVE_DELAY_MS = 350L
    }
}
