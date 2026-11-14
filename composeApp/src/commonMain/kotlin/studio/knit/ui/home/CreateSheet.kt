package studio.knit.ui.home

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import studio.knit.chart.blankChart
import studio.knit.data.ImportOutcome
import studio.knit.io.KnitFiles
import studio.knit.model.Construction
import studio.knit.model.Gauge
import studio.knit.model.Pattern
import studio.knit.model.Row
import studio.knit.model.Section
import studio.knit.template.Template
import studio.knit.template.Templates
import studio.knit.ui.app.LocalApp
import studio.knit.ui.app.LocalNav
import studio.knit.ui.app.unitFormat
import studio.knit.ui.components.Group
import studio.knit.ui.components.Icons
import studio.knit.ui.components.KIcon
import studio.knit.ui.components.ListRow
import studio.knit.ui.components.NumberField
import studio.knit.ui.components.PagePadding
import studio.knit.ui.components.PrimaryButton
import studio.knit.ui.components.RowDivider
import studio.knit.ui.components.SectionFooter
import studio.knit.ui.components.SectionHeader
import studio.knit.ui.components.Segmented
import studio.knit.ui.components.Sheet
import studio.knit.ui.components.Stepper
import studio.knit.ui.components.toDoubleLenient
import studio.knit.ui.platform.rememberPlatformActions
import studio.knit.ui.theme.Knit
import studio.knit.util.newId
import studio.knit.util.nowMillis

private enum class Step { MENU, CHART, TEMPLATES, TEMPLATE_FORM }

@Composable
private fun IconTile(icon: ImageVector) {
    Box(Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(Knit.colors.accentSoft), contentAlignment = Alignment.Center) {
        KIcon(icon, Knit.colors.accent, 19.dp)
    }
}

/**
 * The "New" sheet: every way of starting a pattern in one place.
 */
