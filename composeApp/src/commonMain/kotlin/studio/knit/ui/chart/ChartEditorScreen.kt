package studio.knit.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import studio.knit.chart.cell
import studio.knit.chart.rowNumber
import studio.knit.chart.stitchNumber
import studio.knit.convert.chartToSection
import studio.knit.model.Chart
import studio.knit.stitch.StitchLibrary
import studio.knit.ui.app.LocalApp
import studio.knit.ui.app.LocalNav
import studio.knit.ui.app.rememberHaptic
import studio.knit.ui.components.EmptyState
import studio.knit.ui.components.FixedPage
import studio.knit.ui.components.IconBtn
import studio.knit.ui.components.Icons
import studio.knit.ui.components.KIcon
import studio.knit.ui.components.MenuButton
import studio.knit.ui.components.MenuItem
import studio.knit.ui.components.pressable
import studio.knit.ui.pattern.ColorDot
import studio.knit.ui.theme.Knit
import studio.knit.util.newId
import studio.knit.validate.Severity
import studio.knit.validate.Validator
import kotlin.math.max
import kotlin.math.min

/**
 * The chart editor. One finger draws with the current tool; two fingers always pan and zoom.
 * Touch and hold a cell for row, column and stitch options.
 */
@Composable
fun ChartEditorScreen(patternId: String, chartId: String, focusRow: Int, focusCol: Int) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val editor = remember(patternId) { app.editor(patternId) }
    val pattern by editor.state.collectAsState()
    val loaded by editor.loaded.collectAsState()
    val library by app.library.collectAsState()
    val settings by app.settings.collectAsState()
    val haptic = rememberHaptic()
    val density = LocalDensity.current.density

    val libraryState = rememberUpdatedState(library)
    val ukState = rememberUpdatedState(settings.ukTerms)
    val state = remember(patternId, chartId) { ChartEditorState(editor, chartId, { libraryState.value }, { ukState.value }, haptic) }

    val chart = pattern?.charts?.firstOrNull { it.id == chartId }
    if (chart == null || chart.cells.size != chart.cols * chart.rows) {
        FixedPage("Chart", onBack = nav::back) {
            if (loaded) EmptyState(Icons.Grid, "Chart not available", if (chart == null) "It may have been deleted." else "The chart data is damaged. Run “Check data” in Settings to repair it.")
        }
        return
    }
    val shown = state.preview ?: chart
    val issues = remember(chart, library) { Validator.validateChart(chart, library) }
    val problems = issues.count { it.severity != Severity.INFO }

    var showPicker by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showIssues by remember { mutableStateOf(false) }
    var showColors by remember { mutableStateOf(false) }
    var cellMenu by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    // First layout, and whenever the chart is resized: fit it to the screen.
    LaunchedEffect(state.viewSize, chart.cols, chart.rows) {
        if (state.viewSize.width > 0f) {
            state.fit(chart, density)
            if (focusRow in 0 until chart.rows) {
                val col = if (focusCol in 0 until chart.cols) focusCol else chart.cols / 2
                state.centerOn(chart, focusRow, col, density)
                state.selection = if (focusCol in 0 until chart.cols) studio.knit.chart.CellRect(focusRow, focusCol, focusRow, focusCol)
                else studio.knit.chart.CellRect(focusRow, 0, focusRow, chart.cols - 1)
            }
        }
    }

    FixedPage(
        chart.name.ifBlank { "Chart" }, onBack = nav::back,
        actions = {
            IconBtn(Icons.Undo, "Undo", state::undo, enabled = state.canUndo)
            IconBtn(Icons.Redo, "Redo", state::redo, enabled = state.canRedo)
            if (problems > 0) {
                IconBtn(Icons.Warning, "$problems chart problems", { showIssues = true }, tint = if (issues.any { it.severity == Severity.ERROR }) Knit.colors.error else Knit.colors.warning)
            }
            MenuButton(listOf(
                MenuItem("Chart settings", Icons.Settings) { showSettings = true },
                MenuItem("Fit to screen", Icons.Fit) { state.fit(chart, density) },
                MenuItem(if (pattern?.sections?.any { it.chartId == chartId } == true) "Rewrite rows from chart" else "Write rows from chart", Icons.Rows) {
                    val (section, notes) = chartToSection(chart, library, ::newId, settings.ukTerms)
                    editor.update { p ->
                        val existing = p.sections.firstOrNull { it.chartId == chartId }
                        if (existing == null) p.copy(sections = p.sections + section)
                        else p.copy(sections = p.sections.map { if (it.id == existing.id) section.copy(id = existing.id, title = existing.title, castOn = existing.castOn, description = existing.description) else it })
                    }
                    app.message(if (notes.isEmpty()) "Rows written — see the Rows tab" else "Rows written with ${notes.size} ${if (notes.size == 1) "note" else "notes"}")
                },
                MenuItem("Check chart", Icons.CheckCircle) { showIssues = true },
            ))
        },
    ) {
        ChartCanvas(shown, library, state, Modifier.weight(1f).fillMaxWidth(), onLongPress = { cell -> haptic(true); cellMenu = cell })

        // Status line: what is under the selection, or a hint.
        val sel = state.selection
        val hint = when {
            sel != null && sel.rows == 1 && sel.cols == 1 -> {
                val def = library.require(chart.cell(sel.row0, sel.col0).stitch)
                "Row ${chart.rowNumber(sel.row0)}, stitch ${chart.stitchNumber(sel.col0)}  ·  ${def.name}"
            }
            sel != null -> "${sel.cols} × ${sel.rows} selected"
            state.tool == ChartTool.SELECT -> "Drag to select an area"
            state.tool == ChartTool.MOVE -> "Drag to move, pinch to zoom"
            else -> "${chart.cols} × ${chart.rows}  ·  two fingers to pan and zoom"
        }
        Text(hint, style = Knit.type.footnote, color = Knit.colors.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().background(Knit.colors.background).padding(horizontal = 16.dp, vertical = 6.dp))

        Column(Modifier.fillMaxWidth().background(Knit.colors.surface).navigationBarsPadding()) {
            Box(Modifier.fillMaxWidth().height(0.6.dp).background(Knit.colors.separator))
            if (sel != null && state.tool == ChartTool.SELECT) SelectionBar(state)
            else PaletteBar(state, chart, library, onMore = { showPicker = true }, onAddColor = { showColors = true })
            ToolBar(state)
        }
    }

    if (showPicker) StitchPickerSheet(library, state.stitchId, onPick = { state.useStitch(it); showPicker = false }, onDismiss = { showPicker = false })
    if (showSettings) ChartSettingsSheet(chart, state, library) { showSettings = false }
    if (showColors) ChartColorsSheet(chart, state) { showColors = false }
    if (showIssues) ChartIssuesSheet(issues, onPick = { row, col ->
        showIssues = false
        if (row != null) {
            state.tool = ChartTool.SELECT
            state.selection = if (col != null) studio.knit.chart.CellRect(row, col, row, col) else studio.knit.chart.CellRect(row, 0, row, chart.cols - 1)
            state.centerOn(chart, row, col ?: (chart.cols / 2), density)
        }
    }, onDismiss = { showIssues = false })
    cellMenu?.let { (row, col) -> CellMenuSheet(chart, row, col, state, library) { cellMenu = null } }
}

