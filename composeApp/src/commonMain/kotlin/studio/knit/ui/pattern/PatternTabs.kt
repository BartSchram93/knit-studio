package studio.knit.ui.pattern

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import studio.knit.calc.Needles
import studio.knit.calc.YarnCalc
import studio.knit.chart.blankChart
import studio.knit.convert.chartToSection
import studio.knit.model.Chart
import studio.knit.model.Construction
import studio.knit.model.Needle
import studio.knit.model.Pattern
import studio.knit.model.Row as KnitRow
import studio.knit.model.RowKind
import studio.knit.model.Section
import studio.knit.model.Yarn
import studio.knit.stitch.StitchLibrary
import studio.knit.ui.app.LocalApp
import studio.knit.ui.app.Nav
import studio.knit.ui.app.PatternEditor
import studio.knit.ui.app.unitFormat
import studio.knit.ui.chart.ChartThumbnail
import studio.knit.ui.components.Badge
import studio.knit.ui.components.Card
import studio.knit.ui.components.ConfirmDialog
import studio.knit.ui.components.EmptyState
import studio.knit.ui.components.Group
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
import studio.knit.ui.components.SoftButton
import studio.knit.ui.components.Stepper
import studio.knit.ui.components.pressable
import studio.knit.ui.theme.Knit
import studio.knit.util.newId
import studio.knit.validate.Severity
import studio.knit.validate.Validator
import studio.knit.written.evaluateSection

// ---------------------------------------------------------------------------------------------
// Charts
// ---------------------------------------------------------------------------------------------

fun LazyListScope.chartsTab(p: Pattern, editor: PatternEditor, library: StitchLibrary, nav: Nav) {
    if (p.charts.isEmpty()) {
        item {
            var adding by remember { mutableStateOf(false) }
            EmptyState(Icons.Grid, "No charts yet", "Draw your stitch pattern on a grid. Rows can be written out from it automatically.", Modifier.padding(top = 16.dp)) {
                PrimaryButton("Add chart", { adding = true }, icon = Icons.Plus)
            }
            if (adding) NewChartSheet(p, editor, nav) { adding = false }
        }
        return
    }
    items(p.charts.size, key = { p.charts[it].id }) { index ->
        val chart = p.charts[index]
        ChartCard(p, chart, editor, library, nav)
    }
    item {
        var adding by remember { mutableStateOf(false) }
        SoftButton("Add chart", { adding = true }, Modifier.padding(horizontal = PagePadding).padding(top = 4.dp).fillMaxWidth(), icon = Icons.Plus)
        if (adding) NewChartSheet(p, editor, nav) { adding = false }
    }
}

@Composable
private fun ChartCard(p: Pattern, chart: Chart, editor: PatternEditor, library: StitchLibrary, nav: Nav) {
    val app = LocalApp.current
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val issues = remember(chart, library) { Validator.validateChart(chart, library) }
    val errors = issues.count { it.severity == Severity.ERROR }
    val linked = p.sections.firstOrNull { it.chartId == chart.id }
    Card(Modifier.padding(horizontal = PagePadding).padding(top = 14.dp), onClick = { nav.openChart(p.id, chart.id) }, padding = 0.dp) {
        Box(Modifier.fillMaxWidth().height(170.dp).background(Knit.colors.chartPaper).padding(14.dp)) {
            ChartThumbnail(chart, library, Modifier.fillMaxWidth().height(142.dp))
        }
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(chart.name.ifBlank { "Chart" }, style = Knit.type.headline, color = Knit.colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${chart.cols} × ${chart.rows}  ·  ${if (chart.construction == Construction.ROUND) "in the round" else "flat"}",
                        style = Knit.type.footnote, color = Knit.colors.secondaryLabel)
                    if (errors > 0) { Spacer(Modifier.width(8.dp)); Badge("$errors ${if (errors == 1) "problem" else "problems"}", Knit.colors.error) }
                    else if (linked != null) { Spacer(Modifier.width(8.dp)); Badge("Rows written", Knit.colors.success) }
                }
            }
            MenuButton(listOf(
                MenuItem("Rename", Icons.Pencil) { renaming = true },
                MenuItem(if (linked == null) "Write rows from chart" else "Rewrite rows from chart", Icons.Rows) {
                    val (section, notes) = chartToSection(chart, library, ::newId, app.settings.value.ukTerms)
                    editor.update { pat ->
                        val existing = pat.sections.firstOrNull { it.chartId == chart.id }
                        if (existing == null) pat.copy(sections = pat.sections + section)
                        else pat.copy(sections = pat.sections.map { if (it.id == existing.id) section.copy(id = existing.id, title = existing.title, castOn = existing.castOn, description = existing.description) else it })
                    }
                    app.message(if (notes.isEmpty()) "Rows written from “${chart.name}”" else "Rows written — ${notes.size} ${if (notes.size == 1) "note" else "notes"} in Check")
                },
                MenuItem("Duplicate", Icons.Copy) {
                    editor.update { pat -> pat.copy(charts = pat.charts + chart.copy(id = newId(), name = "${chart.name} copy", repeats = chart.repeats.map { it.copy(id = newId()) })) }
                },
                MenuItem("Delete", Icons.Trash, destructive = true) { deleting = true },
            ), tint = Knit.colors.secondaryLabel)
        }
    }
    if (renaming) PromptDialog("Rename chart", chart.name, "Save", { name -> editor.update { pat -> pat.copy(charts = pat.charts.map { if (it.id == chart.id) it.copy(name = name) else it }) } }, { renaming = false })
    if (deleting) {
        ConfirmDialog("Delete “${chart.name}”?", if (linked != null) "The written rows made from it are kept, but will no longer be linked to a chart." else "This cannot be undone.",
            "Delete", { editor.update { pat -> pat.copy(charts = pat.charts.filterNot { it.id == chart.id }, sections = pat.sections.map { if (it.chartId == chart.id) it.copy(chartId = null, generated = false) else it }) } },
            { deleting = false }, destructive = true)
    }
}

