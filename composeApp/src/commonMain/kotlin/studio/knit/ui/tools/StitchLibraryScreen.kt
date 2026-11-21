package studio.knit.ui.tools

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import studio.knit.model.StitchCategory
import studio.knit.model.StitchDef
import studio.knit.model.WorksOn
import studio.knit.stitch.Glyphs
import studio.knit.stitch.StitchLibrary
import studio.knit.ui.app.LocalApp
import studio.knit.ui.app.LocalNav
import studio.knit.ui.chart.FixedGrid
import studio.knit.ui.chart.StitchInfoSheet
import studio.knit.ui.chart.StitchSymbol
import studio.knit.ui.chart.StitchTile
import studio.knit.ui.chart.deltaLabel
import studio.knit.ui.components.BoundField
import studio.knit.ui.components.Group
import studio.knit.ui.components.IconBtn
import studio.knit.ui.components.Icons
import studio.knit.ui.components.ListRow
import studio.knit.ui.components.Page
import studio.knit.ui.components.PagePadding
import studio.knit.ui.components.PickerRow
import studio.knit.ui.components.PrimaryButton
import studio.knit.ui.components.RowDivider
import studio.knit.ui.components.SectionFooter
import studio.knit.ui.components.SectionHeader
import studio.knit.ui.components.Segmented
import studio.knit.ui.components.Sheet
import studio.knit.ui.components.Stepper
import studio.knit.ui.library.SearchField
import studio.knit.ui.theme.Knit
import studio.knit.util.newId

@Composable
fun StitchLibraryScreen() {
    val app = LocalApp.current
    val nav = LocalNav.current
    val library by app.library.collectAsState()
    val settings by app.settings.collectAsState()
    var query by remember { mutableStateOf("") }
    var info by remember { mutableStateOf<StitchDef?>(null) }
    var editing by remember { mutableStateOf<StitchDef?>(null) }

    Page("Stitch library", onBack = nav::back, backLabel = "Tools", largeTitle = false,
        actions = { IconBtn(Icons.Plus, "New stitch", { editing = StitchDef(id = newId(), name = "", abbr = "", symbol = "diamond", category = StitchCategory.CUSTOM) }, size = 26.dp) }) {
        item { SearchField(query, { query = it }, "Search stitches", Modifier.padding(horizontal = PagePadding).padding(top = 8.dp).fillMaxWidth()) }
        val q = query.trim().lowercase()
        val all = library.all.filter { q.isEmpty() || it.name.lowercase().contains(q) || it.abbr.lowercase().contains(q) || it.aliases.any { a -> a.contains(q) } }
        for (category in listOf(StitchCategory.CUSTOM) + StitchCategory.entries.filter { it != StitchCategory.CUSTOM }) {
            val defs = all.filter { it.category == category }
            if (defs.isEmpty() && category != StitchCategory.CUSTOM) continue
            item(key = "h-${category.name}") { SectionHeader(category.label) }
            if (defs.isEmpty()) {
                item(key = "empty-custom") { SectionFooter("Stitches you define appear here and in the chart editor. Tap + to create one — a nupp, a special cable, anything your pattern needs.") }
                continue
            }
            item(key = "g-${category.name}") {
                Group {
                    defs.forEachIndexed { i, def ->
                        if (i > 0) RowDivider(66.dp)
                        ListRow(
                            def.name, subtitle = listOf(def.abbrFor(settings.ukTerms), deltaLabel(def)).joinToString("  ·  "),
                            leading = { Box(Modifier.padding(end = 2.dp), contentAlignment = Alignment.Center) { if (def.width > 0) StitchSymbol(def, cell = 34.dp) else Spacer(Modifier.height(34.dp)) } },
                            chevron = true,
                        ) { if (def.builtIn) info = def else editing = def }
                    }
                }
            }
        }
        item { SectionFooter("${library.all.size} stitches. Each one knows how many stitches it uses and makes — that is what lets Knit Studio check your counts.") }
    }
    var renaming by remember { mutableStateOf<StitchDef?>(null) }
    info?.let { def -> StitchInfoSheet(def, library, onRename = { info = null; renaming = def }) { info = null } }
    renaming?.let { def -> RenameStitchSheet(def, library) { renaming = null } }
    editing?.let { def -> CustomStitchSheet(def, library, isNew = library[def.id] == null) { editing = null } }
}

