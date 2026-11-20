package studio.knit.ui.companion

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import studio.knit.companion.Position
import studio.knit.companion.Track
import studio.knit.companion.bump
import studio.knit.companion.isComplete
import studio.knit.companion.overallProgress
import studio.knit.companion.position
import studio.knit.companion.setPosition
import studio.knit.companion.startProgress
import studio.knit.companion.step
import studio.knit.companion.tracksOf
import studio.knit.model.Construction
import studio.knit.model.Counter
import studio.knit.model.Pattern
import studio.knit.model.Progress
import studio.knit.model.ProjectNote
import studio.knit.model.RowKind
import studio.knit.model.Side
import studio.knit.stitch.StitchLibrary
import studio.knit.ui.app.LocalApp
import studio.knit.ui.app.LocalNav
import studio.knit.ui.app.PatternEditor
import studio.knit.ui.app.ProgressBar
import studio.knit.ui.app.formatDate
import studio.knit.ui.app.rememberHaptic
import studio.knit.ui.chart.ChartThumbnail
import studio.knit.ui.components.BoundField
import studio.knit.ui.components.ConfirmDialog
import studio.knit.ui.components.EmptyState
import studio.knit.ui.components.Field
import studio.knit.ui.components.FixedPage
import studio.knit.ui.components.Group
import studio.knit.ui.components.IconBtn
import studio.knit.ui.components.Icons
import studio.knit.ui.components.KIcon
import studio.knit.ui.components.ListRow
import studio.knit.ui.components.Menu
import studio.knit.ui.components.MenuButton
import studio.knit.ui.components.MenuItem
import studio.knit.ui.components.PagePadding
import studio.knit.ui.components.PrimaryButton
import studio.knit.ui.components.PromptDialog
import studio.knit.ui.components.RowDivider
import studio.knit.ui.components.SectionFooter
import studio.knit.ui.components.SectionHeader
import studio.knit.ui.components.Segmented
import studio.knit.ui.components.Sheet
import studio.knit.ui.components.Stepper
import studio.knit.ui.components.SwitchRow
import studio.knit.ui.components.pressable
import studio.knit.ui.platform.KeepScreenOn
import studio.knit.ui.theme.Knit
import studio.knit.util.newId
import studio.knit.util.nowMillis
import studio.knit.stats.dayKey
import studio.knit.stats.logRows
import kotlinx.coroutines.launch

/**
 * Knitting mode. One big button moves to the next row; everything else — the instruction, the
 * highlighted chart row, repeat tracking and extra counters — updates around it. The position is
 * saved on every tap, so the project resumes exactly where it was left.
 */
