package studio.knit.ui.written

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import studio.knit.convert.ConversionNote
import studio.knit.convert.chartToSection
import studio.knit.convert.sectionToChart
import studio.knit.model.Construction
import studio.knit.model.Pattern
import studio.knit.model.Row as KnitRow
import studio.knit.model.RowKind
import studio.knit.model.Section
import studio.knit.model.Side
import studio.knit.model.SizedInt
import studio.knit.stitch.StitchLibrary
import studio.knit.ui.app.LocalApp
import studio.knit.ui.app.LocalNav
import studio.knit.ui.app.PatternEditor
import studio.knit.ui.components.BoundField
import studio.knit.ui.components.ConfirmDialog
import studio.knit.ui.components.EmptyState
import studio.knit.ui.components.FixedPage
import studio.knit.ui.components.Group
import studio.knit.ui.components.IconBtn
import studio.knit.ui.components.Icons
import studio.knit.ui.components.KIcon
import studio.knit.ui.components.ListRow
import studio.knit.ui.components.MenuButton
import studio.knit.ui.components.MenuItem
import studio.knit.ui.components.PagePadding
import studio.knit.ui.components.PrimaryButton
import studio.knit.ui.components.RowDivider
import studio.knit.ui.components.SectionFooter
import studio.knit.ui.components.SectionHeader
import studio.knit.ui.components.Segmented
import studio.knit.ui.components.Sheet
import studio.knit.ui.components.SoftButton
import studio.knit.ui.components.Stepper
import studio.knit.ui.components.pressable
import studio.knit.ui.theme.Knit
import studio.knit.util.newId
import studio.knit.validate.Severity
import studio.knit.written.RowParser
import studio.knit.written.RowPrinter
import studio.knit.written.SectionEval
import studio.knit.written.evaluateSection

/** Tokens offered above the keyboard. `|` marks where the caret lands after insertion. */
private val QUICK_TOKENS = listOf(
    "k", "p", "yo", "k2tog", "ssk", "*", "; rep from * to end", "; rep from * to last | sts", "knit to end", "purl to end",
    "(|) 2 times", "sl1", "m1l", "m1r", "kfb", "cdd", "C4F", "C4B", "pm", "sm",
)

/**
 * The written-pattern editor for one section. Every row is its own editable item; the stitch
 * arithmetic is re-run on each keystroke and shown beside the row.
 */
