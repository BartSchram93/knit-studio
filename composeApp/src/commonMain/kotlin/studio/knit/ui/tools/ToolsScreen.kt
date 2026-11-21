package studio.knit.ui.tools

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import studio.knit.calc.GaugeCalc
import studio.knit.calc.Needles
import studio.knit.calc.YarnCalc
import studio.knit.calc.pretty
import studio.knit.model.Gauge
import studio.knit.model.Needle
import studio.knit.model.Yarn
import studio.knit.ui.app.LocalApp
import studio.knit.ui.app.LocalNav
import studio.knit.ui.app.unitFormat
import studio.knit.stash.groupByBrand
import studio.knit.stash.groupBySize
import studio.knit.stash.knownBrands
import studio.knit.stash.moveNotesToBrand
import studio.knit.stash.needlesWithBrandInNotes
import studio.knit.stash.nextCable
import studio.knit.stash.nextInSet
import studio.knit.model.Cable
import studio.knit.model.NeedleType
import studio.knit.ui.pattern.CableSheet
import studio.knit.ui.pattern.cableSummary
import studio.knit.ui.pattern.cableTitle
import studio.knit.ui.components.ConfirmDialog
import studio.knit.ui.components.Group
import studio.knit.ui.components.Icons
import studio.knit.ui.components.KIcon
import studio.knit.ui.components.ListRow
import studio.knit.ui.components.Menu
import studio.knit.ui.components.MenuItem
import studio.knit.ui.components.NumberField
import studio.knit.ui.components.Page
import studio.knit.ui.components.PagePadding
import studio.knit.ui.components.RowDivider
import studio.knit.ui.components.SectionFooter
import studio.knit.ui.components.SectionHeader
import studio.knit.ui.components.Segmented
import studio.knit.ui.components.SoftButton
import studio.knit.ui.components.toDoubleLenient
import studio.knit.ui.pattern.ColorDot
import studio.knit.ui.pattern.YarnPhoto
import studio.knit.ui.pattern.NeedleSheet
import studio.knit.ui.pattern.YarnSheet
import studio.knit.ui.pattern.needleSummary
import studio.knit.ui.pattern.yarnSummary
import studio.knit.ui.theme.Knit
import studio.knit.util.newId

@Composable
private fun ToolIcon(icon: ImageVector) {
    Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(Knit.colors.accentSoft), contentAlignment = Alignment.Center) {
        KIcon(icon, Knit.colors.accent, 19.dp)
    }
}

@Composable
fun ToolsScreen() {
    val nav = LocalNav.current
    Page("Tools") {
        item { SectionHeader("Calculators") }
        item {
            Group {
                ListRow("Gauge", subtitle = "Measure a swatch and compare it with a pattern", leading = { ToolIcon(Icons.Ruler) }, chevron = true) { nav.tool("gauge") }
                RowDivider(60.dp)
                ListRow("Cast-on", subtitle = "Stitches and rows for a size, in your stitch multiple", leading = { ToolIcon(Icons.Calculator) }, chevron = true) { nav.tool("caston") }
                RowDivider(60.dp)
                ListRow("Yarn amount", subtitle = "How much yarn a piece will take", leading = { ToolIcon(Icons.Yarn) }, chevron = true) { nav.tool("yarn") }
                RowDivider(60.dp)
                ListRow("Needle sizes", subtitle = "Metric, US and UK", leading = { ToolIcon(Icons.Needle) }, chevron = true) { nav.tool("needles") }
            }
        }
        item { SectionHeader("Library") }
        item {
            Group {
                ListRow("Stitch library", subtitle = "Symbols, abbreviations and your own stitches", leading = { ToolIcon(Icons.Book) }, chevron = true) { nav.tool("stitches") }
                RowDivider(60.dp)
                ListRow("Yarn & needle stash", subtitle = "What you own, ready to add to a pattern", leading = { ToolIcon(Icons.Layers) }, chevron = true) { nav.tool("stash") }
                RowDivider(60.dp)
                ListRow("Knitting stats", subtitle = "Time knitted, rows per day, streaks", leading = { ToolIcon(Icons.Counter) }, chevron = true) { nav.tool("stats") }
                RowDivider(66.dp)
                ListRow("Achievements", subtitle = "Your level, badges and hidden surprises", leading = { ToolIcon(Icons.Sparkle) }, chevron = true) { nav.tool("achievements") }
            }
        }
    }
}

