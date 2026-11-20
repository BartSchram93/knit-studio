package studio.knit.ui.pattern

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import studio.knit.companion.statusLine
import studio.knit.model.Category
import studio.knit.model.Difficulty
import studio.knit.model.Gauge
import studio.knit.model.Measurement
import studio.knit.model.Pattern
import studio.knit.stitch.StitchLibrary
import studio.knit.ui.app.LocalApp
import studio.knit.ui.app.LocalNav
import studio.knit.ui.app.PatternEditor
import studio.knit.ui.app.formatDate
import studio.knit.ui.app.unitFormat
import studio.knit.ui.components.BoundField
import studio.knit.ui.components.BoundNumberField
import studio.knit.ui.components.Chip
import studio.knit.ui.components.ConfirmDialog
import studio.knit.ui.components.EmptyState
import studio.knit.ui.components.Group
import studio.knit.ui.components.IconBtn
import studio.knit.ui.components.Icons
import studio.knit.ui.components.KIcon
import studio.knit.ui.components.ListRow
import studio.knit.ui.components.Menu
import studio.knit.ui.components.MenuButton
import studio.knit.ui.components.MenuItem
import studio.knit.ui.components.Page
import studio.knit.ui.components.PagePadding
import studio.knit.ui.components.PickerRow
import studio.knit.ui.components.PrimaryButton
import studio.knit.ui.components.PromptDialog
import studio.knit.ui.components.RowDivider
import studio.knit.ui.components.SectionFooter
import studio.knit.ui.components.SectionHeader
import studio.knit.ui.components.Segmented
import studio.knit.ui.components.SoftButton
import studio.knit.ui.components.SwitchRow
import studio.knit.ui.components.pressable
import studio.knit.ui.export.ExportSheet
import studio.knit.ui.platform.decodeImage
import studio.knit.ui.platform.rememberPlatformActions
import studio.knit.ui.theme.Knit
import studio.knit.util.newId
import studio.knit.util.nowMillis
import studio.knit.validate.Issue
import studio.knit.validate.Severity
import studio.knit.validate.Validator

private val TABS = listOf("Overview", "Charts", "Rows", "Materials", "Check")
private val SIZE_PRESETS = listOf(
    "XS – XXL" to listOf("XS", "S", "M", "L", "XL", "XXL"),
    "S / M / L" to listOf("S", "M", "L"),
    "Child 2 – 10" to listOf("2", "4", "6", "8", "10"),
    "One size" to emptyList(),
)
private val MEASUREMENT_NAMES = listOf(
    "Chest circumference", "Waist circumference", "Hip circumference", "Body length", "Sleeve length",
    "Shoulder width", "Neck circumference", "Head circumference", "Foot length", "Width", "Length",
)

/**
 * The home of one pattern. Five tabs keep details, charts, written rows, materials and the
 * validation report one tap apart.
 */
@Composable
fun PatternScreen(patternId: String, initialTab: Int) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val editor = remember(patternId) { app.editor(patternId) }
    LaunchedEffect(editor) { editor.refresh() }
    val pattern by editor.state.collectAsState()
    val loaded by editor.loaded.collectAsState()
    val library by app.library.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(initialTab.coerceIn(0, TABS.size - 1)) }
    var exporting by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    val p = pattern
    if (p == null) {
        Page("Pattern", onBack = nav::back) {
            if (loaded) item { EmptyState(Icons.Document, "Pattern not found", "It may have been deleted.") }
        }
        return
    }
    val issues = remember(p.charts, p.sections, p.sizes, p.gauge, p.measurements, p.name, library) { Validator.validate(p, library) }
    val errorCount = issues.count { it.severity == Severity.ERROR }
    val warningCount = issues.count { it.severity == Severity.WARNING }

    Page(
        p.displayName, onBack = nav::back, backLabel = "Patterns",
        actions = {
            IconBtn(if (p.favorite) Icons.StarFilled else Icons.Star, if (p.favorite) "Remove from favourites" else "Add to favourites", { editor.update { it.copy(favorite = !it.favorite) } })
            MenuButton(listOf(
                MenuItem("Export & share", Icons.Share) { exporting = true },
                MenuItem("Duplicate", Icons.Copy) {
                    scope.launch { editor.flushAndWait(); app.repo.duplicate(p.id)?.let { app.message("Duplicated"); nav.openPattern(it.id) } }
                },
                MenuItem(if (p.archived) "Restore from archive" else "Archive", Icons.Archive) { editor.update { it.copy(archived = !it.archived) } },
                MenuItem("Delete", Icons.Trash, destructive = true) { deleting = true },
            ))
        },
    ) {
        item {
            val labels = TABS.mapIndexed { i, t -> if (i == 4 && errorCount + warningCount > 0) "Check · ${errorCount + warningCount}" else t }
            Segmented(labels, tab, { tab = it }, Modifier.padding(horizontal = PagePadding).padding(top = 4.dp).fillMaxWidth())
        }
        when (tab) {
            0 -> overviewTab(p, editor, library, onKnit = { nav.knit(p.id) }, onExport = { exporting = true }, errorCount = errorCount, onCheck = { tab = 4 })
            1 -> chartsTab(p, editor, library, nav)
            2 -> rowsTab(p, editor, library, nav)
            3 -> materialsTab(p, editor, library)
            else -> checkTab(p, issues, nav)
        }
    }

    if (exporting) ExportSheet(p, editor) { exporting = false }
    if (deleting) {
        ConfirmDialog("Delete “${p.displayName}”?", "The pattern, its charts, rows and knitting progress will be removed from this device. This cannot be undone.",
            "Delete", { scope.launch { app.release(p.id); app.repo.delete(p.id); nav.back() } }, { deleting = false }, destructive = true)
    }
}