@Composable
fun SectionEditorScreen(patternId: String, sectionId: String, focusRowId: String) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val editor = remember(patternId) { app.editor(patternId) }
    val pattern by editor.state.collectAsState()
    val loaded by editor.loaded.collectAsState()
    val library by app.library.collectAsState()
    val settings by app.settings.collectAsState()

    val p = pattern
    val section = p?.sections?.firstOrNull { it.id == sectionId }
    if (p == null || section == null) {
        FixedPage("Rows", onBack = nav::back) { if (loaded) EmptyState(Icons.Rows, "Section not found", "It may have been deleted.") }
        return
    }

    var sizeIndex by remember { mutableIntStateOf(p.progress?.sizeIndex?.coerceIn(0, p.sizeCount - 1) ?: 0) }
    if (sizeIndex >= p.sizeCount) sizeIndex = 0
    val eval = remember(section, sizeIndex, library) { evaluateSection(section, sizeIndex, library) }

    val fields = remember(sectionId) { mutableStateMapOf<String, TextFieldValue>() }
    var focused by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf<Pair<String, List<ConversionNote>>?>(null) }
    var confirm by remember { mutableStateOf<Triple<String, String, () -> Unit>?>(null) }
    val listState = rememberLazyListState()

    fun updateSection(transform: (Section) -> Section) = editor.update { pat -> pat.copy(sections = pat.sections.map { if (it.id == sectionId) transform(it) else it }) }
    fun updateRow(rowId: String, transform: (KnitRow) -> KnitRow) = updateSection { s -> s.copy(rows = s.rows.map { if (it.id == rowId) transform(it) else it }, generated = false) }
    fun addRow(row: KnitRow, afterId: String? = null) = updateSection { s ->
        val at = afterId?.let { id -> s.rows.indexOfFirst { it.id == id } + 1 }?.takeIf { it > 0 } ?: s.rows.size
        s.copy(rows = s.rows.toMutableList().apply { add(at, row) }, generated = false)
    }

    LaunchedEffect(focusRowId) {
        val index = section.rows.indexOfFirst { it.id == focusRowId }
        if (index >= 0) { listState.scrollToItem(index + 1); focused = focusRowId }
    }

    fun setText(rowId: String, value: TextFieldValue) {
        val old = fields[rowId]?.text
        fields[rowId] = value
        if (old != value.text) updateRow(rowId) { it.copy(text = value.text) }
    }

    fun insertToken(token: String) {
        val rowId = focused ?: return
        val row = section.rows.firstOrNull { it.id == rowId } ?: return
        if (row.kind != RowKind.INSTRUCTION) return
        val current = fields[rowId] ?: TextFieldValue(row.text, TextRange(row.text.length))
        val start = current.selection.min
        val end = current.selection.max
        val before = current.text.substring(0, start)
        val after = current.text.substring(end)
        // Add the separator a knitter would type between two instructions.
        val trimmed = before.trimEnd()
        val tail = trimmed.lastOrNull()
        val (base, prefix) = when {
            token.startsWith(";") || token.startsWith(")") -> trimmed to ""
            tail == null -> "" to ""
            tail in "([*" -> trimmed to ""
            tail in ",;:" -> trimmed to " "
            else -> trimmed to ", "
        }
        val caretMark = token.indexOf('|')
        val clean = token.replace("|", "")
        val text = base + prefix + clean + after
        val caret = base.length + prefix.length + (if (caretMark >= 0) caretMark else clean.length)
        setText(rowId, TextFieldValue(text, TextRange(caret)))
    }

    FixedPage(
        section.title.ifBlank { "Section" }, onBack = nav::back,
        actions = {
            IconBtn(Icons.Settings, "Section settings", { showSettings = true })
            MenuButton(sectionMenu(p, section, library, editor, settings.ukTerms, eval,
                onNotes = { title, list -> notes = title to list },
                onConfirm = { title, message, action -> confirm = Triple(title, message, action) },
                onMessage = app::message, onOpenChart = { nav.openChart(p.id, it) }, sizeIndex = sizeIndex,
                clearFields = { fields.clear() }))
        },
    ) {
        Column(Modifier.weight(1f).imePadding()) {
            LazyColumn(Modifier.weight(1f), state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
                item(key = "__header") {
                    SectionSummary(p, section, eval, sizeIndex, onSize = { sizeIndex = it }, onCastOn = { v -> updateSection { it.copy(castOn = v) } }, onMarkers = { m -> updateSection { it.copy(markers = m) } })
                }
                itemsIndexed(section.rows, key = { _, r -> r.id }) { index, row ->
                    RowCard(
                        section, row, index, eval, library,
                        value = fields[row.id] ?: TextFieldValue(row.text, TextRange(row.text.length)),
                        isFocused = focused == row.id,
                        onValue = { setText(row.id, it) },
                        onFocus = { if (it) focused = row.id else if (focused == row.id) focused = null },
                        onUpdate = { t -> updateRow(row.id, t) },
                        onMove = { delta ->
                            updateSection { s ->
                                val list = s.rows.toMutableList()
                                val to = (index + delta).coerceIn(0, list.size - 1)
                                list.add(to, list.removeAt(index))
                                s.copy(rows = list, generated = false)
                            }
                        },
                        onDuplicate = { addRow(row.copy(id = newId()), row.id) },
                        onInsertBelow = { addRow(KnitRow(id = newId()), row.id) },
                        onDelete = { fields.remove(row.id); updateSection { s -> s.copy(rows = s.rows.filterNot { it.id == row.id }, generated = false) } },
                    )
                }
                item(key = "__add") {
                    Row(Modifier.padding(horizontal = PagePadding).padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val word = if (section.construction == Construction.ROUND) "Round" else "Row"
                        SoftButton(word, { val r = KnitRow(id = newId()); addRow(r); focused = r.id }, Modifier.weight(1f), icon = Icons.Plus, compact = true)
                        SoftButton("Repeat", {
                            val count = eval.rows.size
                            addRow(KnitRow(id = newId(), kind = RowKind.REPEAT_ROWS, repeatFrom = 1, repeatTo = count.coerceAtLeast(1), repeatTimes = SizedInt(1)))
                        }, Modifier.weight(1f), icon = Icons.Repeat, compact = true, enabled = eval.rows.isNotEmpty())
                        SoftButton("Note", { addRow(KnitRow(id = newId(), kind = RowKind.NOTE)) }, Modifier.weight(1f), icon = Icons.Note, compact = true)
                    }
                    SectionFooter("Write rows the way patterns do: K2, *p2, k2; rep from * to end. Counts for sizes go in brackets: k10 (12, 14).")
                }
            }
            // Quick-insert bar, shown while a row is being edited.
            val focusedRow = section.rows.firstOrNull { it.id == focused }
            if (focusedRow != null && focusedRow.kind == RowKind.INSTRUCTION) {
                Column(Modifier.fillMaxWidth().background(Knit.colors.surface)) {
                    Box(Modifier.fillMaxWidth().height(0.6.dp).background(Knit.colors.separator))
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        for (token in QUICK_TOKENS) {
                            val label = token.replace("|", "…").trim().removePrefix("; ")
                            Box(
                                Modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(9.dp)).background(Knit.colors.fill).pressable { insertToken(token) }
                                    .padding(horizontal = 12.dp).semantics { contentDescription = "Insert $label" },
                                contentAlignment = Alignment.Center,
                            ) { Text(label, style = Knit.type.subhead.copy(fontWeight = FontWeight.Medium), color = Knit.colors.label, maxLines = 1) }
                        }
                    }
                }
            } else Spacer(Modifier.navigationBarsPadding())
        }
    }

    if (showSettings) SectionSettingsSheet(p, section, { t -> updateSection(t) }) { showSettings = false }
    notes?.let { (title, list) ->
        Sheet(title, { notes = null }) {
            Spacer(Modifier.height(8.dp))
            Group {
                list.forEachIndexed { i, n ->
                    if (i > 0) RowDivider(50.dp)
                    val (icon, color) = when (n.severity) {
                        Severity.ERROR -> Icons.Error to Knit.colors.error
                        Severity.WARNING -> Icons.Warning to Knit.colors.warning
                        Severity.INFO -> Icons.Info to Knit.colors.info
                    }
                    ListRow(n.message, leading = { KIcon(icon, color, 22.dp) })
                }
            }
        }
    }
    confirm?.let { (title, message, action) -> ConfirmDialog(title, message, "Replace", action, { confirm = null }, destructive = true) }
}