@Composable
fun KnitScreen(patternId: String) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val editor = remember(patternId) { app.editor(patternId) }
    val pattern by editor.state.collectAsState()
    val loaded by editor.loaded.collectAsState()
    val library by app.library.collectAsState()
    val settings by app.settings.collectAsState()
    val haptic = rememberHaptic()

    val p = pattern
    if (p == null) {
        FixedPage("Knit", onBack = nav::back) { if (loaded) EmptyState(Icons.Play, "Pattern not found", "It may have been deleted.") }
        return
    }
    // Starting to knit creates the progress record.
    LaunchedEffect(p.id) { if (p.progress == null) editor.update { it.copy(progress = startProgress(it, nowMillis())) } }
    val progress = p.progress ?: startProgress(p, nowMillis())
    val tracks = remember(p.sections, p.charts, progress.sizeIndex, library) { tracksOf(p, progress.sizeIndex, library) }
    KeepScreenOn(settings.keepScreenOn)

    var showNotes by remember { mutableStateOf(false) }
    var showSetup by remember { mutableStateOf(false) }
    var editingCounter by remember { mutableStateOf<Counter?>(null) }
    var jumping by remember { mutableStateOf(false) }
    var restarting by remember { mutableStateOf(false) }

    if (tracks.isEmpty()) {
        FixedPage(p.displayName, onBack = nav::back) {
            val pdf = p.files.firstOrNull()
            if (pdf != null) {
                EmptyState(Icons.Document, "Knit from the PDF", "This pattern is a PDF. Open it to read along, with a row counter.", Modifier.padding(top = 40.dp)) {
                    PrimaryButton("Open PDF", { nav.openPdf(p.id, pdf.id) }, icon = Icons.Document)
                }
            } else EmptyState(Icons.Rows, "Nothing to knit yet", "Add a chart, some written rows or a PDF to this pattern, then come back.", Modifier.padding(top = 40.dp))
        }
        return
    }
    val track = tracks.firstOrNull { it.id == progress.activeTrackId } ?: tracks.first()
    val pos = track.position(progress)

    fun update(transform: (Progress) -> Progress) = editor.update { it.copy(progress = transform(it.progress ?: startProgress(it, nowMillis()))) }
    val timer = rememberKnitTimer(editor)
    fun step(delta: Int) {
        val before = pos.done
        timer.touch()
        update { pr ->
            val now = nowMillis()
            val next = pr.step(track, delta, now)
            // Rows actually moved (none past the end or below zero) go into today's log.
            next.logRows(dayKey(now), (next.positions[track.id] ?: 0) - (pr.positions[track.id] ?: 0))
        }
        haptic(delta > 0 && !track.repeats && before + delta >= track.length)
    }

    androidx.compose.foundation.layout.Box(Modifier.knitActivity(timer)) {
    FixedPage(
        p.displayName, onBack = nav::back,
        actions = {
            IconBtn(Icons.Note, "Notes", { showNotes = true })
            MenuButton(listOfNotNull(
                MenuItem("Go to row…", Icons.Rows) { jumping = true },
                MenuItem("Size & options", Icons.Settings) { showSetup = true },
                p.files.firstOrNull { it.mime == "application/pdf" }?.let { pdf -> MenuItem("Open PDF", Icons.Book) { nav.openPdf(p.id, pdf.id) } },
                MenuItem("Open pattern", Icons.Document) { nav.openPattern(p.id) },
                MenuItem("Start over", Icons.Undo, destructive = true) { restarting = true },
            ))
        },
    ) {
        // Which part is being worked.
        if (tracks.size > 1) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = PagePadding, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (t in tracks) {
                    val done = !t.repeats && t.position(progress).finished
                    studio.knit.ui.components.Chip(t.title, t.id == track.id, { update { it.copy(activeTrackId = t.id) } },
                        leading = if (done) ({ KIcon(Icons.Check, if (t.id == track.id) Knit.colors.onAccent else Knit.colors.success, 15.dp) }) else null)
                }
            }
        } else Spacer(Modifier.height(10.dp))

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            CurrentRowCard(p, track, pos, progress)
            track.chart?.let { chart ->
                Box(Modifier.padding(horizontal = PagePadding).padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Knit.colors.chartPaper).padding(10.dp)) {
                    val aspect = (chart.cols + 3.2f) / (chart.rows + 1.6f)
                    ChartThumbnail(
                        chart, library, Modifier.fillMaxWidth().height((320f / aspect).coerceIn(90f, 300f).dp),
                        highlightRow = if (pos.finished) null else pos.chartRow, doneBelow = pos.chartRow, numbers = true,
                    )
                }
            }
            CountersRow(progress, onBump = { c, d -> update { pr -> pr.copy(counters = pr.counters.map { if (it.id == c.id) it.bump(d) else it }) }; haptic(false) },
                onEdit = { editingCounter = it },
                onAdd = { editingCounter = Counter(id = newId(), name = "Counter ${progress.counters.size + 1}") })
            Spacer(Modifier.height(12.dp))
        }

        // The controls stay in thumb reach at the bottom.
        Column(Modifier.fillMaxWidth().background(Knit.colors.surface).navigationBarsPadding().padding(horizontal = PagePadding, vertical = 12.dp)) {
            val fraction = if (track.repeats) (pos.rowIndex.toFloat() / track.length) else pos.done.toFloat() / track.length.coerceAtLeast(1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressBar(fraction, Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                Text(if (track.repeats) "${pos.rowIndex + 1} / ${track.length}" else "${pos.done} / ${track.length}", style = Knit.type.footnote, color = Knit.colors.secondaryLabel)
            }
            TodayLine(progress, timer)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(Knit.colors.fill).pressable(enabled = pos.done > 0) { step(-1) }
                        .semantics { contentDescription = "Back one row" },
                    contentAlignment = Alignment.Center,
                ) { KIcon(Icons.Undo, Knit.colors.label, 26.dp) }
                val nextFinished = pos.finished
                Box(
                    Modifier.weight(1f).height(64.dp).clip(RoundedCornerShape(18.dp)).background(if (nextFinished) Knit.colors.fill else Knit.colors.accent)
                        .pressable(enabled = !nextFinished) { step(1) }.semantics { contentDescription = "Row done, next row" },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        KIcon(Icons.Check, if (nextFinished) Knit.colors.secondaryLabel else Knit.colors.onAccent, 24.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(if (nextFinished) "Section finished" else if (track.section?.construction == Construction.ROUND || track.chart?.construction == Construction.ROUND && track.section == null) "Round done" else "Row done",
                            style = Knit.type.title3, color = if (nextFinished) Knit.colors.secondaryLabel else Knit.colors.onAccent)
                    }
                }
            }
            if (pos.finished) {
                val next = tracks.firstOrNull { !it.repeats && !it.position(progress).finished }
                Spacer(Modifier.height(10.dp))
                if (next != null) {
                    PrimaryButton("Continue with “${next.title}”", { update { it.copy(activeTrackId = next.id) } }, Modifier.fillMaxWidth(), icon = Icons.ChevronRight)
                } else if (progress.finishedAt == null && isComplete(p, library)) {
                    PrimaryButton("Mark project as finished", { update { it.copy(finishedAt = nowMillis()) }; app.message("Finished — well done!") }, Modifier.fillMaxWidth(), icon = Icons.CheckCircle)
                }
            }
        }
    }

    }
    if (showNotes) NotesSheet(progress, pos, track, ::update) { showNotes = false }
    if (showSetup) SetupSheet(p, progress, ::update) { showSetup = false }
    editingCounter?.let { c ->
        CounterSheet(c, isNew = progress.counters.none { it.id == c.id },
            onSave = { saved -> update { pr -> pr.copy(counters = if (pr.counters.any { it.id == saved.id }) pr.counters.map { if (it.id == saved.id) saved else it } else pr.counters + saved) } },
            onDelete = { update { pr -> pr.copy(counters = pr.counters.filterNot { it.id == c.id }) } },
            onDismiss = { editingCounter = null })
    }
    if (jumping) {
        PromptDialog("Go to row", (pos.rowIndex + 1).toString(), "Go", { text ->
            text.toIntOrNull()?.let { n -> update { it.setPosition(track, (n - 1).coerceAtLeast(0) + (if (track.repeats) pos.pass * track.length else 0), nowMillis()) } }
        }, { jumping = false }, message = "Rows 1–${track.length} of “${track.title}”. The row you enter becomes the next row to knit.", keyboard = androidx.compose.ui.text.input.KeyboardType.Number)
    }
    if (restarting) {
        ConfirmDialog("Start over?", "Row positions and counters are reset to zero. Your notes are kept.", "Start over",
            { update { it.copy(positions = emptyMap(), counters = it.counters.map { c -> c.copy(value = 0, laps = 0) }, finishedAt = null) } }, { restarting = false }, destructive = true)
    }
}