@Composable
private fun ChartCanvas(
    chart: Chart, library: StitchLibrary, state: ChartEditorState, modifier: Modifier,
    onLongPress: (Pair<Int, Int>) -> Unit,
) {
    val style = rememberChartStyle()
    val measurer = rememberTextMeasurer(cacheSize = 128)
    val current = rememberUpdatedState(chart)
    val longPress = rememberUpdatedState(onLongPress)
    Box(
        modifier.clipToBounds().background(Knit.colors.background)
            .onSizeChanged { state.viewSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .semantics { contentDescription = "Chart canvas, ${chart.cols} stitches by ${chart.rows} rows. ${state.tool.label} tool." }
            .pointerInput(state) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startCell = state.cellAt(current.value, down.position)
                    // 0 = undecided, 1 = stroke, 2 = one-finger pan, 3 = two-finger transform
                    var mode = 0
                    var lastTime = down.uptimeMillis
                    val timeout = viewConfiguration.longPressTimeoutMillis
                    val slop = viewConfiguration.touchSlop
                    while (true) {
                        val event = if (mode == 0) {
                            val remaining = timeout - (lastTime - down.uptimeMillis)
                            withTimeoutOrNull(remaining.coerceAtLeast(1)) { awaitPointerEvent() }
                        } else awaitPointerEvent()

                        if (event == null) {
                            // Held still: context menu for the cell. Swallow the rest of the gesture.
                            if (startCell != null) longPress.value(startCell)
                            while (true) {
                                val e = awaitPointerEvent()
                                e.changes.forEach { it.consume() }
                                if (e.changes.none { it.pressed }) break
                            }
                            break
                        }
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) {
                            when (mode) {
                                0 -> state.tap(current.value, startCell)
                                1 -> state.endStroke()
                            }
                            break
                        }
                        lastTime = pressed[0].uptimeMillis
                        if (pressed.size >= 2) {
                            if (mode == 1) state.endStroke()
                            mode = 3
                            state.transform(current.value, event.calculateCentroid(useCurrent = false), event.calculatePan(), event.calculateZoom())
                            event.changes.forEach { it.consume() }
                        } else {
                            val change = pressed[0]
                            when (mode) {
                                0 -> if ((change.position - down.position).getDistance() > slop) {
                                    if (state.tool == ChartTool.MOVE) {
                                        mode = 2
                                    } else {
                                        mode = 1
                                        state.beginStroke(current.value, startCell)
                                        state.strokeTo(strokeCell(state, current.value, change.position))
                                    }
                                }
                                1 -> state.strokeTo(strokeCell(state, current.value, change.position))
                                2 -> state.panBy(current.value, change.positionChange())
                            }
                            if (mode != 0) change.consume()
                        }
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cell = state.cellPx
            if (cell <= 0f) return@Canvas
            val o = state.offset
            // Only the cells on screen are drawn.
            val c0 = max(0, ((0f - o.x) / cell).toInt())
            val c1 = min(chart.cols - 1, ((size.width - o.x) / cell).toInt())
            val topRow = max(0, ((0f - o.y) / cell).toInt())
            val bottomRow = min(chart.rows - 1, ((size.height - o.y) / cell).toInt())
            if (c0 > c1 || topRow > bottomRow) return@Canvas
            drawChart(
                chart, library, style, measurer, o, cell,
                rows = (chart.rows - 1 - bottomRow)..(chart.rows - 1 - topRow), cols = c0..c1,
                selection = state.selection, numbers = true, detail = cell >= 7f,
            )
        }
    }
}