// ---------------------------------------------------------------------------------------------
// Overview
// ---------------------------------------------------------------------------------------------

private fun LazyListScope.overviewTab(
    p: Pattern, editor: PatternEditor, library: StitchLibrary,
    onKnit: () -> Unit, onExport: () -> Unit, errorCount: Int, onCheck: () -> Unit,
) {
    item {
        val status = remember(p, library) { statusLine(p, library) }
        val canKnit = p.sections.any { s -> s.rows.isNotEmpty() } || p.charts.isNotEmpty()
        val nav = studio.knit.ui.app.LocalNav.current
        val pdf = p.files.firstOrNull()
        Row(Modifier.padding(horizontal = PagePadding).padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!canKnit && pdf != null) {
                // A pattern that is only a PDF: reading it is the way to knit it.
                PrimaryButton("Open PDF", { nav.openPdf(p.id, pdf.id) }, Modifier.weight(1.4f), icon = Icons.Document)
            } else {
                PrimaryButton(if (p.progress == null) "Start knitting" else "Continue", onKnit, Modifier.weight(1.4f), icon = Icons.Play, enabled = canKnit)
            }
            SoftButton("Export", onExport, Modifier.weight(1f), icon = Icons.Share)
        }
        if (status != null) SectionFooter(status)
        if (errorCount > 0) {
            Row(
                Modifier.padding(horizontal = PagePadding).padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(Knit.colors.error.copy(alpha = 0.10f)).pressable(onClick = onCheck).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KIcon(Icons.Error, Knit.colors.error, 20.dp)
                Spacer(Modifier.width(10.dp))
                Text("$errorCount ${if (errorCount == 1) "problem" else "problems"} found in this pattern", style = Knit.type.subhead, color = Knit.colors.label, modifier = Modifier.weight(1f))
                KIcon(Icons.ChevronRight, Knit.colors.tertiaryLabel, 15.dp)
            }
        }
    }

    // Knitting statistics
    item {
        val today = remember { studio.knit.stats.dayKey(nowMillis()) }
        val stats = remember(p.progress?.days, today) { studio.knit.stats.statsOf(listOfNotNull(p.progress?.days), today) }
        if (!stats.isEmpty) {
            SectionHeader("This project")
            studio.knit.ui.tools.StatsBlock(stats)
        }
    }

    item { SectionHeader("Details") }
    item {
        Column(Modifier.padding(horizontal = PagePadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BoundField(p.name, { v -> editor.update { it.copy(name = v) } }, Modifier.fillMaxWidth(), "Name", "Untitled pattern", capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words)
            BoundField(p.description, { v -> editor.update { it.copy(description = v) } }, Modifier.fillMaxWidth(), "Description", "What is it, who is it for?", singleLine = false, minLines = 2)
        }
    }
    item {
        Spacer(Modifier.height(14.dp))
        Group {
            PickerRow("Category", Category.entries, p.category, { it.label }) { v -> editor.update { it.copy(category = v) } }
            RowDivider()
            PickerRow("Difficulty", Difficulty.entries, p.difficulty, { it.label }) { v -> editor.update { it.copy(difficulty = v) } }
        }
    }

    // Sizes
    item {
        var presets by remember { mutableStateOf(false) }
        SectionHeader("Sizes", action = {
            Box {
                Text("Presets", style = Knit.type.subhead, color = Knit.colors.accent, modifier = Modifier.pressable { presets = true }.padding(4.dp))
                Menu(presets, { presets = false }, SIZE_PRESETS.map { (label, sizes) -> MenuItem(label) { editor.update { it.copy(sizes = sizes) } } })
            }
        })
    }
    item {
        var adding by remember { mutableStateOf(false) }
        var removing by remember { mutableStateOf<Int?>(null) }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = PagePadding), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (p.sizes.isEmpty()) Chip("One size", true, {})
            p.sizes.forEachIndexed { i, s -> Chip(s, false, { removing = i }) }
            Chip("Add size", false, { adding = true }, leading = { KIcon(Icons.Plus, Knit.colors.accent, 15.dp) })
        }
        SectionFooter(
            if (p.sizes.size > 1) "Write counts as ${listOf(10, 12, 14, 16, 18, 20).take(p.sizes.size).let { "${it.first()} (${it.drop(1).joinToString(", ")})" }} — smallest size first. Tap a size to remove it."
            else "Add sizes to write counts like 10 (12, 14) and have every size checked.",
        )
        if (adding) PromptDialog("Add size", "", "Add", { v -> editor.update { it.copy(sizes = it.sizes + v) } }, { adding = false }, placeholder = "e.g. M or 38")
        removing?.let { i ->
            ConfirmDialog("Remove size “${p.sizes.getOrElse(i) { "" }}”?", "Counts written for this size stay in the rows and will be flagged by Check until you remove them.",
                "Remove", { editor.update { it.copy(sizes = it.sizes.filterIndexed { k, _ -> k != i }) } }, { removing = null }, destructive = true)
        }
    }

    // Gauge
    item { GaugeEditor(p, editor) }

    // Measurements
    item {
        var open by remember { mutableStateOf(false) }
        var custom by remember { mutableStateOf(false) }
        SectionHeader("Measurements", action = {
            Box {
                KIcon(Icons.Plus, Knit.colors.accent, 22.dp, Modifier.pressable { open = true }.padding(2.dp), "Add measurement")
                Menu(open, { open = false }, MEASUREMENT_NAMES.filter { n -> p.measurements.none { it.name == n } }.map { n ->
                    MenuItem(n) { editor.update { it.copy(measurements = it.measurements + Measurement(newId(), n)) } }
                } + MenuItem("Custom…", Icons.Pencil) { custom = true })
            }
        })
        if (custom) PromptDialog("Custom measurement", "", "Add", { n -> editor.update { it.copy(measurements = it.measurements + Measurement(newId(), n)) } }, { custom = false }, placeholder = "Name")
    }
    if (p.measurements.isEmpty()) {
        item { SectionFooter("Finished measurements appear in the exported pattern, one column per size.") }
    }
    items(p.measurements.size, key = { p.measurements[it].id }) { index ->
        MeasurementEditor(p, p.measurements[index], editor)
    }

    // Notes
    item { SectionHeader("Notes") }
    item {
        BoundField(p.notes, { v -> editor.update { it.copy(notes = v) } }, Modifier.padding(horizontal = PagePadding).fillMaxWidth(),
            placeholder = "Blocking, finishing, modifications…", singleLine = false, minLines = 3)
    }

    // PDF patterns
    item { SectionHeader("PDF pattern") }
    item { PdfList(p, editor) }

    // Photos
    item { SectionHeader("Reference photos") }
    item { PhotoStrip(p, editor) }

    item { SectionHeader("Status") }
    item {
        val now = remember(p.updatedAt) { nowMillis() }
        Group {
            SwitchRow("Draft", p.draft, { v -> editor.update { it.copy(draft = v) } }, "Turn off when the design is finished")
            RowDivider()
            ListRow("Created", value = formatDate(p.createdAt, now))
            RowDivider()
            ListRow("Last changed", value = formatDate(p.updatedAt, now))
        }
    }
}

