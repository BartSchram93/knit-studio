package studio.knit.ui.chart

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import studio.knit.chart.Anchor
import studio.knit.chart.CellRect
import studio.knit.chart.addColor
import studio.knit.chart.cell
import studio.knit.chart.deleteCol
import studio.knit.chart.deleteRow
import studio.knit.chart.insertCol
import studio.knit.chart.insertRow
import studio.knit.chart.removeColor
import studio.knit.chart.removeRepeat
import studio.knit.chart.resize
import studio.knit.chart.rowNumber
import studio.knit.chart.sideOf
import studio.knit.chart.stitchNumber
import studio.knit.chart.usedColors
import studio.knit.model.Chart
import studio.knit.model.Construction
import studio.knit.model.Direction
import studio.knit.model.Side
import studio.knit.model.StitchCategory
import studio.knit.model.StitchDef
import studio.knit.stitch.StitchLibrary
import studio.knit.ui.components.BoundField
import studio.knit.ui.components.EmptyState
import studio.knit.ui.components.Group
import studio.knit.ui.components.IconBtn
import studio.knit.ui.components.Icons
import studio.knit.ui.components.KIcon
import studio.knit.ui.components.ListRow
import studio.knit.ui.components.PagePadding
import studio.knit.ui.components.RowDivider
import studio.knit.ui.components.SectionFooter
import studio.knit.ui.components.SectionHeader
import studio.knit.ui.components.Segmented
import studio.knit.ui.components.Sheet
import studio.knit.ui.components.Stepper
import studio.knit.ui.components.pressable
import studio.knit.ui.library.SearchField
import studio.knit.ui.pattern.ColorDot
import studio.knit.ui.pattern.ColorGrid
import studio.knit.ui.pattern.IssueRow
import studio.knit.ui.theme.Knit
import studio.knit.validate.Issue

/** How a stitch changes the count, in words: "+1", "−2", "no change". */
fun deltaLabel(def: StitchDef): String = when {
    def.width == 0 -> "not counted"
    def.consumes == 0 && def.produces == 0 -> "no stitch"
    def.delta > 0 -> "+${def.delta} st"
    def.delta < 0 -> "−${-def.delta} st"
    else -> if (def.consumes == 1) "1 st" else "${def.consumes} sts"
}