private fun strokeCell(state: ChartEditorState, chart: Chart, pos: androidx.compose.ui.geometry.Offset): Pair<Int, Int>? =
    if (state.tool == ChartTool.SELECT) state.nearestCell(chart, pos) else state.cellAt(chart, pos)

// ---------------------------------------------------------------------------------------------
// Bars
// ---------------------------------------------------------------------------------------------

@Composable
private fun PaletteBar(state: ChartEditorState, chart: Chart, library: StitchLibrary, onMore: () -> Unit, onAddColor: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(62.dp), verticalAlignment = Alignment.CenterVertically) {
        // Layer switch: stitches or colours.
        Row(Modifier.padding(start = 10.dp).clip(RoundedCornerShape(10.dp)).background(Knit.colors.fill).padding(2.dp)) {
            LayerButton(Icons.Grid, "Stitches", state.layer == ChartLayer.STITCH) { state.layer = ChartLayer.STITCH; if (state.tool == ChartTool.ERASE) state.tool = ChartTool.DRAW }
            LayerButton(Icons.Palette, "Colours", state.layer == ChartLayer.COLOR) { state.layer = ChartLayer.COLOR; if (state.tool == ChartTool.ERASE) state.tool = ChartTool.DRAW }
        }
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (state.layer == ChartLayer.STITCH) {
                PaletteTile(false, "All stitches", onMore) { KIcon(Icons.Book, Knit.colors.accent, 22.dp) }
                for (id in state.recent) {
                    val def = library[id] ?: continue
                    PaletteTile(state.stitchId == id, def.name, { state.useStitch(id) }) { StitchSymbol(def, cell = 28.dp) }
                }
            } else {
                PaletteTile(state.colorIndex == -1, "No colour", { state.colorIndex = -1; state.tool = ChartTool.DRAW }) {
                    Box(Modifier.size(26.dp).clip(RoundedCornerShape(6.dp)).background(Knit.colors.chartPaper).border(1.dp, Knit.colors.separator, RoundedCornerShape(6.dp)))
                }
                chart.palette.forEachIndexed { i, c ->
                    PaletteTile(state.colorIndex == i, "Colour ${c.name}", { state.colorIndex = i; state.tool = ChartTool.DRAW }) {
                        Box(contentAlignment = Alignment.Center) {
                            Box(Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(Color(c.argb)))
                            Text(c.name, style = Knit.type.caption, color = if (Color(c.argb).luminanceOf() < 0.5f) Color.White else Color(0xFF1C1C1E))
                        }
                    }
                }
                PaletteTile(false, "Edit colours", onAddColor) { KIcon(Icons.Plus, Knit.colors.accent, 22.dp) }
            }
        }
    }
}