@Composable
private fun CurrentRowCard(p: Pattern, track: Track, pos: Position, progress: Progress) {
    val round = (track.section?.construction ?: track.chart?.construction) == Construction.ROUND
    Column(
        Modifier.padding(horizontal = PagePadding).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Knit.colors.surface).padding(18.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        AnimatedContent(
            pos.done,
            transitionSpec = {
                if (targetState > initialState) (slideInVertically { it / 3 } + fadeIn()) togetherWith (slideOutVertically { -it / 3 } + fadeOut())
                else (slideInVertically { -it / 3 } + fadeIn()) togetherWith (slideOutVertically { it / 3 } + fadeOut())
            },
        ) { _ ->
            Column {
                val row = pos.expanded
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(if (pos.finished) "Done" else (pos.rowIndex + 1).toString(), style = Knit.type.counter, color = Knit.colors.label)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.padding(bottom = 10.dp)) {
                        Text(
                            when {
                                pos.finished -> track.title
                                round -> "Round"
                                row != null -> if (row.side == Side.RS) "Row · right side" else "Row · wrong side"
                                else -> "Row"
                            },
                            style = Knit.type.headline, color = Knit.colors.secondaryLabel,
                        )
                        val detail = when {
                            pos.finished -> "All ${track.length} rows worked"
                            track.repeats -> "Repeat ${pos.pass + 1} of the chart"
                            row != null && row.repeatTotal > 0 -> "Repeat ${row.repeatIteration} of ${row.repeatTotal}"
                            else -> "of ${track.length}"
                        }
                        Text(detail, style = Knit.type.subhead, color = Knit.colors.accent)
                    }
                }
                if (!pos.finished && row != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(row.source.text.ifBlank { "—" }, style = Knit.type.instruction.copy(fontSize = Knit.type.instruction.fontSize * 1.1f), color = Knit.colors.label)
                    val e = row.eval
                    if (e.produced != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (e.delta == 0) "${e.produced} stitches" else "${e.consumed} → ${e.produced} stitches",
                            style = Knit.type.subhead, color = Knit.colors.secondaryLabel,
                        )
                    }
                    if (row.source.note.isNotBlank()) Text(row.source.note, style = Knit.type.subhead.copy(fontStyle = FontStyle.Italic), color = Knit.colors.secondaryLabel)
                    // Notes written between rows show up when the knitter reaches them.
                    val section = track.section
                    if (section != null && row.viaRepeat == null) {
                        val at = section.rows.indexOfFirst { it.id == row.source.id }
                        val note = section.rows.getOrNull(at - 1)?.takeIf { it.kind == RowKind.NOTE && it.text.isNotBlank() }
                        if (note != null) {
                            Spacer(Modifier.height(10.dp))
                            Row(Modifier.clip(RoundedCornerShape(10.dp)).background(Knit.colors.accentSoft).padding(10.dp)) {
                                KIcon(Icons.Note, Knit.colors.accent, 17.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(note.text, style = Knit.type.subhead, color = Knit.colors.label)
                            }
                        }
                    }
                    val next = track.eval?.rows?.getOrNull(pos.rowIndex + 1)
                    if (next != null) {
                        Spacer(Modifier.height(12.dp))
                        Text("Next: ${next.source.text}", style = Knit.type.footnote, color = Knit.colors.tertiaryLabel, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                } else if (!pos.finished) {
                    Spacer(Modifier.height(6.dp))
                    Text("Follow the highlighted chart row.", style = Knit.type.callout, color = Knit.colors.secondaryLabel)
                }
            }
        }
        if (p.sizes.size > 1) {
            Spacer(Modifier.height(10.dp))
            Text("Size ${p.sizes.getOrElse(progress.sizeIndex) { p.sizes.first() }}", style = Knit.type.footnote.copy(fontWeight = FontWeight.SemiBold), color = Knit.colors.secondaryLabel)
        }
    }
}