/**
 * Gives a built-in stitch the knitter's own name and abbreviation, e.g. "skp" instead of "skpo".
 * The original spellings keep being understood in rows and imported patterns.
 */
@Composable
private fun RenameStitchSheet(def: StitchDef, library: StitchLibrary, onDismiss: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val original = studio.knit.stitch.BuiltInStitches.all.first { it.id == def.id }
    var name by remember { mutableStateOf(def.name) }
    var abbr by remember { mutableStateOf(def.abbr) }
    val clash = library.spellings.firstOrNull { (spelling, d) -> d.id != def.id && spelling == abbr.trim().lowercase() }?.second
    Sheet("Rename stitch", onDismiss, doneLabel = "Save", onDone = {
        if (clash != null || abbr.isBlank()) return@Sheet
        scope.launch {
            if (name.trim() == original.name && abbr.trim() == original.abbr) app.repo.deleteStitch(def.id)
            else app.repo.saveStitch(original.copy(name = name.trim(), abbr = abbr.trim()))
            app.message("“${original.abbr}” is now shown as “${abbr.trim()}”")
        }
        onDismiss()
    }) {
        Column(Modifier.padding(horizontal = PagePadding).padding(top = 8.dp)) {
            studio.knit.ui.components.Field(name, { name = it }, Modifier.fillMaxWidth(), "Name")
            Spacer(Modifier.height(12.dp))
            studio.knit.ui.components.Field(abbr, { abbr = it }, Modifier.fillMaxWidth(), "Abbreviation", isError = clash != null,
                capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.None)
        }
        SectionFooter(
            if (clash != null) "“${abbr.trim()}” already means ${clash.name}. Choose another abbreviation."
            else "Original: ${original.name} (${original.abbr}). Rows written with the original abbreviation keep working; " +
                "written rows, knitting mode and PDFs show your abbreviation."
        )
        if (library.isRenamed(def.id)) {
            Spacer(Modifier.height(8.dp))
            Group {
                ListRow("Use the original name", titleColor = Knit.colors.accent) {
                    scope.launch { app.repo.deleteStitch(def.id); app.message("Back to “${original.abbr}”") }
                    onDismiss()
                }
            }
        }
    }
}