private fun sectionMenu(
    p: Pattern, section: Section, library: StitchLibrary, editor: PatternEditor, uk: Boolean, eval: SectionEval, sizeIndex: Int,
    onNotes: (String, List<ConversionNote>) -> Unit, onConfirm: (String, String, () -> Unit) -> Unit,
    onMessage: (String) -> Unit, onOpenChart: (String) -> Unit, clearFields: () -> Unit,
): List<MenuItem> {
    val chart = p.charts.firstOrNull { it.id == section.chartId }
    fun update(transform: (Section) -> Section) = editor.update { pat -> pat.copy(sections = pat.sections.map { if (it.id == section.id) transform(it) else it }) }

    fun drawChart() {
        val result = sectionToChart(section, sizeIndex, library, ::newId)
        val made = result.chart
        if (made == null) { onNotes("Could not draw a chart", result.notes); return }
        val apply = {
            editor.update { pat ->
                val id = chart?.id ?: made.id
                val next = made.copy(id = id, name = chart?.name ?: made.name)
                pat.copy(
                    charts = if (chart != null) pat.charts.map { if (it.id == id) next else it } else pat.charts + next,
                    sections = pat.sections.map { if (it.id == section.id) it.copy(chartId = id, generated = false) else it },
                )
            }
            if (result.notes.any { it.severity != Severity.INFO }) onNotes("Chart drawn — please review", result.notes)
            else onMessage(result.notes.firstOrNull()?.message ?: "Chart drawn from these rows")
        }
        if (chart != null) onConfirm("Replace “${chart.name}”?", "The linked chart will be redrawn from these rows. Anything drawn by hand on it is lost.") { apply() } else apply()
    }

    fun rewriteFromChart() {
        val c = chart ?: return
        val apply = {
            val (fresh, n) = chartToSection(c, library, ::newId, uk)
            clearFields()
            update { it.copy(rows = fresh.rows, construction = fresh.construction, firstRowSide = fresh.firstRowSide, generated = true) }
            if (n.isNotEmpty()) onNotes("Rows rewritten — please review", n) else onMessage("Rows rewritten from “${c.name}”")
        }
        if (section.generated) apply() else onConfirm("Replace these rows?", "The rows will be written again from “${c.name}”. Your edits to them are lost.") { apply() }
    }

    fun tidy() {
        val parser = RowParser(library)
        val printer = RowPrinter(uk)
        clearFields()
        update { s ->
            s.copy(rows = s.rows.map { row ->
                if (row.kind != RowKind.INSTRUCTION || row.text.isBlank()) row
                else {
                    val parsed = parser.parse(row.text)
                    // Rows the parser does not fully understand are left exactly as typed.
                    if (parsed.errors.isEmpty()) row.copy(text = printer.print(parsed.nodes, parsed.stated)) else row
                }
            })
        }
        onMessage("Wording tidied")
    }

    return listOfNotNull(
        MenuItem(if (chart == null) "Draw chart from rows" else "Redraw chart from rows", Icons.Grid, enabled = eval.rows.isNotEmpty()) { drawChart() },
        if (chart != null) MenuItem("Open chart “${chart.name}”", Icons.ChevronRight) { onOpenChart(chart.id) } else null,
        if (chart != null) MenuItem("Rewrite rows from chart", Icons.Rows) { rewriteFromChart() } else null,
        MenuItem(if (uk) "Tidy wording (UK terms)" else "Tidy wording", Icons.Sparkle) { tidy() },
    )
}