@Composable
private fun CountersRow(progress: Progress, onBump: (Counter, Int) -> Unit, onEdit: (Counter) -> Unit, onAdd: () -> Unit) {
    Row(Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState()).padding(horizontal = PagePadding), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (c in progress.counters) {
            Column(Modifier.width(150.dp).clip(RoundedCornerShape(14.dp)).background(Knit.colors.surface).padding(top = 10.dp, bottom = 6.dp)) {
                Row(Modifier.padding(horizontal = 12.dp).pressable { onEdit(c) }, verticalAlignment = Alignment.CenterVertically) {
                    Text(c.name, style = Knit.type.footnote, color = Knit.colors.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (c.linked) KIcon(Icons.Repeat, Knit.colors.tertiaryLabel, 13.dp, description = "Follows the row counter")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBtn(Icons.Minus, "Decrease ${c.name}", { onBump(c, -1) }, tint = Knit.colors.secondaryLabel, size = 18.dp)
                    Column(Modifier.weight(1f).pressable { onBump(c, 1) }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(c.value.toString() + (c.resetAt?.let { " / $it" } ?: c.target?.let { " / $it" } ?: ""), style = Knit.type.title2, color = Knit.colors.label, textAlign = TextAlign.Center)
                        if (c.resetAt != null) Text("lap ${c.laps + 1}", style = Knit.type.caption, color = Knit.colors.accent)
                    }
                    IconBtn(Icons.Plus, "Increase ${c.name}", { onBump(c, 1) }, tint = Knit.colors.accent, size = 20.dp)
                }
            }
        }
        Column(
            Modifier.width(if (progress.counters.isEmpty()) 190.dp else 96.dp).heightIn(min = 84.dp).clip(RoundedCornerShape(14.dp)).background(Knit.colors.surface).pressable(onClick = onAdd).padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            KIcon(Icons.Plus, Knit.colors.accent, 22.dp)
            Spacer(Modifier.height(4.dp))
            Text(if (progress.counters.isEmpty()) "Add a counter" else "Counter", style = Knit.type.footnote, color = Knit.colors.accent)
            if (progress.counters.isEmpty()) Text("for repeats, decreases…", style = Knit.type.caption, color = Knit.colors.secondaryLabel)
        }
    }
}