@Composable
private fun GaugeEditor(p: Pattern, editor: PatternEditor) {
    val app = LocalApp.current
    val settings by app.settings.collectAsState()
    val units = unitFormat()
    // Gauge is stored per 10 cm; over 4 in (10.16 cm) the counts are 1.6 % larger.
    val factor = if (units.imperial) 1.016 else 1.0
    SectionHeader(if (settings.ukTerms) "Tension over ${units.gaugeSpan}" else "Gauge over ${units.gaugeSpan}")
    Column(Modifier.padding(horizontal = PagePadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            fun set(sts: Double?, rows: Double?) = editor.update {
                val g = it.gauge ?: Gauge(0.0, 0.0)
                val next = g.copy(stitchesPer10cm = sts ?: g.stitchesPer10cm, rowsPer10cm = rows ?: g.rowsPer10cm)
                it.copy(gauge = if (next.stitchesPer10cm <= 0 && next.rowsPer10cm <= 0) null else next)
            }
            BoundNumberField(p.gauge?.stitchesPer10cm?.takeIf { it > 0 }?.times(factor), { v -> set((v ?: 0.0) / factor, null) }, Modifier.weight(1f), "Stitches", "sts")
            BoundNumberField(p.gauge?.rowsPer10cm?.takeIf { it > 0 }?.times(factor), { v -> set(null, (v ?: 0.0) / factor) }, Modifier.weight(1f), "Rows", "rows")
        }
        p.gauge?.let { g ->
            BoundField(g.over, { v -> editor.update { it.copy(gauge = it.gauge?.copy(over = v)) } }, Modifier.fillMaxWidth(), "Measured over",
                if (settings.ukTerms) "stocking stitch" else "stockinette stitch", capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.None)
        }
    }
}