/** Create or edit a user-defined stitch, including its chart symbol and stitch arithmetic. */
@Composable
private fun CustomStitchSheet(initial: StitchDef, library: StitchLibrary, isNew: Boolean, onDismiss: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var d by remember { mutableStateOf(initial) }
    var textSymbol by remember { mutableStateOf(if (Glyphs.isText(initial.symbol)) Glyphs.textOf(initial.symbol) else "") }
    val abbrTaken = d.abbr.isNotBlank() && library.all.any { it.id != d.id && (it.abbr.equals(d.abbr.trim(), true) || it.aliases.any { a -> a.equals(d.abbr.trim(), true) }) }
    val valid = d.name.isNotBlank() && d.abbr.isNotBlank() && !abbrTaken

    Sheet(if (isNew) "New stitch" else "Edit stitch", onDismiss, doneLabel = "Save", onDone = {
        if (valid) {
            scope.launch { app.repo.saveStitch(d.copy(name = d.name.trim(), abbr = d.abbr.trim(), category = StitchCategory.CUSTOM, builtIn = false)) }
            onDismiss()
        } else app.message(if (abbrTaken) "That abbreviation is already used by another stitch" else "Give the stitch a name and an abbreviation")
    }) {
        Row(Modifier.padding(horizontal = PagePadding).padding(top = 8.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StitchSymbol(d, cell = 50.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BoundField(d.name, { d = d.copy(name = it) }, Modifier.fillMaxWidth(), "Name", "e.g. Nupp")
                BoundField(d.abbr, { d = d.copy(abbr = it) }, Modifier.fillMaxWidth(), "Abbreviation", "e.g. nupp", capitalization = KeyboardCapitalization.None, isError = abbrTaken)
            }
        }
        if (abbrTaken) SectionFooter("Another stitch already uses this abbreviation, so written rows could not tell them apart.")

        SectionHeader("Stitch count")
        Group {
            // Knitters think in "decreases 1" (−1) and "worked over 2 stitches"; the stitches it
            // leaves on the needle follow from those two.
            val change = d.produces - d.consumes
            Stepper(change, { c -> d = d.copy(produces = (d.consumes + c).coerceAtLeast(0)) }, -d.consumes..8,
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp), "Change in stitch count", signed = true)
            RowDivider()
            Stepper(d.consumes, { n -> d = d.copy(consumes = n, produces = (n + change).coerceAtLeast(0)) }, 0..12,
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp), "Worked over (stitches)")
            RowDivider()
            Stepper(d.width, { d = d.copy(width = it) }, 1..8, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), "Chart cells wide")
        }
        SectionFooter(
            "Change: −1 for a decrease, +1 for an increase, 0 when the count stays the same. Worked over: how many stitches it uses " +
                "from the left needle — k2tog is worked over 2 (change −1), a yarn over over 0 (change +1), a 4-stitch cable over 4 (change 0). " +
                "This stitch uses ${d.consumes} and leaves ${d.produces} on the right needle."
        )

        SectionHeader("Symbol")
        FixedGrid(Glyphs.pickable, 5, Modifier.padding(horizontal = PagePadding)) { key, m ->
            val preview = StitchDef(id = key, name = key.replace('_', ' '), abbr = "", symbol = key)
            Box(m) {
                StitchTile(preview.copy(abbr = " ", name = " "), d.symbol == key, Modifier.fillMaxWidth()) { d = d.copy(symbol = key); textSymbol = "" }
            }
        }
        Spacer(Modifier.height(12.dp))
        BoundField(textSymbol, { t ->
            textSymbol = t.take(3)
            if (t.isNotBlank()) d = d.copy(symbol = "text:${t.take(3)}")
        }, Modifier.padding(horizontal = PagePadding).fillMaxWidth(), "Or use letters as the symbol", "up to 3 characters", capitalization = KeyboardCapitalization.Characters)

        SectionHeader("On wrong-side rows")
        Segmented(listOf("Worked the same", "Not defined"), if (d.ws == d.id) 0 else 1, { i -> d = d.copy(ws = if (i == 0) d.id else null) }, Modifier.padding(horizontal = PagePadding).fillMaxWidth())
        SectionFooter("Charts show the right side of the fabric. If this stitch is worked identically on a wrong-side row, written instructions can use it there too.")

        SectionHeader("Where it may be used")
        Group { PickerRow("Rows", WorksOn.entries, d.worksOn, { it.label }) { d = d.copy(worksOn = it) } }

        SectionHeader("Description")
        Column(Modifier.padding(horizontal = PagePadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BoundField(d.description, { d = d.copy(description = it) }, Modifier.fillMaxWidth(), placeholder = "What the stitch looks like", singleLine = false, minLines = 2)
            BoundField(d.instructions, { d = d.copy(instructions = it) }, Modifier.fillMaxWidth(), "How to work it", "Step by step", singleLine = false, minLines = 2)
        }
        if (!isNew) {
            Spacer(Modifier.height(22.dp))
            PrimaryButton("Delete stitch", { scope.launch { app.repo.deleteStitch(d.id) }; onDismiss() }, Modifier.padding(horizontal = PagePadding).fillMaxWidth(), destructive = true)
            SectionFooter("Patterns that already use this stitch keep their own copy of it.")
        }
    }
}