@Composable
fun StitchTile(def: StitchDef, selected: Boolean, modifier: Modifier = Modifier, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(if (selected) Knit.colors.accentSoft else Knit.colors.surface)
            .border(if (selected) 2.dp else 0.dp, if (selected) Knit.colors.accent else Color.Transparent, RoundedCornerShape(12.dp))
            .pressable(onLongClick = onLongClick, onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.height(34.dp), contentAlignment = Alignment.Center) { StitchSymbol(def, cell = 32.dp) }
        Spacer(Modifier.height(6.dp))
        Text(def.abbr, style = Knit.type.footnote.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = Knit.colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(def.name, style = Knit.type.caption, color = Knit.colors.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

/** A simple fixed-column grid that works inside a scrolling sheet. */
@Composable
fun <T> FixedGrid(items: List<T>, columns: Int, modifier: Modifier = Modifier, content: @Composable (T, Modifier) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in items.chunked(columns)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (item in row) content(item, Modifier.weight(1f))
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun StitchPickerSheet(library: StitchLibrary, selected: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var info by remember { mutableStateOf<StitchDef?>(null) }
    Sheet("Stitches", onDismiss) {
        SearchField(query, { query = it }, "Search stitches", Modifier.padding(horizontal = PagePadding).padding(top = 4.dp).fillMaxWidth())
        val q = query.trim().lowercase()
        val chartable = library.all.filter { it.width > 0 }.filter {
            q.isEmpty() || it.name.lowercase().contains(q) || it.abbr.lowercase().contains(q) || it.aliases.any { a -> a.contains(q) }
        }
        if (chartable.isEmpty()) EmptyState(Icons.Search, "No stitch found", "Create your own in Tools › Stitch library.")
        for (category in StitchCategory.entries) {
            val defs = chartable.filter { it.category == category }
            if (defs.isEmpty()) continue
            SectionHeader(category.label)
            FixedGrid(defs, 4, Modifier.padding(horizontal = PagePadding)) { def, m ->
                StitchTile(def, def.id == selected, m, onLongClick = { info = def }) { onPick(def.id) }
            }
        }
        SectionFooter("Touch and hold a stitch to see how it is worked.")
    }
    info?.let { StitchInfoSheet(it, library) { info = null } }
}

@Composable
fun StitchInfoSheet(def: StitchDef, library: StitchLibrary, onRename: (() -> Unit)? = null, onDismiss: () -> Unit) {
    Sheet(def.name, onDismiss) {
        Row(Modifier.padding(horizontal = PagePadding).padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            StitchSymbol(def, cell = 54.dp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(def.abbr, style = Knit.type.title2, color = Knit.colors.label)
                Text(deltaLabel(def) + "  ·  uses ${def.consumes}, makes ${def.produces}", style = Knit.type.subhead, color = Knit.colors.secondaryLabel)
            }
        }
        if (def.description.isNotBlank()) { SectionHeader("About"); Text(def.description, style = Knit.type.body, color = Knit.colors.label, modifier = Modifier.padding(horizontal = PagePadding + 4.dp)) }
        if (def.instructions.isNotBlank()) { SectionHeader("How to work it"); Text(def.instructions, style = Knit.type.body, color = Knit.colors.label, modifier = Modifier.padding(horizontal = PagePadding + 4.dp)) }
        SectionHeader("On the chart")
        Group {
            ListRow("Width", value = if (def.width == 1) "1 cell" else "${def.width} cells")
            RowDivider()
            val ws = def.ws?.let { library[it] }
            ListRow("On wrong-side rows", value = when {
                ws == null -> "not defined"
                ws.id == def.id -> "same"
                else -> "work ${ws.abbr}"
            })
            RowDivider()
            ListRow("Worked on", value = def.worksOn.label)
            if (def.abbrUk != null) { RowDivider(); ListRow("UK term", value = def.abbrUk!!) }
        }
        if (onRename != null) {
            SectionHeader("Your name for it")
            Group {
                ListRow("Rename", subtitle = "Use your own name and abbreviation in rows, charts and PDFs", titleColor = Knit.colors.accent, chevron = true, onClick = onRename)
            }
        }
    }
}

@Composable
fun ChartSettingsSheet(chart: Chart, state: ChartEditorState, library: StitchLibrary, onDismiss: () -> Unit) {
    var colSide by remember { mutableIntStateOf(1) }
    var rowSide by remember { mutableIntStateOf(0) }
    fun change(transform: (Chart) -> Chart) = state.commit(transform(chart), chart)
    Sheet("Chart settings", onDismiss) {
        BoundField(chart.name, { v -> change { it.copy(name = v) } }, Modifier.padding(horizontal = PagePadding).padding(top = 8.dp).fillMaxWidth(), "Name")

        SectionHeader("Size")
        Group {
            Stepper(chart.cols, { n -> change { it.resize(n, it.rows, if (colSide == 0) Anchor.START else Anchor.END, Anchor.END, library) } }, 1..200,
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp), "Stitches")
            Segmented(listOf("Change at left", "Change at right"), colSide, { colSide = it }, Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp).fillMaxWidth())
            RowDivider()
            Stepper(chart.rows, { n -> change { it.resize(it.cols, n, Anchor.END, if (rowSide == 0) Anchor.END else Anchor.START, library) } }, 1..200,
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp), "Rows")
            Segmented(listOf("Change at top", "Change at bottom"), rowSide, { rowSide = it }, Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp).fillMaxWidth())
        }

        SectionHeader("How it is knitted")
        Segmented(listOf("Flat (rows)", "In the round"), if (chart.construction == Construction.FLAT) 0 else 1,
            { i -> change { it.copy(construction = if (i == 0) Construction.FLAT else Construction.ROUND) } }, Modifier.padding(horizontal = PagePadding).fillMaxWidth())
        if (chart.construction == Construction.FLAT) {
            Spacer(Modifier.height(10.dp))
            Segmented(listOf("Row ${chart.firstRowNumber} is right side", "Row ${chart.firstRowNumber} is wrong side"), if (chart.firstRowSide == Side.RS) 0 else 1,
                { i -> change { it.copy(firstRowSide = if (i == 0) Side.RS else Side.WS) } }, Modifier.padding(horizontal = PagePadding).fillMaxWidth())
        }
        SectionHeader("Reading direction")
        Segmented(listOf("Right to left", "Left to right"), if (chart.rsDirection == Direction.RIGHT_TO_LEFT) 0 else 1,
            { i -> change { it.copy(rsDirection = if (i == 0) Direction.RIGHT_TO_LEFT else Direction.LEFT_TO_RIGHT) } }, Modifier.padding(horizontal = PagePadding).fillMaxWidth())
        SectionFooter(
            if (chart.construction == Construction.FLAT) "Direction of right-side rows; wrong-side rows are read the opposite way. Choose left to right for mirrored (left-handed) knitting."
            else "Direction in which every round is read.",
        )

        SectionHeader("Numbering")
        Group {
            Stepper(chart.firstRowNumber, { n -> change { it.copy(firstRowNumber = n) } }, 1..999, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), "First row number")
        }

        if (chart.repeats.isNotEmpty()) {
            SectionHeader("Repeats")
            Group {
                chart.repeats.forEachIndexed { i, rep ->
                    if (i > 0) RowDivider()
                    val sts = "${rep.colEnd - rep.colStart + 1} sts"
                    val rows = "rows ${chart.rowNumber(rep.rowStart)}–${chart.rowNumber(rep.rowEnd)}"
                    ListRow("$sts × $rows", leading = { KIcon(Icons.Repeat, Knit.colors.accent, 20.dp) }, trailing = {
                        IconBtn(Icons.Trash, "Remove repeat", { change { it.removeRepeat(rep.id) } }, tint = Knit.colors.error, size = 20.dp)
                    })
                }
            }
            SectionFooter("To add a repeat, select an area with the Select tool and choose Repeat.")
        }

        SectionHeader("Notes")
        BoundField(chart.notes, { v -> change { it.copy(notes = v) } }, Modifier.padding(horizontal = PagePadding).fillMaxWidth(), singleLine = false, minLines = 2, placeholder = "Shown above the chart in the exported pattern")
    }
}