@Composable
private fun NewChartSheet(p: Pattern, editor: PatternEditor, nav: Nav, onDismiss: () -> Unit) {
    var cols by remember { mutableIntStateOf(16) }
    var rows by remember { mutableIntStateOf(12) }
    var round by remember { mutableIntStateOf(0) }
    Sheet("New chart", onDismiss, doneLabel = "Cancel") {
        Spacer(Modifier.height(8.dp))
        Group {
            Stepper(cols, { cols = it }, 1..200, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), "Stitches wide")
            RowDivider()
            Stepper(rows, { rows = it }, 1..200, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), "Rows high")
        }
        SectionHeader("Worked")
        Segmented(listOf("Flat (rows)", "In the round"), round, { round = it }, Modifier.padding(horizontal = PagePadding).fillMaxWidth())
        SectionFooter("You can resize the chart at any time.")
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Create chart", {
            val chart = blankChart(newId(), cols, rows, "Chart ${p.charts.size + 1}", if (round == 0) Construction.FLAT else Construction.ROUND)
            editor.update { it.copy(charts = it.charts + chart) }
            onDismiss()
            nav.openChart(p.id, chart.id)
        }, Modifier.padding(horizontal = PagePadding).fillMaxWidth())
    }
}

// ---------------------------------------------------------------------------------------------
// Rows (written sections)
// ---------------------------------------------------------------------------------------------

fun LazyListScope.rowsTab(p: Pattern, editor: PatternEditor, library: StitchLibrary, nav: Nav) {
    fun addSection(): Section {
        val section = Section(id = newId(), title = "Section ${p.sections.size + 1}", rows = listOf(KnitRow(id = newId())))
        editor.update { it.copy(sections = it.sections + section) }
        return section
    }
    if (p.sections.isEmpty()) {
        item {
            EmptyState(Icons.Rows, "No written rows yet", "Type instructions such as “K2, *p2, k2; rep from * to end” and the stitch counts are checked as you write.", Modifier.padding(top = 16.dp)) {
                val pdf = p.files.firstOrNull { it.mime == "application/pdf" }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (pdf != null) {
                        PrimaryButton("Read rows from PDF", { nav.openImportRows(p.id, pdf.id) }, icon = Icons.Document)
                        Spacer(Modifier.height(10.dp))
                        SoftButton("Add section", { nav.openSection(p.id, addSection().id) }, icon = Icons.Plus)
                    } else {
                        PrimaryButton("Add section", { nav.openSection(p.id, addSection().id) }, icon = Icons.Plus)
                    }
                }
            }
        }
        return
    }
    items(p.sections.size, key = { p.sections[it].id }) { index ->
        SectionCard(p, p.sections[index], index, editor, library, nav)
    }
    item {
        SoftButton("Add section", { nav.openSection(p.id, addSection().id) }, Modifier.padding(horizontal = PagePadding).padding(top = 4.dp).fillMaxWidth(), icon = Icons.Plus)
        p.files.firstOrNull { it.mime == "application/pdf" }?.let { pdf ->
            SoftButton("Read rows from PDF", { nav.openImportRows(p.id, pdf.id) }, Modifier.padding(horizontal = PagePadding).padding(top = 8.dp).fillMaxWidth(), icon = Icons.Document)
        }
        SectionFooter("Use a section for each part of the piece — ribbing, body, sleeves, crown.")
    }
}

