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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import studio.knit.model.Side
import studio.knit.pdfimport.ImportItem
import studio.knit.pdfimport.ImportedSection
import studio.knit.pdfimport.PdfImport
import studio.knit.pdfimport.RowExtractor
import studio.knit.ui.app.LocalApp
import studio.knit.ui.app.LocalNav
import studio.knit.ui.components.Card
import studio.knit.ui.components.EmptyState
import studio.knit.ui.components.Group
import studio.knit.ui.components.Icons
import studio.knit.ui.components.KIcon
import studio.knit.ui.components.ListRow
import studio.knit.ui.components.Page
import studio.knit.ui.components.PagePadding
import studio.knit.ui.components.PrimaryButton
import studio.knit.ui.components.RowDivider
import studio.knit.ui.components.SectionFooter
import studio.knit.ui.components.SectionHeader
import studio.knit.ui.components.SwitchRow
import studio.knit.ui.platform.PlatformActions
import studio.knit.ui.platform.rememberPlatformActions
import studio.knit.ui.theme.Knit
import studio.knit.util.newId

/** What reading a PDF gave. */
sealed interface RowsFromPdf {
    data object Reading : RowsFromPdf
    data class Failed(val title: String, val message: String) : RowsFromPdf
    data class Found(val import: PdfImport) : RowsFromPdf
}

/** Reads the text of a PDF and finds its rows. */
suspend fun readRowsFromPdf(bytes: ByteArray?, platform: PlatformActions): RowsFromPdf {
    if (bytes == null) return RowsFromPdf.Failed("The PDF file is missing", "Remove it from the pattern and add it again.")
    val text = platform.pdfText(bytes)
        ?: return RowsFromPdf.Failed("This PDF could not be read", "It may be damaged or protected with a password. You can still read it and count rows in the PDF view.")
    if (text.isBlank()) return RowsFromPdf.Failed(
        "This PDF has no text",
        "It is probably a scan or a photo of the pattern. Rows can only be read from PDFs whose text you can select. Count rows in the PDF view instead.",
    )
    val found = withContext(Dispatchers.Default) { RowExtractor.extract(text) }
    if (found.isEmpty) return RowsFromPdf.Failed(
        "No rows found",
        "The app looks for lines such as “Row 1 (RS): …”, “Rnd 3: …” or “Next row: …”. This pattern is written differently, or uses only charts.",
    )
    return RowsFromPdf.Found(found)
}

/**
 * Review of the rows found in a PDF before they are added to the pattern as written sections.
 * Nothing is changed until "Add to pattern".
 */