@Composable
private fun CounterSheet(initial: Counter, isNew: Boolean, onSave: (Counter) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var c by remember { mutableStateOf(initial) }
    var mode by remember { mutableStateOf(if (initial.resetAt != null) 1 else if (initial.target != null) 2 else 0) }
    Sheet(if (isNew) "New counter" else "Counter", onDismiss, doneLabel = "Save", onDone = {
        onSave(c.copy(resetAt = if (mode == 1) (c.resetAt ?: 4) else null, target = if (mode == 2) (c.target ?: 10) else null))
    }) {
        BoundField(c.name, { c = c.copy(name = it) }, Modifier.padding(horizontal = PagePadding).padding(top = 8.dp).fillMaxWidth(), "Name", "e.g. Cable row, sleeve decreases")
        SectionHeader("Counts")
        Segmented(listOf("Freely", "In a cycle", "To a target"), mode, { mode = it }, Modifier.padding(horizontal = PagePadding).fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Group {
            Stepper(c.value, { c = c.copy(value = it) }, 0..9999, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), "Current value")
            if (mode == 1) { RowDivider(); Stepper(c.resetAt ?: 4, { c = c.copy(resetAt = it) }, 2..999, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), "Start again after") }
            if (mode == 2) { RowDivider(); Stepper(c.target ?: 10, { c = c.copy(target = it) }, 1..9999, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), "Target") }
            RowDivider()
            SwitchRow("Follow the row counter", c.linked, { c = c.copy(linked = it) }, "Counts up each time you finish a row")
        }
        SectionFooter(when (mode) {
            1 -> "Good for “cable every 4th row”: the counter runs 0–${(c.resetAt ?: 4) - 1} and shows which lap you are on."
            2 -> "Good for “decrease 10 times”."
            else -> "Tap the number to add one."
        })
        if (!isNew) {
            Spacer(Modifier.height(20.dp))
            PrimaryButton("Delete counter", { onDelete(); onDismiss() }, Modifier.padding(horizontal = PagePadding).fillMaxWidth(), destructive = true)
        }
    }
}