@Composable
private fun SectionCard(p: Pattern, section: Section, index: Int, editor: PatternEditor, library: StitchLibrary, nav: Nav) {
    var deleting by remember { mutableStateOf(false) }
    val eval = remember(section, library) { evaluateSection(section, 0, library) }
    val issues = remember(section, p.sizes, p.charts, library) { Validator.validateSection(section, p, library) }
    val errors = issues.count { it.severity == Severity.ERROR }
    val warnings = issues.count { it.severity == Severity.WARNING }
    val chart = p.charts.firstOrNull { it.id == section.chartId }
    Card(Modifier.padding(horizontal = PagePadding).padding(top = 14.dp), onClick = { nav.openSection(p.id, section.id) }, padding = 0.dp) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(section.title.ifBlank { "Section" }, style = Knit.type.headline, color = Knit.colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val word = if (section.construction == Construction.ROUND) "rounds" else "rows"
                val counts = if (eval.startCount != null && eval.endCount != null) "  ·  ${eval.startCount} → ${eval.endCount} sts" else ""
                Text("${eval.rows.size} $word$counts", style = Knit.type.footnote, color = Knit.colors.secondaryLabel)
            }
            MenuButton(listOfNotNull(
                MenuItem("Move up", enabled = index > 0) { editor.update { pat -> pat.copy(sections = pat.sections.toMutableList().apply { add(index - 1, removeAt(index)) }) } },
                MenuItem("Move down", enabled = index < p.sections.size - 1) { editor.update { pat -> pat.copy(sections = pat.sections.toMutableList().apply { add(index + 1, removeAt(index)) }) } },
                MenuItem("Duplicate", Icons.Copy) {
                    editor.update { pat -> pat.copy(sections = pat.sections + section.copy(id = newId(), title = "${section.title} copy", chartId = null, generated = false, rows = section.rows.map { it.copy(id = newId()) })) }
                },
                MenuItem("Delete", Icons.Trash, destructive = true) { deleting = true },
            ), tint = Knit.colors.secondaryLabel)
        }
        // A short preview of the first rows.
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 12.dp)) {
            for (row in section.rows.filter { it.kind == RowKind.INSTRUCTION && it.text.isNotBlank() }.take(2)) {
                Text(row.text, style = Knit.type.subhead, color = Knit.colors.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (errors + warnings > 0 || chart != null) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (errors > 0) Badge("$errors ${if (errors == 1) "problem" else "problems"}", Knit.colors.error)
                    if (warnings > 0) Badge("$warnings to check", Knit.colors.warning)
                    if (chart != null) Badge(if (section.generated) "From chart “${chart.name}”" else "Linked to “${chart.name}”", Knit.colors.info)
                }
            }
        }
    }
    if (deleting) {
        ConfirmDialog("Delete “${section.title}”?", "Its ${section.rows.size} rows will be removed. This cannot be undone.", "Delete",
            { editor.update { pat -> pat.copy(sections = pat.sections.filterNot { it.id == section.id }) } }, { deleting = false }, destructive = true)
    }
}

// ---------------------------------------------------------------------------------------------
// Materials
// ---------------------------------------------------------------------------------------------

fun LazyListScope.materialsTab(p: Pattern, editor: PatternEditor, library: StitchLibrary) {
    item { YarnList(p, editor) }
    item { NeedleList(p, editor) }
    item { YarnEstimate(p, library) }
}

@Composable
private fun YarnList(p: Pattern, editor: PatternEditor) {
    val app = LocalApp.current
    val units = unitFormat()
    val settings by app.settings.collectAsState()
    val stash by app.repo.yarnStash.collectAsState(emptyList())
    var editing by remember { mutableStateOf<Yarn?>(null) }
    var isNew by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    SectionHeader("Yarn")
    Group {
        p.yarns.forEach { y ->
            ListRow(
                listOf(y.label.takeIf { it.isNotBlank() }, y.displayName).filterNotNull().joinToString(" · "),
                subtitle = yarnSummary(y, units, settings.ukTerms).ifBlank { null }, leading = { ColorDot(y.colorArgb, 26.dp) }, chevron = true,
            ) { isNew = false; editing = y }
            RowDivider(54.dp)
        }
        Box {
            ListRow("Add yarn", titleColor = Knit.colors.accent, leading = { KIcon(Icons.Plus, Knit.colors.accent, 22.dp) }) {
                if (stash.isEmpty()) { isNew = true; editing = Yarn(id = newId(), label = if (p.yarns.isEmpty()) "MC" else "") } else menu = true
            }
            Menu(menu, { menu = false }, listOf(MenuItem("New yarn", Icons.Plus) { isNew = true; editing = Yarn(id = newId(), label = if (p.yarns.isEmpty()) "MC" else "") }) +
                stash.map { s -> MenuItem("From stash: ${s.displayName}") { editor.update { it.copy(yarns = it.yarns + s.copy(id = newId(), photoId = null)) } } })
        }
    }
    editing?.let { y ->
        YarnSheet(y, isNew,
            onSave = { saved -> editor.update { pat -> pat.copy(yarns = if (pat.yarns.any { it.id == saved.id }) pat.yarns.map { if (it.id == saved.id) saved else it } else pat.yarns + saved) } },
            onDelete = if (isNew) null else ({ editor.update { pat -> pat.copy(yarns = pat.yarns.filterNot { it.id == y.id }) } }),
            onDismiss = { editing = null })
    }
}