@Composable
private fun SectionSummary(
    p: Pattern, section: Section, eval: SectionEval, sizeIndex: Int, onSize: (Int) -> Unit, onCastOn: (SizedInt?) -> Unit,
    onMarkers: (List<Int>) -> Unit,
) {
    val chart = p.charts.firstOrNull { it.id == section.chartId }
    Column(Modifier.padding(horizontal = PagePadding).padding(top = 12.dp)) {
        if (p.sizes.size > 1) {
            Segmented(p.sizes, sizeIndex, onSize, Modifier.fillMaxWidth())
            Spacer(Modifier.height(4.dp))
            Text("Stitch counts are shown for size ${p.sizes[sizeIndex]}.", style = Knit.type.footnote, color = Knit.colors.secondaryLabel, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BoundField(
                section.castOn?.toString() ?: "",
                { text -> onCastOn(if (text.isBlank()) null else RowParser.parseSized(text)) },
                Modifier.weight(1f), "Stitches before row 1", if (p.sizes.size > 1) "e.g. 22 (26, 30)" else "e.g. 22",
                suffix = "sts", keyboard = if (p.sizes.size > 1) KeyboardType.Text else KeyboardType.Number, capitalization = KeyboardCapitalization.None,
            )
            Column(Modifier.weight(1f).padding(bottom = 10.dp)) {
                val multiple = eval.rows.firstNotNullOfOrNull { r -> r.eval.multiple?.let { it to (r.eval.extra ?: 0) } }
                val line = when {
                    eval.startCount != null && eval.endCount != null -> "${eval.rows.size} ${if (section.construction == Construction.ROUND) "rounds" else "rows"}  ·  ends with ${eval.endCount} sts"
                    multiple != null -> "Multiple of ${multiple.first}" + if (multiple.second > 0) " + ${multiple.second}" else ""
                    else -> "${eval.rows.size} ${if (section.construction == Construction.ROUND) "rounds" else "rows"}"
                }
                Text(line, style = Knit.type.subhead, color = Knit.colors.secondaryLabel)
            }
        }
        val usesMarkers = section.markers.isNotEmpty() || section.rows.any { Regex("""\b(marker|sm|pm|m\d)\b""", RegexOption.IGNORE_CASE).containsMatchIn(it.text) }
        if (usesMarkers) {
            val total = section.castOn?.forSize(sizeIndex)
            BoundField(
                markersToGroups(section.markers, total),
                { text -> onMarkers(groupsToMarkers(text, total)) },
                Modifier.fillMaxWidth(), "Markers after cast-on: stitches between them", "e.g. 2, 2, 2",
                keyboard = KeyboardType.Text, capitalization = KeyboardCapitalization.None,
            )
            Text(
                "“2, 2, 2” means 2 sts, marker, 2 sts, marker, 2 sts. “Knit to marker” and “slip marker” are then checked.",
                style = Knit.type.footnote, color = Knit.colors.secondaryLabel, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            )
        }
        if (chart != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                if (section.generated) "Written from chart “${chart.name}” — it follows the chart until you edit a row."
                else "Linked to chart “${chart.name}”. Differences are listed under Check.",
                style = Knit.type.footnote, color = Knit.colors.info, modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

@Composable
private fun RowCard(
    section: Section, row: KnitRow, index: Int, eval: SectionEval, library: StitchLibrary,
    value: TextFieldValue, isFocused: Boolean, onValue: (TextFieldValue) -> Unit, onFocus: (Boolean) -> Unit,
    onUpdate: ((KnitRow) -> KnitRow) -> Unit, onMove: (Int) -> Unit, onDuplicate: () -> Unit, onInsertBelow: () -> Unit, onDelete: () -> Unit,
) {
    val round = section.construction == Construction.ROUND
    val word = if (round) "Rnd" else "Row"
    val menu = listOf(
        MenuItem("Insert ${word.lowercase()} below", Icons.Plus, onClick = onInsertBelow),
        MenuItem("Duplicate", Icons.Copy, onClick = onDuplicate),
        MenuItem("Move up", enabled = index > 0) { onMove(-1) },
        MenuItem("Move down", enabled = index < section.rows.size - 1) { onMove(1) },
        MenuItem("Delete", Icons.Trash, destructive = true, onClick = onDelete),
    )
    val issues = eval.issues.filter { it.rowId == row.id }
    val hasError = issues.any { it.severity == Severity.ERROR }
    val border = when {
        hasError -> Knit.colors.error.copy(alpha = 0.7f)
        isFocused -> Knit.colors.accent
        else -> Color.Transparent
    }
    Column(
        Modifier.padding(horizontal = PagePadding).padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Knit.colors.surface)
            .border(if (border == Color.Transparent) 0.dp else 1.5.dp, border, RoundedCornerShape(14.dp)).padding(start = 14.dp, end = 2.dp, bottom = 12.dp),
    ) {
        when (row.kind) {
            RowKind.INSTRUCTION -> {
                val expanded = eval.firstFor(row.id)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val label = expanded?.let { if (round) "Rnd ${it.number}" else "Row ${it.number} · ${if (it.side == Side.RS) "RS" else "WS"}" } ?: word
                    Text(label, style = Knit.type.footnote.copy(fontWeight = FontWeight.SemiBold), color = Knit.colors.accent, modifier = Modifier.weight(1f))
                    val e = expanded?.eval
                    if (e?.produced != null && e.consumed != null && value.text.isNotBlank()) {
                        val delta = e.produced!! - e.consumed!!
                        val text = if (delta == 0) "${e.produced} sts" else "${e.consumed} → ${e.produced} sts"
                        Text(text, style = Knit.type.footnote, color = if (hasError) Knit.colors.error else Knit.colors.secondaryLabel)
                    }
                    MenuButton(menu, tint = Knit.colors.tertiaryLabel, description = "Row options")
                }
                Box(Modifier.padding(end = 12.dp)) {
                    if (value.text.isEmpty()) Text("K2, *p2, k2; rep from * to end", style = Knit.type.instruction, color = Knit.colors.tertiaryLabel)
                    BasicTextField(
                        value, onValue,
                        Modifier.fillMaxWidth().onFocusChanged { onFocus(it.isFocused) }.semantics { contentDescription = "Instructions" },
                        textStyle = Knit.type.instruction.copy(color = Knit.colors.label),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii),
                        cursorBrush = SolidColor(Knit.colors.accent),
                    )
                }
                for (issue in issues) {
                    val color = when (issue.severity) { Severity.ERROR -> Knit.colors.error; Severity.WARNING -> Knit.colors.warning; Severity.INFO -> Knit.colors.secondaryLabel }
                    Row(Modifier.padding(top = 6.dp, end = 12.dp), verticalAlignment = Alignment.Top) {
                        KIcon(when (issue.severity) { Severity.ERROR -> Icons.Error; Severity.WARNING -> Icons.Warning; Severity.INFO -> Icons.Info }, color, 15.dp, Modifier.padding(top = 2.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(issue.message, style = Knit.type.footnote, color = color)
                    }
                }
            }

            RowKind.REPEAT_ROWS -> {
                val made = eval.rows.filter { it.viaRepeat?.id == row.id }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KIcon(Icons.Repeat, Knit.colors.accent, 17.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("Repeat ${word.lowercase()}s", style = Knit.type.footnote.copy(fontWeight = FontWeight.SemiBold), color = Knit.colors.accent, modifier = Modifier.weight(1f))
                    if (made.isNotEmpty()) Text("${word}s ${made.first().number}–${made.last().number}", style = Knit.type.footnote, color = Knit.colors.secondaryLabel)
                    MenuButton(menu.drop(1), tint = Knit.colors.tertiaryLabel, description = "Repeat options")
                }
                val max = eval.rows.count { it.viaRepeat == null }.coerceAtLeast(1)
                Column(Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Stepper(row.repeatFrom, { v -> onUpdate { it.copy(repeatFrom = v, repeatTo = maxOf(it.repeatTo, v)) } }, 1..max.coerceAtLeast(row.repeatFrom), Modifier.fillMaxWidth(), "From ${word.lowercase()}")
                    Stepper(row.repeatTo, { v -> onUpdate { it.copy(repeatTo = v, repeatFrom = minOf(it.repeatFrom, v)) } }, 1..max.coerceAtLeast(row.repeatTo), Modifier.fillMaxWidth(), "To ${word.lowercase()}")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("More times", style = Knit.type.body, color = Knit.colors.label, modifier = Modifier.weight(1f))
                        BoundField(row.repeatTimes.toString(), { t -> RowParser.parseSized(t)?.let { v -> onUpdate { it.copy(repeatTimes = v) } } },
                            Modifier.width(132.dp), placeholder = "1", keyboard = KeyboardType.Text, capitalization = KeyboardCapitalization.None)
                    }
                }
                for (issue in issues) {
                    Text(issue.message, style = Knit.type.footnote, color = if (issue.severity == Severity.ERROR) Knit.colors.error else Knit.colors.warning, modifier = Modifier.padding(top = 6.dp, end = 12.dp))
                }
            }

            RowKind.NOTE -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KIcon(Icons.Note, Knit.colors.secondaryLabel, 16.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("Note", style = Knit.type.footnote.copy(fontWeight = FontWeight.SemiBold), color = Knit.colors.secondaryLabel, modifier = Modifier.weight(1f))
                    MenuButton(menu.drop(1), tint = Knit.colors.tertiaryLabel, description = "Note options")
                }
                Box(Modifier.padding(end = 12.dp)) {
                    if (value.text.isEmpty()) Text("e.g. Change to larger needles", style = Knit.type.body.copy(fontStyle = FontStyle.Italic), color = Knit.colors.tertiaryLabel)
                    BasicTextField(
                        value, onValue, Modifier.fillMaxWidth().onFocusChanged { onFocus(it.isFocused) }.semantics { contentDescription = "Note" },
                        textStyle = Knit.type.body.copy(color = Knit.colors.label, fontStyle = FontStyle.Italic), cursorBrush = SolidColor(Knit.colors.accent),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionSettingsSheet(p: Pattern, section: Section, update: ((Section) -> Section) -> Unit, onDismiss: () -> Unit) {
    Sheet("Section", onDismiss) {
        Column(Modifier.padding(horizontal = PagePadding).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BoundField(section.title, { v -> update { it.copy(title = v) } }, Modifier.fillMaxWidth(), "Title", "e.g. Body", capitalization = KeyboardCapitalization.Words)
            BoundField(section.description, { v -> update { it.copy(description = v) } }, Modifier.fillMaxWidth(), "Introduction", "e.g. Cast on with the long-tail method", singleLine = false, minLines = 2)
        }
        SectionHeader("Worked")
        Segmented(listOf("Flat (rows)", "In the round"), if (section.construction == Construction.FLAT) 0 else 1,
            { i -> update { it.copy(construction = if (i == 0) Construction.FLAT else Construction.ROUND, generated = false) } }, Modifier.padding(horizontal = PagePadding).fillMaxWidth())
        if (section.construction == Construction.FLAT) {
            Spacer(Modifier.height(10.dp))
            Segmented(listOf("Row 1 is right side", "Row 1 is wrong side"), if (section.firstRowSide == Side.RS) 0 else 1,
                { i -> update { it.copy(firstRowSide = if (i == 0) Side.RS else Side.WS, generated = false) } }, Modifier.padding(horizontal = PagePadding).fillMaxWidth())
        }
        SectionHeader("Linked chart")
        Group {
            ListRow("None", trailing = { if (section.chartId == null) KIcon(Icons.Check, Knit.colors.accent, 18.dp) }) { update { it.copy(chartId = null, generated = false) } }
            for (c in p.charts) {
                RowDivider()
                ListRow(c.name.ifBlank { "Chart" }, subtitle = "${c.cols} × ${c.rows}", trailing = { if (section.chartId == c.id) KIcon(Icons.Check, Knit.colors.accent, 18.dp) }) {
                    update { it.copy(chartId = c.id, generated = false) }
                }
            }
        }
        SectionFooter("A linked chart is highlighted row by row while you knit, and Check reports where the rows and the chart disagree.")
    }
}

/** Marker positions [2, 4] on 6 sts → "2, 2, 2". */
fun markersToGroups(markers: List<Int>, total: Int?): String {
    if (markers.isEmpty()) return ""
    val sorted = markers.sorted()
    val groups = sorted.mapIndexed { i, m -> m - (if (i == 0) 0 else sorted[i - 1]) }
    val last = total?.let { it - sorted.last() }?.takeIf { it >= 0 }
    return (groups + listOfNotNull(last)).joinToString(", ")
}

/** "2, 2, 2" → markers after 2 and 4 sts (a last group that reaches the end places no marker). */
fun groupsToMarkers(text: String, total: Int?): List<Int> {
    val groups = Regex("""\d+""").findAll(text).map { it.value.toInt() }.toList()
    var sum = 0
    val positions = groups.map { sum += it; sum }
    return if (positions.isNotEmpty() && (total == null || positions.last() >= total) && groups.size > 1) positions.dropLast(1) else positions
}