@Composable
private fun NotesSheet(progress: Progress, pos: Position, track: Track, update: ((Progress) -> Progress) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val now = remember(progress.notes.size) { nowMillis() }
    Sheet("Project notes", onDismiss) {
        Row(Modifier.padding(horizontal = PagePadding).padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
            Field(text, { text = it }, Modifier.weight(1f), placeholder = "What happened on this row?", singleLine = false)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(46.dp).clip(CircleShape).background(if (text.isBlank()) Knit.colors.fill else Knit.colors.accent).pressable(enabled = text.isNotBlank()) {
                val context = "${track.title} · row ${pos.rowIndex + 1}"
                update { it.copy(notes = listOf(ProjectNote(newId(), text.trim(), nowMillis(), context)) + it.notes) }
                text = ""
            }.semantics { contentDescription = "Add note" }, contentAlignment = Alignment.Center) { KIcon(Icons.Plus, if (text.isBlank()) Knit.colors.secondaryLabel else Knit.colors.onAccent, 22.dp) }
        }
        if (progress.notes.isEmpty()) {
            SectionFooter("Notes are stamped with the row you were on, and travel with the pattern file.")
        } else {
            Spacer(Modifier.height(14.dp))
            Group {
                progress.notes.forEachIndexed { i, n ->
                    if (i > 0) RowDivider()
                    ListRow(n.text, subtitle = "${n.context}  ·  ${formatDate(n.createdAt, now)}", trailing = {
                        IconBtn(Icons.Trash, "Delete note", { update { it.copy(notes = it.notes.filterNot { x -> x.id == n.id }) } }, tint = Knit.colors.tertiaryLabel, size = 18.dp)
                    })
                }
            }
        }
    }
}

@Composable
private fun SetupSheet(p: Pattern, progress: Progress, update: ((Progress) -> Progress) -> Unit, onDismiss: () -> Unit) {
    val app = LocalApp.current
    val settings by app.settings.collectAsState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val now = remember { nowMillis() }
    Sheet("Knitting options", onDismiss) {
        if (p.sizes.size > 1) {
            SectionHeader("Size you are knitting")
            Segmented(p.sizes, progress.sizeIndex.coerceIn(0, p.sizes.size - 1), { i -> update { it.copy(sizeIndex = i) } }, Modifier.padding(horizontal = PagePadding).fillMaxWidth())
            SectionFooter("Row repeats and stitch counts follow this size.")
        }
        SectionHeader("This project")
        Group {
            ListRow("Started", value = formatDate(progress.startedAt, now))
            RowDivider()
            ListRow("Last knitted", value = formatDate(progress.lastKnitAt, now))
            progress.finishedAt?.let { RowDivider(); ListRow("Finished", value = formatDate(it, now)) }
        }
        SectionHeader("Screen")
        Group {
            SwitchRow("Keep screen on", settings.keepScreenOn, { v -> scope.launch { app.repo.updateSettings { it.copy(keepScreenOn = v) } } })
            RowDivider()
            SwitchRow("Haptic feedback", settings.haptics, { v -> scope.launch { app.repo.updateSettings { it.copy(haptics = v) } } })
        }
    }
}

/** "Today 42 min · 18 rows", and whether the timer is paused for inactivity. */
@Composable
fun TodayLine(progress: Progress, timer: KnitTimer) {
    val today = progress.days[dayKey(nowMillis())]
    val text = buildString {
        append("Today ")
        append(studio.knit.stats.formatDuration(today?.seconds ?: 0))
        append("  ·  ${today?.rows ?: 0} ${if (today?.rows == 1) "row" else "rows"}")
        if (timer.paused) append("  ·  timer paused, tap to resume")
    }
    Text(text, style = Knit.type.footnote, color = if (timer.paused) Knit.colors.warning else Knit.colors.secondaryLabel,
        modifier = Modifier.padding(top = 6.dp))
}