@Composable
fun CreateSheet(onDismiss: () -> Unit) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val platform = rememberPlatformActions()
    val units = unitFormat()
    var step by remember { mutableStateOf(Step.MENU) }
    var template by remember { mutableStateOf<Template?>(null) }

    fun create(pattern: Pattern, then: (Pattern) -> Unit) {
        scope.launch {
            val saved = app.repo.create(pattern)
            onDismiss()
            then(saved)
        }
    }

    val title = when (step) {
        Step.MENU -> "New"
        Step.CHART -> "New chart"
        Step.TEMPLATES -> "Templates"
        Step.TEMPLATE_FORM -> template?.title ?: "Template"
    }
    Sheet(title, onDismiss, doneLabel = "Cancel") {
        when (step) {
            Step.MENU -> {
                Spacer(Modifier.height(8.dp))
                Group {
                    ListRow("Blank pattern", subtitle = "Start with the details, add charts and rows later", leading = { IconTile(Icons.Document) }, chevron = true) {
                        create(Pattern(id = newId())) { nav.openPattern(it.id) }
                    }
                    RowDivider(62.dp)
                    ListRow("Chart", subtitle = "Draw stitches on a grid", leading = { IconTile(Icons.Grid) }, chevron = true) { step = Step.CHART }
                    RowDivider(62.dp)
                    ListRow("Written pattern", subtitle = "Type row-by-row instructions", leading = { IconTile(Icons.Rows) }, chevron = true) {
                        val section = Section(id = newId(), title = "Section 1", rows = listOf(Row(id = newId())))
                        create(Pattern(id = newId(), sections = listOf(section))) { nav.openPattern(it.id, 2); nav.openSection(it.id, section.id) }
                    }
                }
                SectionHeader("Or start from")
                Group {
                    ListRow("Template", subtitle = "Scarf, hat, lace, cables — sized from your gauge", leading = { IconTile(Icons.Sparkle) }, chevron = true) { step = Step.TEMPLATES }
                    RowDivider(62.dp)
                    ListRow("Import a file", subtitle = "Open a .knit pattern from another device", leading = { IconTile(Icons.Import) }, chevron = true) {
                        platform.pickFile { file ->
                            if (file == null) return@pickFile
                            scope.launch {
                                val text = runCatching { file.bytes.decodeToString() }.getOrDefault("")
                                if (KnitFiles.isBackup(text)) {
                                    app.message("That is a full backup. Restore it from Settings")
                                    return@launch
                                }
                                when (val r = app.repo.importPattern(text)) {
                                    is ImportOutcome.Failed -> app.message(r.reason)
                                    is ImportOutcome.Imported -> {
                                        app.message(if (r.warnings.isEmpty()) "Imported “${r.pattern.displayName}”" else "Imported with ${r.warnings.size} repair${if (r.warnings.size == 1) "" else "s"}")
                                        onDismiss()
                                        nav.openPattern(r.pattern.id)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Step.CHART -> {
                var cols by remember { mutableIntStateOf(16) }
                var rows by remember { mutableIntStateOf(12) }
                var round by remember { mutableIntStateOf(0) }
                Spacer(Modifier.height(8.dp))
                Group {
                    Stepper(cols, { cols = it }, 1..200, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), "Stitches wide")
                    RowDivider()
                    Stepper(rows, { rows = it }, 1..200, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), "Rows high")
                }
                SectionHeader("Worked")
                Segmented(listOf("Flat (rows)", "In the round"), round, { round = it }, Modifier.padding(horizontal = PagePadding).fillMaxWidth())
                SectionFooter(
                    if (round == 0) "Right-side rows are read right to left, wrong-side rows left to right."
                    else "Every round is read right to left.",
                )
                Spacer(Modifier.height(20.dp))
                PrimaryButton("Create chart", {
                    val chart = blankChart(newId(), cols, rows, "Chart 1", if (round == 0) Construction.FLAT else Construction.ROUND)
                    create(Pattern(id = newId(), charts = listOf(chart))) { nav.openPattern(it.id, 1); nav.openChart(it.id, chart.id) }
                }, Modifier.padding(horizontal = PagePadding).fillMaxWidth())
            }

            Step.TEMPLATES -> {
                Spacer(Modifier.height(8.dp))
                Group {
                    Templates.all.forEachIndexed { i, t ->
                        if (i > 0) RowDivider()
                        ListRow(t.title, subtitle = "${t.subtitle}  ·  ${t.difficulty.label}", chevron = true) {
                            template = t
                            step = Step.TEMPLATE_FORM
                        }
                    }
                }
            }

            Step.TEMPLATE_FORM -> {
                val t = template ?: return@Sheet
                var sts by remember(t) { mutableStateOf(t.defaultGauge.stitchesPer10cm.toInt().toString()) }
                var rws by remember(t) { mutableStateOf(t.defaultGauge.rowsPer10cm.toInt().toString()) }
                val values = remember(t) { mutableStateMapOf<String, String>().apply { t.fields.forEach { put(it.key, units.lengthValue(it.defaultCm).let { v -> if (v == v.toLong().toDouble()) v.toLong().toString() else ((v * 10).toLong() / 10.0).toString() }) } } }
                Spacer(Modifier.height(8.dp))
                if (t.needsGauge) {
                    SectionHeader("Your gauge over ${units.gaugeSpan}")
                    Row(Modifier.padding(horizontal = PagePadding), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NumberField(sts, { sts = it }, Modifier.weight(1f), "Stitches", "sts")
                        NumberField(rws, { rws = it }, Modifier.weight(1f), "Rows", "rows")
                    }
                    SectionFooter("Measure a washed swatch for the best fit. The stitch counts are calculated from these numbers.")
                }
                if (t.fields.isNotEmpty()) {
                    SectionHeader("Measurements")
                    Column(Modifier.padding(horizontal = PagePadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (f in t.fields) NumberField(values[f.key] ?: "", { values[f.key] = it }, Modifier.fillMaxWidth(), f.label, units.lengthUnit)
                    }
                }
                if (!t.needsGauge) SectionFooter(t.subtitle + ". You can change everything afterwards.")
                Spacer(Modifier.height(22.dp))
                val s = sts.toDoubleLenient()
                val r = rws.toDoubleLenient()
                val parsed = t.fields.associate { it.key to values[it.key]?.toDoubleLenient() }
                val valid = (!t.needsGauge || (s != null && r != null && s > 0 && r > 0)) && parsed.values.all { it != null && it > 0 }
                PrimaryButton("Create pattern", {
                    // Gauge and lengths are entered in the user's units and stored metric.
                    val factor = if (units.imperial) 1 / 1.016 else 1.0
                    val gauge = if (t.needsGauge) Gauge(s!! * factor, r!! * factor) else t.defaultGauge
                    val pattern = Templates.build(t, gauge, parsed.mapValues { units.toCm(it.value!!) }, app.library.value, ::newId, nowMillis())
                    create(pattern) { nav.openPattern(it.id) }
                }, Modifier.padding(horizontal = PagePadding).fillMaxWidth(), enabled = valid)
            }
        }
    }
}