@Composable
private fun NeedleList(p: Pattern, editor: PatternEditor) {
    val app = LocalApp.current
    val units = unitFormat()
    val stash by app.repo.needleStash.collectAsState(emptyList())
    var editing by remember { mutableStateOf<Needle?>(null) }
    var isNew by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    SectionHeader("Needles & tools")
    Group {
        p.needles.forEach { n ->
            ListRow(Needles.label(n.sizeMm, units.imperial), subtitle = needleSummary(n, units), leading = { KIcon(Icons.Needle, Knit.colors.secondaryLabel, 22.dp) }, chevron = true) { isNew = false; editing = n }
            RowDivider(50.dp)
        }
        Box {
            ListRow("Add needle", titleColor = Knit.colors.accent, leading = { KIcon(Icons.Plus, Knit.colors.accent, 22.dp) }) {
                if (stash.isEmpty()) { isNew = true; editing = Needle(id = newId(), sizeMm = p.gauge?.needleMm ?: 4.0) } else menu = true
            }
            Menu(menu, { menu = false }, listOf(MenuItem("New needle", Icons.Plus) { isNew = true; editing = Needle(id = newId()) }) +
                studio.knit.stash.groupByBrand(stash).flatMap { it.needles }.map { s -> MenuItem("From stash: ${Needles.label(s.sizeMm, units.imperial)} ${s.type.label.lowercase()}" + (s.brand.trim().takeIf { it.isNotEmpty() }?.let { " · $it" } ?: "")) { editor.update { it.copy(needles = it.needles + s.copy(id = newId())) } } })
        }
    }
    editing?.let { n ->
        NeedleSheet(n, isNew,
            onSave = { saved -> editor.update { pat -> pat.copy(needles = if (pat.needles.any { it.id == saved.id }) pat.needles.map { if (it.id == saved.id) saved else it } else pat.needles + saved) } },
            onDelete = if (isNew) null else ({ editor.update { pat -> pat.copy(needles = pat.needles.filterNot { it.id == n.id }) } }),
            onDismiss = { editing = null },
            brands = studio.knit.stash.knownBrands(stash + p.needles))
    }
}

/** How much yarn the written pattern needs, compared with what is listed. */
@Composable
private fun YarnEstimate(p: Pattern, library: StitchLibrary) {
    val units = unitFormat()
    val gauge = p.gauge
    SectionHeader("Yarn estimate")
    if (gauge == null || gauge.stitchesPer10cm <= 0 || gauge.rowsPer10cm <= 0) {
        SectionFooter("Set the gauge on the Overview tab to estimate how much yarn this pattern needs.")
        return
    }
    val sizes = if (p.sizes.isEmpty()) listOf("") else p.sizes
    val rows = remember(p.sections, p.charts, p.sizes, gauge, library) {
        sizes.indices.map { i -> YarnCalc.totalStitches(p, i, library)?.let { it to YarnCalc.forStitches(it, gauge) } }
    }
    if (rows.all { it == null }) {
        SectionFooter("Add rows with known stitch counts (set a cast-on count) and the amount of yarn is worked out here.")
        return
    }
    val have = p.yarns.mapNotNull { it.totalMeters }.sum()
    Group {
        sizes.forEachIndexed { i, size ->
            if (i > 0) RowDivider()
            val r = rows[i]
            ListRow(
                if (size.isEmpty()) "This pattern" else "Size $size",
                subtitle = r?.let { (stitches, _) -> "$stitches stitches  ·  about ${units.length(YarnCalc.cmPerStitch(gauge), 1)} each" } ?: "Stitch count not known for every row",
                value = r?.second?.let { "≈ ${units.yarn(it.meters)}" } ?: "—",
            )
        }
        if (have > 0) {
            RowDivider()
            val need = rows.filterNotNull().maxOfOrNull { it.second.meters } ?: 0.0
            ListRow("Listed yarn", value = units.yarn(have), trailing = {
                if (have >= need) KIcon(Icons.CheckCircle, Knit.colors.success, 20.dp, description = "Enough") else KIcon(Icons.Warning, Knit.colors.warning, 20.dp, description = "Not enough")
            })
        }
    }
    SectionFooter("An estimate for plain knitting at your gauge, including a 10% margin. Cables use more yarn, lace less. For accuracy, weigh a swatch with the yarn calculator in Tools.")
}