@Composable
fun ImportRowsScreen(patternId: String, fileId: String) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val editor = remember(patternId) { app.editor(patternId) }
    val pattern by editor.state.collectAsState()
    val platform = rememberPlatformActions()
    val file = pattern?.files?.firstOrNull { it.id == fileId }
    val result by produceState<RowsFromPdf>(RowsFromPdf.Reading, fileId) {
        value = readRowsFromPdf(app.repo.fileBytes(fileId), platform)
    }
    var skipped by remember { mutableStateOf(setOf<Int>()) }

    val found = (result as? RowsFromPdf.Found)?.import
    val chosen = found?.let { f -> f.copy(sections = f.sections.filterIndexed { i, _ -> i !in skipped }) }

    fun add() {
        val f = chosen ?: return
        val name = file?.name ?: "PDF"
        val sections = RowExtractor.toSections(f, name, ::newId)
        editor.update { cur ->
            cur.copy(
                sections = cur.sections + sections,
                // Counts written for several sizes need size names; the knitter can rename them.
                sizes = if (cur.sizes.size < f.sizeCount) cur.sizes + (cur.sizes.size + 1..f.sizeCount).map { "Size $it" } else cur.sizes,
            )
        }
        app.message("Added ${f.rowCount} rows from “$name”. Open Knit to follow them row by row.")
        nav.back()
    }

    Page(
        "Rows from PDF", onBack = nav::back,
        overlay = {
            if (chosen != null) {
                Box(Modifier.fillMaxWidth().align(Alignment.BottomCenter).background(Knit.colors.background).padding(horizontal = PagePadding, vertical = 10.dp)) {
                    PrimaryButton(
                        if (chosen.rowCount == 0) "Choose at least one part" else "Add ${chosen.rowCount} rows to pattern",
                        ::add, Modifier.fillMaxWidth(), icon = Icons.Plus, enabled = chosen.rowCount > 0,
                    )
                }
            }
        },
    ) {
        when (val r = result) {
            RowsFromPdf.Reading -> item { EmptyState(Icons.Document, "Reading the PDF…", "Looking for rows and rounds.") }
            is RowsFromPdf.Failed -> item { EmptyState(Icons.Warning, r.title, r.message) }
            is RowsFromPdf.Found -> {
                val imp = r.import
                item {
                    Card(Modifier.padding(horizontal = PagePadding)) {
                        Text(
                            "Found ${imp.rowCount} rows in ${imp.sections.size} ${if (imp.sections.size == 1) "part" else "parts"}",
                            style = Knit.type.headline, color = Knit.colors.label,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Rows are kept in the designer's words. Stitch counts are checked for the rows the app understands; " +
                                "the others are shown as written and marked “not checked”. Check the rows against the PDF before you knit.",
                            style = Knit.type.subhead, color = Knit.colors.secondaryLabel,
                        )
                    }
                }
                if (imp.warnings.isNotEmpty()) {
                    item { SectionHeader("Please check") }
                    item {
                        Group {
                            imp.warnings.forEachIndexed { i, w ->
                                if (i > 0) RowDivider(50.dp)
                                ListRow(w, leading = { KIcon(Icons.Warning, Knit.colors.warning, 20.dp) })
                            }
                        }
                    }
                }
                imp.sections.forEachIndexed { index, s ->
                    item(key = "h$index") { SectionHeader(s.title) }
                    item(key = "s$index") {
                        Group {
                            SwitchRow(
                                "Add this part", index !in skipped,
                                { on -> skipped = if (on) skipped - index else skipped + index },
                                subtitle = sectionSummary(s),
                            )
                        }
                    }
                    if (index !in skipped) item(key = "r$index") { SectionPreview(s) }
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }
}

private fun sectionSummary(s: ImportedSection): String = listOfNotNull(
    "${s.rowCount} ${if (s.round) "round" else "row"}${if (s.rowCount == 1) "" else "s"}",
    s.repeatCount.takeIf { it > 0 }?.let { "$it ${if (it == 1) "repeat" else "repeats"}" },
    s.castOn?.let { "cast on $it" },
    if (!s.round && s.firstSide == Side.WS) "first row is WS" else null,
).joinToString(" · ")

/** The rows of one part, as they will be added. Long parts are shortened to keep the page light. */
@Composable
private fun SectionPreview(s: ImportedSection) {
    val shown = if (s.items.size > MAX_PREVIEW) s.items.take(MAX_PREVIEW) else s.items
    Group(Modifier.padding(top = 8.dp)) {
        var position = 0
        shown.forEachIndexed { i, item ->
            if (i > 0) RowDivider(16.dp)
            when (item) {
                is ImportItem.Instruction -> {
                    position++
                    val label = (if (s.round) "Rnd " else "Row ") + (item.number ?: position) + (item.side?.let { " (${it.name})" } ?: "")
                    PreviewRow(label, item.text, item.note)
                }
                is ImportItem.Repeat -> {
                    val unit = if (s.round) "Rnd" else "Row"
                    val which = if (item.from == item.to) "$unit ${item.from}" else "${unit}s ${item.from}–${item.to}"
                    val times = if (item.more.values.all { it == 1 }) "time" else "times"
                    PreviewRow("Repeat", "$which, ${item.more} more $times", "", accent = true)
                }
                is ImportItem.Note -> PreviewRow("Note", item.text, "", muted = true)
            }
        }
    }
    if (s.items.size > MAX_PREVIEW) SectionFooter("…and ${s.items.size - MAX_PREVIEW} more lines. All of them are added.")
}

@Composable
private fun PreviewRow(label: String, text: String, note: String, accent: Boolean = false, muted: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp), horizontalArrangement = Arrangement.Start) {
        Text(
            label, style = Knit.type.footnote,
            color = if (accent) Knit.colors.accent else Knit.colors.secondaryLabel,
            modifier = Modifier.widthIn(min = 64.dp).width(76.dp).padding(top = 2.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(text, style = Knit.type.body, color = if (muted) Knit.colors.secondaryLabel else Knit.colors.label)
            if (note.isNotBlank()) Text(note, style = Knit.type.footnote, color = Knit.colors.secondaryLabel)
        }
    }
}

private const val MAX_PREVIEW = 60