@Composable
fun ToolScreen(tool: String) {
    when (tool) {
        "gauge" -> GaugeTool()
        "caston" -> CastOnTool()
        "yarn" -> YarnTool()
        "needles" -> NeedleTool()
        "stitches" -> StitchLibraryScreen()
        "stats" -> StatsScreen()
        "achievements" -> AchievementsScreen()
        "year" -> YearReviewScreen()
        else -> StashScreen()
    }
}

/** A highlighted result with its explanation. */
@Composable
fun ResultCard(title: String, value: String, detail: String? = null, tone: Color = Knit.colors.accent) {
    Column(Modifier.padding(horizontal = PagePadding).padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Knit.colors.surface).padding(18.dp)) {
        Text(title.uppercase(), style = Knit.type.overline, color = Knit.colors.secondaryLabel)
        Spacer(Modifier.height(4.dp))
        Text(value, style = Knit.type.title1, color = tone)
        if (detail != null) { Spacer(Modifier.height(4.dp)); Text(detail, style = Knit.type.subhead, color = Knit.colors.secondaryLabel) }
    }
}

@Composable
private fun TwoFields(a: @Composable (Modifier) -> Unit, b: @Composable (Modifier) -> Unit) {
    Row(Modifier.padding(horizontal = PagePadding), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        a(Modifier.weight(1f)); b(Modifier.weight(1f))
    }
}

// ---------------------------------------------------------------------------------------------
// Gauge
// ---------------------------------------------------------------------------------------------