@Composable
private fun MeasurementEditor(p: Pattern, m: Measurement, editor: PatternEditor) {
    val units = unitFormat()
    val sizes = if (p.sizes.isEmpty()) listOf("") else p.sizes
    fun update(transform: (Measurement) -> Measurement) = editor.update { pat -> pat.copy(measurements = pat.measurements.map { if (it.id == m.id) transform(it) else it }) }
    Column(Modifier.padding(horizontal = PagePadding).padding(bottom = 10.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Knit.colors.surface).padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(m.name, style = Knit.type.headline, color = Knit.colors.label, modifier = Modifier.weight(1f))
            IconBtn(Icons.Trash, "Remove ${m.name}", { editor.update { it.copy(measurements = it.measurements.filterNot { x -> x.id == m.id }) } }, tint = Knit.colors.secondaryLabel, size = 19.dp)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(end = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            sizes.forEachIndexed { i, size ->
                BoundNumberField(
                    m.valuesCm.getOrNull(i)?.takeIf { it > 0 }?.let { units.lengthValue(it) },
                    { v ->
                        update { old ->
                            val list = MutableList(sizes.size) { k -> old.valuesCm.getOrElse(k) { 0.0 } }
                            list[i] = v?.let { units.toCm(it) } ?: 0.0
                            old.copy(valuesCm = list)
                        }
                    },
                    Modifier.width(if (sizes.size == 1) 150.dp else 104.dp), size.ifEmpty { null }, units.lengthUnit,
                )
            }
        }
    }
}

@Composable
private fun PhotoStrip(p: Pattern, editor: PatternEditor) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val platform = rememberPlatformActions()
    var removing by remember { mutableStateOf<String?>(null) }
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = PagePadding), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (id in p.imageIds) {
            val bitmap by produceState<ImageBitmap?>(null, id) { value = app.repo.image(id)?.let { decodeImage(it) } }
            Box(Modifier.size(104.dp).clip(RoundedCornerShape(12.dp)).background(Knit.colors.fill).pressable { removing = id }, contentAlignment = Alignment.Center) {
                bitmap?.let { Image(it, "Reference photo", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            }
        }
        Box(
            Modifier.size(104.dp).clip(RoundedCornerShape(12.dp)).background(Knit.colors.surface).pressable {
                platform.pickImage { bytes ->
                    if (bytes == null) return@pickImage
                    scope.launch {
                        val id = app.repo.addImage(p.id, bytes)
                        editor.update { it.copy(imageIds = it.imageIds + id) }
                    }
                }
            },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                KIcon(Icons.Photo, Knit.colors.accent, 26.dp)
                Spacer(Modifier.height(4.dp))
                Text("Add photo", style = Knit.type.footnote, color = Knit.colors.accent)
            }
        }
    }
    SectionFooter("The first photo is used on the cover of the exported pattern.")
    removing?.let { id ->
        ConfirmDialog("Remove this photo?", "It will be deleted from the pattern.", "Remove",
            { editor.update { it.copy(imageIds = it.imageIds - id) }; scope.launch { app.repo.deleteImage(id) } }, { removing = null }, destructive = true)
    }
}

// ---------------------------------------------------------------------------------------------
// Check
// ---------------------------------------------------------------------------------------------