@Composable
fun ChartColorsSheet(chart: Chart, state: ChartEditorState, onDismiss: () -> Unit) {
    val used = chart.usedColors()
    Sheet("Colours", onDismiss) {
        if (chart.palette.isNotEmpty()) {
            SectionHeader("In this chart")
            Group {
                chart.palette.forEachIndexed { i, c ->
                    if (i > 0) RowDivider(54.dp)
                    ListRow("Colour ${c.name}", subtitle = if (i in used) "In use" else "Not used yet", leading = { ColorDot(c.argb, 26.dp) }, trailing = {
                        IconBtn(Icons.Trash, "Remove colour ${c.name}", {
                            state.commit(chart.removeColor(i), chart)
                            if (state.colorIndex >= chart.palette.size - 1) state.colorIndex = chart.palette.size - 2
                        }, tint = Knit.colors.error, size = 20.dp)
                    })
                }
            }
            SectionFooter("Cells without a colour are the main colour (MC). Removing a colour returns its cells to MC.")
        }
        SectionHeader("Add a colour")
        ColorGrid(null, { argb ->
            if (chart.palette.size < 12) {
                state.commit(chart.addColor(argb), chart)
                state.colorIndex = chart.palette.size
                state.layer = ChartLayer.COLOR
                state.tool = ChartTool.DRAW
                onDismiss()
            }
        }, Modifier.padding(horizontal = PagePadding + 4.dp))
        if (chart.palette.size >= 12) SectionFooter("A chart can have up to 12 colours.")
    }
}