@Composable
private fun GaugeTool() {
    val app = LocalApp.current
    val nav = LocalNav.current
    val units = unitFormat()
    val settings by app.settings.collectAsState()
    val patterns by app.patterns.collectAsState()
    val scope = rememberCoroutineScope()
    val span = if (units.imperial) "4" else "10"
    var sts by rememberSaveable { mutableStateOf("") }
    var width by rememberSaveable(units.imperial) { mutableStateOf(span) }
    var rows by rememberSaveable { mutableStateOf("") }
    var height by rememberSaveable(units.imperial) { mutableStateOf(span) }
    var targetSts by rememberSaveable { mutableStateOf("") }
    var targetRows by rememberSaveable { mutableStateOf("") }
    var patternCount by rememberSaveable { mutableStateOf("") }
    var pick by remember { mutableStateOf(false) }
    var saveTo by remember { mutableStateOf(false) }
    val word = if (settings.ukTerms) "Tension" else "Gauge"
    val factor = if (units.imperial) 1.016 else 1.0

    val mine = run {
        val s = sts.toDoubleLenient(); val w = width.toDoubleLenient(); val r = rows.toDoubleLenient(); val h = height.toDoubleLenient()
        if (s != null && w != null && r != null && h != null) GaugeCalc.fromSwatch(s, r, units.toCm(w), units.toCm(h)) else null
    }
    val target = run {
        val s = targetSts.toDoubleLenient(); val r = targetRows.toDoubleLenient()
        if (s != null && s > 0) Gauge(s / factor, (r ?: 0.0).takeIf { it > 0 }?.div(factor) ?: (mine?.rowsPer10cm ?: 1.0)) else null
    }

    Page(word, onBack = nav::back, backLabel = "Tools", largeTitle = false) {
        item { SectionHeader("Your swatch") }
        item {
            TwoFields({ NumberField(sts, { sts = it }, it, "Stitches counted", "sts") }, { NumberField(width, { width = it }, it, "Over a width of", units.lengthUnit) })
            Spacer(Modifier.height(12.dp))
            TwoFields({ NumberField(rows, { rows = it }, it, "Rows counted", "rows") }, { NumberField(height, { height = it }, it, "Over a height of", units.lengthUnit) })
            SectionFooter("Count over as wide a stretch as you can, away from the edges, after washing and drying the swatch as you will the finished piece.")
        }
        if (mine != null) {
            item {
                ResultCard("Your ${word.lowercase()}", units.gauge(mine), "${(mine.stitchesPer10cm / 10).pretty(2)} sts and ${(mine.rowsPer10cm / 10).pretty(2)} rows per cm")
                Box(Modifier.padding(horizontal = PagePadding).padding(top = 10.dp)) {
                    SoftButton("Use for a pattern", { saveTo = true }, Modifier.fillMaxWidth(), icon = Icons.Check, compact = true, enabled = !patterns.isNullOrEmpty())
                    Menu(saveTo, { saveTo = false }, patterns.orEmpty().filter { !it.archived }.take(12).map { p ->
                        MenuItem(p.displayName) {
                            scope.launch {
                                val current = app.editor(p.id).current ?: app.repo.getPattern(p.id) ?: return@launch
                                app.repo.save(current.copy(gauge = (current.gauge ?: mine).copy(stitchesPer10cm = mine.stitchesPer10cm, rowsPer10cm = mine.rowsPer10cm)))
                                app.editor(p.id).refresh()
                                app.message("$word saved to “${p.displayName}”")
                            }
                        }
                    })
                }
            }
        }

        item {
            SectionHeader("Pattern ${word.lowercase()} over ${units.gaugeSpan}")
        }
        item {
            TwoFields({ NumberField(targetSts, { targetSts = it }, it, "Stitches", "sts") }, { NumberField(targetRows, { targetRows = it }, it, "Rows", "rows") })
            val withGauge = patterns.orEmpty().filter { it.gauge != null }
            if (withGauge.isNotEmpty()) {
                Box(Modifier.padding(horizontal = PagePadding).padding(top = 10.dp)) {
                    SoftButton("Fill in from one of my patterns", { pick = true }, Modifier.fillMaxWidth(), compact = true)
                    Menu(pick, { pick = false }, withGauge.take(12).map { p ->
                        MenuItem(p.displayName) {
                            targetSts = (p.gauge!!.stitchesPer10cm * factor).pretty(1)
                            targetRows = (p.gauge!!.rowsPer10cm * factor).pretty(1)
                        }
                    })
                }
            }
        }
        if (mine != null && target != null) {
            val cmp = GaugeCalc.compare(target, mine)
            item {
                val tone = if (cmp.verdict == "On gauge") Knit.colors.success else Knit.colors.warning
                val percent = kotlin.math.abs(cmp.stitchPercent).pretty(1)
                ResultCard(
                    "Compared with the pattern",
                    if (settings.ukTerms) cmp.verdict.replace("gauge", "tension") else cmp.verdict,
                    (if (cmp.verdict == "On gauge") "" else "$percent% ${if (cmp.stitchPercent > 0) "more" else "fewer"} stitches. ") + cmp.advice +
                        " A piece meant to be ${units.length(100.0, 0)} wide would measure ${units.length(100.0 * cmp.widthFactor)}.",
                    tone,
                )
            }
            item { SectionHeader("Adjust a stitch count") }
            item {
                NumberField(patternCount, { patternCount = it }, Modifier.padding(horizontal = PagePadding).fillMaxWidth(), "The pattern says", "sts", decimal = false)
                val n = patternCount.toIntOrNull()
                if (n != null && n > 0) {
                    val converted = GaugeCalc.convertStitches(n, target, mine)
                    ResultCard("Work this many instead", "$converted sts", "Gives the same ${units.length(GaugeCalc.widthOf(n, target))} at your ${word.lowercase()}. Round to your stitch multiple with the cast-on calculator.")
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Cast-on
// ---------------------------------------------------------------------------------------------

@Composable
private fun CastOnTool() {
    val nav = LocalNav.current
    val units = unitFormat()
    val factor = if (units.imperial) 1.016 else 1.0
    var width by rememberSaveable { mutableStateOf("") }
    var length by rememberSaveable { mutableStateOf("") }
    var sts by rememberSaveable { mutableStateOf("") }
    var rows by rememberSaveable { mutableStateOf("") }
    var multiple by rememberSaveable { mutableStateOf("1") }
    var extra by rememberSaveable { mutableStateOf("0") }

    Page("Cast-on", onBack = nav::back, backLabel = "Tools", largeTitle = false) {
        item { SectionHeader("Finished size") }
        item { TwoFields({ NumberField(width, { width = it }, it, "Width", units.lengthUnit) }, { NumberField(length, { length = it }, it, "Length (optional)", units.lengthUnit) }) }
        item { SectionHeader("Gauge over ${units.gaugeSpan}") }
        item { TwoFields({ NumberField(sts, { sts = it }, it, "Stitches", "sts") }, { NumberField(rows, { rows = it }, it, "Rows (optional)", "rows") }) }
        item { SectionHeader("Stitch pattern") }
        item {
            TwoFields({ NumberField(multiple, { multiple = it }, it, "Multiple of", "sts", decimal = false) }, { NumberField(extra, { extra = it }, it, "Plus", "sts", decimal = false) })
            SectionFooter("For 2×2 rib that begins and ends with k2, enter a multiple of 4 plus 2. Leave at 1 and 0 for plain knitting.")
        }
        val w = width.toDoubleLenient()
        val s = sts.toDoubleLenient()
        if (w != null && s != null && w > 0 && s > 0) {
            val gauge = Gauge(s / factor, (rows.toDoubleLenient() ?: 1.0).coerceAtLeast(0.01) / factor)
            val m = (multiple.toIntOrNull() ?: 1).coerceAtLeast(1)
            val e = (extra.toIntOrNull() ?: 0).coerceAtLeast(0)
            val exact = GaugeCalc.stitchesFor(units.toCm(w), gauge)
            val count = GaugeCalc.roundToMultiple(exact, m, e)
            item {
                ResultCard("Cast on", "$count stitches",
                    "Exactly ${exact.pretty(1)} stitches would be needed; $count is the nearest count that fits ${if (m == 1) "" else "a multiple of $m"}${if (e > 0) " plus $e" else ""}".trimEnd() +
                        ". The piece will measure ${units.length(GaugeCalc.widthOf(count, gauge))}.")
            }
            val l = length.toDoubleLenient()
            val r = rows.toDoubleLenient()
            if (l != null && r != null && l > 0 && r > 0) {
                val rowCount = GaugeCalc.rowsFor(units.toCm(l), gauge)
                item { ResultCard("Work", "${kotlin.math.round(rowCount).toInt()} rows", "For ${units.length(units.toCm(l))} at ${r.pretty(1)} rows per ${units.gaugeSpan}. That is ${count.toLong() * kotlin.math.round(rowCount).toLong()} stitches in all.") }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Yarn
// ---------------------------------------------------------------------------------------------

@Composable
private fun YarnTool() {
    val nav = LocalNav.current
    val units = unitFormat()
    val factor = if (units.imperial) 1.016 else 1.0
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var width by rememberSaveable { mutableStateOf("") }
    var length by rememberSaveable { mutableStateOf("") }
    var sts by rememberSaveable { mutableStateOf("") }
    var rows by rememberSaveable { mutableStateOf("") }
    var swatchW by rememberSaveable { mutableStateOf("") }
    var swatchH by rememberSaveable { mutableStateOf("") }
    var swatchWeight by rememberSaveable { mutableStateOf("") }
    var skeinLength by rememberSaveable { mutableStateOf("") }
    var skeinWeight by rememberSaveable { mutableStateOf("") }

    Page("Yarn amount", onBack = nav::back, backLabel = "Tools", largeTitle = false) {
        item { Segmented(listOf("From gauge", "From a weighed swatch"), mode, { mode = it }, Modifier.padding(horizontal = PagePadding).padding(top = 8.dp).fillMaxWidth()) }
        item { SectionHeader("The piece") }
        item {
            TwoFields({ NumberField(width, { width = it }, it, "Width", units.lengthUnit) }, { NumberField(length, { length = it }, it, "Length", units.lengthUnit) })
            SectionFooter("For a garment, add up the pieces: for example front, back and two sleeves as rectangles.")
        }
        if (mode == 0) {
            item { SectionHeader("Gauge over ${units.gaugeSpan}") }
            item { TwoFields({ NumberField(sts, { sts = it }, it, "Stitches", "sts") }, { NumberField(rows, { rows = it }, it, "Rows", "rows") }) }
        } else {
            item { SectionHeader("Your swatch") }
            item {
                TwoFields({ NumberField(swatchW, { swatchW = it }, it, "Width", units.lengthUnit) }, { NumberField(swatchH, { swatchH = it }, it, "Height", units.lengthUnit) })
                Spacer(Modifier.height(12.dp))
                NumberField(swatchWeight, { swatchWeight = it }, Modifier.padding(horizontal = PagePadding).fillMaxWidth(), "Weight of the swatch", units.weightUnit)
            }
        }
        item { SectionHeader("One skein") }
        item { TwoFields({ NumberField(skeinLength, { skeinLength = it }, it, "Length", units.yarnUnit) }, { NumberField(skeinWeight, { skeinWeight = it }, it, "Weight", units.weightUnit) }) }

        val w = width.toDoubleLenient()?.let { units.toCm(it) }
        val l = length.toDoubleLenient()?.let { units.toCm(it) }
        val skeinM = skeinLength.toDoubleLenient()?.takeIf { it > 0 }?.let { units.toMeters(it) }
        val skeinG = skeinWeight.toDoubleLenient()?.takeIf { it > 0 }?.let { units.toGrams(it) }
        if (w != null && l != null && w > 0 && l > 0) {
            if (mode == 0) {
                val s = sts.toDoubleLenient(); val r = rows.toDoubleLenient()
                if (s != null && r != null && s > 0 && r > 0) {
                    val e = YarnCalc.forArea(w, l, Gauge(s / factor, r / factor), skeinM, skeinG)
                    item {
                        ResultCard("You will need about", units.yarn(e.meters),
                            listOfNotNull(e.skeins?.let { "$it ${if (it == 1) "skein" else "skeins"}" }, e.grams?.let { units.weight(it) }, "includes a ${e.marginPercent}% margin").joinToString("  ·  "))
                        SectionFooter("Worked out from stitch density for plain knitting. Cables and ribbing use more, lace uses less. Weighing a swatch is more exact.")
                    }
                }
            } else {
                val sw = swatchW.toDoubleLenient()?.let { units.toCm(it) }; val sh = swatchH.toDoubleLenient()?.let { units.toCm(it) }
                val g = swatchWeight.toDoubleLenient()?.let { units.toGrams(it) }
                val e = if (sw != null && sh != null && g != null) YarnCalc.fromSwatchWeight(g, sw, sh, w * l, skeinM, skeinG) else null
                if (e != null) {
                    item {
                        ResultCard("You will need about", units.weight(e.grams ?: 0.0),
                            listOfNotNull(e.skeins?.let { "$it ${if (it == 1) "skein" else "skeins"}" }, e.meters.takeIf { it > 0 }?.let { units.yarn(it) }, "includes a ${e.marginPercent}% margin").joinToString("  ·  "))
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Needles
// ---------------------------------------------------------------------------------------------

@Composable
private fun NeedleTool() {
    val nav = LocalNav.current
    Page("Needle sizes", onBack = nav::back, backLabel = "Tools", largeTitle = false) {
        item {
            Row(Modifier.padding(horizontal = PagePadding + 16.dp).padding(top = 14.dp, bottom = 6.dp)) {
                for (h in listOf("Metric", "US", "UK / Canada")) Text(h.uppercase(), style = Knit.type.overline, color = Knit.colors.secondaryLabel, modifier = Modifier.weight(1f))
            }
        }
        item {
            Group {
                Needles.sizes.forEachIndexed { i, s ->
                    if (i > 0) RowDivider()
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 11.dp)) {
                        Text("${s.mm.pretty(2)} mm", style = Knit.type.body.copy(fontWeight = FontWeight.Medium), color = Knit.colors.label, modifier = Modifier.weight(1f))
                        Text(s.us ?: "—", style = Knit.type.body, color = if (s.us != null) Knit.colors.label else Knit.colors.tertiaryLabel, modifier = Modifier.weight(1f))
                        Text(s.uk ?: "—", style = Knit.type.body, color = if (s.uk != null) Knit.colors.label else Knit.colors.tertiaryLabel, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        item { SectionFooter("Metric is the diameter and the only exact measure. US numbers rise with thickness; old UK numbers fall.") }
    }
}

// ---------------------------------------------------------------------------------------------
// Stash
// ---------------------------------------------------------------------------------------------

@Composable
private fun StashScreen() {
    val app = LocalApp.current
    val nav = LocalNav.current
    val units = unitFormat()
    val settings by app.settings.collectAsState()
    val scope = rememberCoroutineScope()
    val yarns by app.repo.yarnStash.collectAsState(emptyList())
    val needles by app.repo.needleStash.collectAsState(emptyList())
    val cables by app.repo.cableStash.collectAsState(emptyList())
    var cable by remember { mutableStateOf<Cable?>(null) }
    var yarn by remember { mutableStateOf<Yarn?>(null) }
    var needle by remember { mutableStateOf<Needle?>(null) }
    var needleView by rememberSaveable { mutableIntStateOf(0) }
    // Open brand folders. A list, not a set, so it can be saved across configuration changes.
    var expandedList by rememberSaveable { mutableStateOf(listOf<String>()) }
    fun toggle(key: String) { expandedList = if (key in expandedList) expandedList - key else expandedList + key }
    fun open(key: String) { if (key !in expandedList) expandedList = expandedList + key }
    var confirmMove by remember { mutableStateOf(false) }
    val inNotes = remember(needles) { needlesWithBrandInNotes(needles) }

    Page("Stash", onBack = nav::back, backLabel = "Tools", largeTitle = false) {
        item { SectionHeader("Yarn") }
        item {
            Group {
                yarns.forEach { y ->
                    ListRow(y.displayName, subtitle = yarnSummary(y, units, settings.ukTerms).ifBlank { null }, leading = { YarnPhoto(y.photoId, y.colorArgb, 40.dp) }, chevron = true) { yarn = y }
                    RowDivider(68.dp)
                }
                ListRow("Add yarn", titleColor = Knit.colors.accent, leading = { KIcon(Icons.Plus, Knit.colors.accent, 22.dp) }) { yarn = Yarn(id = newId()) }
            }
            val total = yarns.mapNotNull { it.totalMeters }.sum()
            if (total > 0) SectionFooter("${units.yarn(total)} in ${yarns.size} ${if (yarns.size == 1) "yarn" else "yarns"}.")
        }
        item { SectionHeader("Needles") }
        if (needles.isNotEmpty()) item {
            Segmented(listOf("By brand", "By size"), needleView, { needleView = it }, Modifier.padding(horizontal = PagePadding).padding(bottom = 12.dp).fillMaxWidth())
        }
        if (inNotes.isNotEmpty()) item {
            Column(Modifier.padding(horizontal = PagePadding).padding(bottom = 12.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Knit.colors.accentSoft).padding(14.dp)) {
                Text(
                    "${inNotes.size} ${if (inNotes.size == 1) "needle has" else "needles have"} a brand written in the notes. Move it to the new Brand field so the needles are sorted into folders?",
                    style = Knit.type.subhead, color = Knit.colors.label,
                )
                Spacer(Modifier.height(10.dp))
                SoftButton("Move to Brand…", { confirmMove = true }, compact = true)
            }
        }
        if (needleView == 0) {
            val groups = groupByBrand(needles, cables)
            items(groups.size, key = { "brand-" + groups[it].key }) { i ->
                val g = groups[i]
                val open = g.key in expandedList || groups.size == 1
                Group(Modifier.padding(bottom = 10.dp)) {
                    ListRow(
                        g.brand.ifEmpty { "No brand" },
                        leading = { KIcon(Icons.Folder, if (g.brand.isEmpty()) Knit.colors.secondaryLabel else Knit.colors.accent, 22.dp) },
                        value = folderCount(g.needles.size, g.cables.size),
                        trailing = { KIcon(if (open) Icons.ChevronDown else Icons.ChevronRight, Knit.colors.tertiaryLabel, 16.dp) },
                    ) { toggle(g.key) }
                    if (open) {
                        g.needles.forEach { n ->
                            RowDivider(50.dp)
                            ListRow(Needles.label(n.sizeMm, units.imperial), subtitle = needleSummary(n, units, withBrand = false).ifBlank { null }, leading = { KIcon(Icons.Needle, Knit.colors.secondaryLabel, 22.dp) }, chevron = true) { needle = n }
                        }
                        if (g.brand.isNotEmpty()) {
                            RowDivider(50.dp)
                            ListRow("Add ${g.brand} needle", titleColor = Knit.colors.accent, leading = { KIcon(Icons.Plus, Knit.colors.accent, 22.dp) }) {
                                needle = nextInSet(g.needles, Needles.sizes.map { it.mm }, newId()) ?: Needle(id = newId(), brand = g.brand)
                            }
                        }
                        // Cables for interchangeable tips of this brand.
                        if (g.cables.isNotEmpty() || g.needles.any { it.type == NeedleType.INTERCHANGEABLE }) {
                            RowDivider(0.dp)
                            Text("CABLES", style = Knit.type.overline, color = Knit.colors.secondaryLabel, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
                            g.cables.forEach { c ->
                                RowDivider(50.dp)
                                ListRow(cableTitle(c, units), subtitle = cableSummary(c).ifBlank { null }, leading = { KIcon(Icons.Cable, Knit.colors.secondaryLabel, 22.dp) }, chevron = true) { cable = c }
                            }
                            if (g.brand.isNotEmpty()) {
                                RowDivider(50.dp)
                                ListRow("Add ${g.brand} cable", titleColor = Knit.colors.accent, leading = { KIcon(Icons.Plus, Knit.colors.accent, 22.dp) }) {
                                    cable = nextCable(g.cables, g.brand, newId())
                                }
                            }
                        }
                    }
                }
            }
        } else {
            val sizes = groupBySize(needles)
            items(sizes.size, key = { "size-" + sizes[it].first }) { i ->
                val (mm, list) = sizes[i]
                Text(Needles.label(mm, units.imperial), style = Knit.type.footnote.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = Knit.colors.secondaryLabel,
                    modifier = Modifier.padding(start = PagePadding + 4.dp, bottom = 6.dp, top = if (i == 0) 0.dp else 8.dp))
                Group {
                    list.forEachIndexed { k, n ->
                        if (k > 0) RowDivider(50.dp)
                        ListRow(n.brand.trim().ifEmpty { "No brand" }, subtitle = needleSummary(n, units, withBrand = false).ifBlank { null }, leading = { KIcon(Icons.Needle, Knit.colors.secondaryLabel, 22.dp) }, chevron = true) { needle = n }
                    }
                }
            }
        }
        if (needleView == 1 && cables.isNotEmpty()) item {
            Text("Cables", style = Knit.type.footnote.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = Knit.colors.secondaryLabel,
                modifier = Modifier.padding(start = PagePadding + 4.dp, bottom = 6.dp, top = 8.dp))
            Group(Modifier.padding(bottom = 6.dp)) {
                cables.sortedWith(compareBy({ it.lengthCm }, { studio.knit.stash.brandKey(it.brand) })).forEachIndexed { k, c ->
                    if (k > 0) RowDivider(50.dp)
                    ListRow(cableTitle(c, units), subtitle = listOf(c.brand.trim().ifEmpty { "No brand" }, cableSummary(c)).filter { it.isNotBlank() }.joinToString("  ·  "),
                        leading = { KIcon(Icons.Cable, Knit.colors.secondaryLabel, 22.dp) }, chevron = true) { cable = c }
                }
            }
        }
        item {
            Group(Modifier.padding(top = 4.dp)) {
                ListRow("Add needle", titleColor = Knit.colors.accent, leading = { KIcon(Icons.Plus, Knit.colors.accent, 22.dp) }) { needle = Needle(id = newId()) }
                RowDivider(50.dp)
                ListRow("Add cable", subtitle = "For interchangeable needle tips", titleColor = Knit.colors.accent, leading = { KIcon(Icons.Plus, Knit.colors.accent, 22.dp) }) { cable = Cable(id = newId()) }
            }
            val brandCount = groupByBrand(needles, cables).count { it.brand.isNotEmpty() }
            SectionFooter(
                (if (needles.isNotEmpty()) "${needles.size} ${if (needles.size == 1) "needle" else "needles"}" + (if (brandCount > 0) " from $brandCount ${if (brandCount == 1) "brand" else "brands"}" else "") + ". " else "") +
                    "Items in your stash can be added to any pattern from its Materials tab.",
            )
        }
    }
    if (confirmMove) {
        ConfirmDialog(
            "Move notes to Brand?",
            "For these needles the note becomes the brand and the note is cleared:\n" +
                inNotes.groupBy { it.notes.trim() }.entries.joinToString("\n") { (b, l) -> "• $b (${l.size})" },
            "Move", { scope.launch { inNotes.forEach { app.repo.saveNeedle(moveNotesToBrand(it)) }; app.message("Needles sorted by brand") } },
            { confirmMove = false },
        )
    }
    yarn?.let { y ->
        val isNew = yarns.none { it.id == y.id }
        YarnSheet(y, isNew, { scope.launch { app.repo.saveYarn(it) } }, if (isNew) null else ({ scope.launch { app.repo.deleteYarn(y) } }), { yarn = null }, allowPhoto = true)
    }
    needle?.let { n ->
        val isNew = needles.none { it.id == n.id }
        NeedleSheet(n, isNew, { saved ->
            scope.launch { app.repo.saveNeedle(saved) }
            // Show the folder the needle went into.
            studio.knit.stash.brandKey(saved.brand).takeIf { it.isNotEmpty() }?.let { open(it) }
        }, if (isNew) null else ({ scope.launch { app.repo.deleteStash(n.id) } }), { needle = null }, brands = knownBrands(needles, cables))
    }
    cable?.let { c ->
        val isNew = cables.none { it.id == c.id }
        CableSheet(c, isNew, { saved ->
            scope.launch { app.repo.saveCable(saved) }
            studio.knit.stash.brandKey(saved.brand).takeIf { it.isNotEmpty() }?.let { open(it) }
        }, if (isNew) null else ({ scope.launch { app.repo.deleteStash(c.id) } }), { cable = null }, brands = knownBrands(needles, cables))
    }
}

private fun folderCount(needles: Int, cables: Int): String = when {
    cables == 0 -> "$needles"
    needles == 0 -> "$cables ${if (cables == 1) "cable" else "cables"}"
    else -> "$needles · $cables ${if (cables == 1) "cable" else "cables"}"
}