private fun LazyListScope.checkTab(p: Pattern, issues: List<Issue>, nav: studio.knit.ui.app.Nav) {
    if (issues.isEmpty()) {
        item { EmptyState(Icons.CheckCircle, "Everything adds up", "Stitch counts, repeats and sizes are consistent.", Modifier.padding(top = 24.dp)) }
        return
    }
    val groups = listOf(
        Triple(Severity.ERROR, "Problems", "These make the pattern impossible to knit as written."),
        Triple(Severity.WARNING, "Worth a look", "Unusual, but possibly intended."),
        Triple(Severity.INFO, "Suggestions", ""),
    )
    for ((severity, title, footer) in groups) {
        val list = issues.filter { it.severity == severity }
        if (list.isEmpty()) continue
        item { SectionHeader("$title · ${list.size}") }
        item {
            Group {
                list.forEachIndexed { i, issue ->
                    if (i > 0) RowDivider(50.dp)
                    val loc = issue.location
                    val target: (() -> Unit)? = when {
                        loc.chartId != null -> { { nav.openChart(p.id, loc.chartId!!, loc.row ?: -1, loc.col ?: -1) } }
                        loc.sectionId != null -> { { nav.openSection(p.id, loc.sectionId!!, loc.rowId ?: "") } }
                        else -> null
                    }
                    IssueRow(issue, target)
                }
            }
        }
        if (footer.isNotEmpty()) item { SectionFooter(footer) }
    }
    item { SectionFooter("Knit Studio never changes your pattern on its own. Tap an item to go to it.") }
}

@Composable
fun IssueRow(issue: Issue, onClick: (() -> Unit)?) {
    val (icon, color) = when (issue.severity) {
        Severity.ERROR -> Icons.Error to Knit.colors.error
        Severity.WARNING -> Icons.Warning to Knit.colors.warning
        Severity.INFO -> Icons.Info to Knit.colors.info
    }
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.pressable(onClick = onClick) else Modifier).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.Top,
    ) {
        KIcon(icon, color, 22.dp, Modifier.padding(top = 1.dp), description = issue.severity.name.lowercase())
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(issue.where, style = Knit.type.footnote.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = Knit.colors.secondaryLabel)
            Text(issue.message, style = Knit.type.callout, color = Knit.colors.label)
        }
        if (onClick != null) { Spacer(Modifier.width(6.dp)); KIcon(Icons.ChevronRight, Knit.colors.tertiaryLabel, 15.dp, Modifier.padding(top = 4.dp)) }
    }
}

@Composable
private fun PdfList(p: Pattern, editor: PatternEditor) {
    val app = LocalApp.current
    val nav = studio.knit.ui.app.LocalNav.current
    val scope = rememberCoroutineScope()
    val platform = rememberPlatformActions()
    var removing by remember { mutableStateOf<studio.knit.model.AttachedFile?>(null) }
    Group {
        p.files.forEach { f ->
            val rows = p.progress?.counters?.firstOrNull { it.id == pdfCounterId(f) }?.value
            ListRow(
                f.name, subtitle = listOfNotNull(formatSize(f.sizeBytes), rows?.let { "row $it" }).joinToString("  ·  "),
                leading = { KIcon(Icons.Document, Knit.colors.accent, 24.dp) }, chevron = true,
                onLongClick = { removing = f },
            ) { nav.openPdf(p.id, f.id) }
            RowDivider(54.dp)
        }
        ListRow("Add PDF", titleColor = Knit.colors.accent, leading = { KIcon(Icons.Plus, Knit.colors.accent, 22.dp) }) {
            platform.pickFile { picked ->
                if (picked == null) return@pickFile
                scope.launch {
                    val f = storePickedPdf(app, picked) ?: return@launch
                    editor.update { it.copy(files = it.files + f) }
                    nav.openPdf(p.id, f.id)
                }
            }
        }
    }
    SectionFooter(if (p.files.isEmpty()) "Keep the PDF of a bought or downloaded pattern here and read it while you knit, with a row counter." else "Touch and hold a PDF to remove it.")
    removing?.let { f ->
        ConfirmDialog("Remove “${f.name}”?", "The PDF and its row counter are removed from this pattern.", "Remove",
            { removePdf(app, editor, f, scope) }, { removing = null }, destructive = true)
    }
}

fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000 -> "${(bytes / 100_000) / 10.0} MB"
    bytes >= 1_000 -> "${bytes / 1_000} KB"
    else -> "$bytes bytes"
}