@Composable
fun ChartIssuesSheet(issues: List<Issue>, onPick: (row: Int?, col: Int?) -> Unit, onDismiss: () -> Unit) {
    Sheet("Check chart", onDismiss) {
        if (issues.isEmpty()) {
            EmptyState(Icons.CheckCircle, "The chart adds up", "Every row works exactly the stitches the row below leaves.")
        } else {
            Spacer(Modifier.height(8.dp))
            Group {
                issues.forEachIndexed { i, issue ->
                    if (i > 0) RowDivider(50.dp)
                    IssueRow(issue, if (issue.location.row != null) ({ onPick(issue.location.row, issue.location.col) }) else null)
                }
            }
            SectionFooter("Tap an item to jump to it. Nothing is changed automatically.")
        }
    }
}

/** Options for one cell, opened by touching and holding it. */
@Composable
fun CellMenuSheet(chart: Chart, row: Int, col: Int, state: ChartEditorState, library: StitchLibrary, onDismiss: () -> Unit) {
    val cell = chart.cell(row, col)
    val def = library.require(cell.stitch)
    var info by remember { mutableStateOf(false) }
    fun act(next: Chart) { state.commit(next, chart); onDismiss() }
    Sheet("Row ${chart.rowNumber(row)}, stitch ${chart.stitchNumber(col)}", onDismiss) {
        Spacer(Modifier.height(8.dp))
        Group {
            ListRow(def.name, subtitle = "${if (chart.construction == Construction.ROUND) "Round" else if (chart.sideOf(row) == Side.RS) "Right-side row" else "Wrong-side row"}  ·  ${deltaLabel(def)}",
                leading = { StitchSymbol(def, cell = 34.dp) }, chevron = true) { info = true }
            RowDivider()
            ListRow("Use this stitch", leading = { KIcon(Icons.Pencil, Knit.colors.accent, 20.dp) }) {
                state.useStitch(def.id)
                if (cell.color >= 0) state.colorIndex = cell.color
                onDismiss()
            }
        }
        SectionHeader("Select")
        Group {
            ListRow("Select this row", leading = { KIcon(Icons.Select, Knit.colors.accent, 20.dp) }) {
                state.tool = ChartTool.SELECT; state.selection = CellRect(row, 0, row, chart.cols - 1); onDismiss()
            }
            RowDivider()
            ListRow("Select this column", leading = { KIcon(Icons.Select, Knit.colors.accent, 20.dp) }) {
                state.tool = ChartTool.SELECT; state.selection = CellRect(0, col, chart.rows - 1, col); onDismiss()
            }
        }
        SectionHeader("Rows")
        Group {
            ListRow("Insert row above") { act(chart.insertRow(row + 1)) }
            RowDivider()
            ListRow("Insert row below") { act(chart.insertRow(row)) }
            RowDivider()
            ListRow("Delete this row", titleColor = if (chart.rows > 1) Knit.colors.error else Knit.colors.tertiaryLabel, onClick = if (chart.rows > 1) ({ act(chart.deleteRow(row)) }) else null)
        }
        SectionHeader("Columns")
        Group {
            ListRow("Insert column to the left") { act(chart.insertCol(col, library)) }
            RowDivider()
            ListRow("Insert column to the right") { act(chart.insertCol(col + 1, library)) }
            RowDivider()
            ListRow("Delete this column", titleColor = if (chart.cols > 1) Knit.colors.error else Knit.colors.tertiaryLabel, onClick = if (chart.cols > 1) ({ act(chart.deleteCol(col, library)) }) else null)
        }
    }
    if (info) StitchInfoSheet(def, library) { info = false }
}