private fun Color.luminanceOf(): Float = 0.299f * red + 0.587f * green + 0.114f * blue

@Composable
private fun LayerButton(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(if (on) Knit.colors.surfaceRaised else Color.Transparent)
            .pressable(role = Role.Tab, onClick = onClick).semantics { contentDescription = label; selected = on },
        contentAlignment = Alignment.Center,
    ) { KIcon(icon, if (on) Knit.colors.accent else Knit.colors.secondaryLabel, 20.dp) }
}

@Composable
private fun PaletteTile(selected: Boolean, description: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.height(46.dp).clip(RoundedCornerShape(11.dp))
            .background(if (selected) Knit.colors.accentSoft else Knit.colors.fill)
            .border(if (selected) 2.dp else 0.dp, if (selected) Knit.colors.accent else Color.Transparent, RoundedCornerShape(11.dp))
            .pressable(onClick = onClick).padding(horizontal = 9.dp).semantics { contentDescription = description; this.selected = selected },
        contentAlignment = Alignment.Center,
    ) { Box(Modifier.padding(horizontal = 1.dp), contentAlignment = Alignment.Center) { content() } }
}

@Composable
private fun ToolBar(state: ChartEditorState) {
    val tools = listOf(ChartTool.DRAW to Icons.Pencil, ChartTool.ERASE to Icons.Eraser, ChartTool.SELECT to Icons.Select, ChartTool.MOVE to Icons.Move)
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        for ((tool, icon) in tools) {
            val on = state.tool == tool
            Column(
                Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(12.dp)).background(if (on) Knit.colors.accentSoft else Color.Transparent)
                    .pressable(role = Role.Tab) { state.tool = tool; if (tool != ChartTool.SELECT) state.selection = null }
                    .semantics { selected = on },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                KIcon(icon, if (on) Knit.colors.accent else Knit.colors.secondaryLabel, 22.dp)
                Text(tool.label, style = Knit.type.caption, color = if (on) Knit.colors.accent else Knit.colors.secondaryLabel)
            }
        }
    }
}

/** Actions for the selected area; replaces the palette while a selection exists. */
@Composable
private fun SelectionBar(state: ChartEditorState) {
    val app = LocalApp.current
    Row(Modifier.fillMaxWidth().height(62.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        SelectionAction(Icons.Copy, "Copy") { state.copySelection(); app.message("Copied") }
        SelectionAction(Icons.Scissors, "Cut") { state.cutSelection() }
        SelectionAction(Icons.Paste, "Paste", enabled = state.clip != null) { state.paste() }
        SelectionAction(Icons.Fill, "Fill") { state.fillSelection() }
        SelectionAction(Icons.Eraser, "Clear") { state.clearSelection() }
        SelectionAction(Icons.Repeat, if (state.repeatInSelection() != null) "Unrepeat" else "Repeat") { state.toggleRepeat() }
        SelectionAction(Icons.FlipH, "Mirror") { state.flip(true) }
        SelectionAction(Icons.FlipV, "Flip") { state.flip(false) }
        SelectionAction(Icons.Close, "Done") { state.selection = null }
    }
}

@Composable
private fun SelectionAction(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Column(Modifier.width(60.dp).height(54.dp).pressable(enabled, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        KIcon(icon, Knit.colors.accent, 22.dp)
        Spacer(Modifier.height(2.dp))
        Text(label, style = Knit.type.caption, color = Knit.colors.label, maxLines = 1)
    }
}
