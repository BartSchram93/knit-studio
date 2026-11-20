package studio.knit.ui.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import studio.knit.data.PaperSize
import studio.knit.io.KnitFiles
import studio.knit.model.Pattern
import studio.knit.pdf.PatternPdf
import studio.knit.pdf.PdfOptions
import studio.knit.ui.app.LocalApp
import studio.knit.ui.app.PatternEditor
import studio.knit.ui.components.Group
import studio.knit.ui.components.Icons
import studio.knit.ui.components.PagePadding
import studio.knit.ui.components.PrimaryButton
import studio.knit.ui.components.RowDivider
import studio.knit.ui.components.SectionFooter
import studio.knit.ui.components.SectionHeader
import studio.knit.ui.components.Segmented
import studio.knit.ui.components.Sheet
import studio.knit.ui.components.SoftButton
import studio.knit.ui.components.SwitchRow
import studio.knit.ui.platform.rememberPlatformActions

/** Builds the PDF bytes for [pattern] with the user's units and terminology. */
suspend fun buildPdf(app: studio.knit.ui.app.AppContainer, pattern: Pattern, options: PdfOptions): ByteArray = withContext(Dispatchers.Default) {
    val images = pattern.imageIds.mapNotNull { id -> app.repo.image(id)?.let { id to it } }.toMap()
    PatternPdf(pattern, app.library.value.with(app.library.value.custom + pattern.customStitches), options, images).render()
}

/**
 * Export: a print-ready PDF with selectable contents, or the `.knit` project file for moving the
 * pattern to another device.
 */
@Composable
fun ExportSheet(pattern: Pattern, editor: PatternEditor, onDismiss: () -> Unit) {
    val app = LocalApp.current
    val settings by app.settings.collectAsState()
    val scope = rememberCoroutineScope()
    val platform = rememberPlatformActions()
    var mode by remember { mutableIntStateOf(0) }
    var options by remember {
        mutableStateOf(PdfOptions(paper = settings.paper, landscape = settings.pdfLandscape, imperial = settings.imperial, uk = settings.ukTerms, shadeWsRows = settings.shadeWsRows))
    }
    var includeProgress by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }

    fun pdf(share: Boolean) {
        busy = true
        scope.launch {
            try {
                val bytes = buildPdf(app, pattern, options)
                val name = KnitFiles.fileName(pattern.displayName, "pdf")
                if (share) platform.shareFile(name, "application/pdf", bytes) else platform.saveFile(name, "application/pdf", bytes)
                // Remember the paper choice for next time.
                app.repo.updateSettings { it.copy(paper = options.paper, pdfLandscape = options.landscape) }
            } catch (e: Exception) {
                app.message("Could not create the PDF: ${e.message ?: "unknown error"}")
            } finally {
                busy = false
            }
        }
    }

    fun file(share: Boolean) {
        busy = true
        scope.launch {
            try {
                editor.flushAndWait()
                val text = app.repo.exportPattern(pattern.id, includeProgress)
                if (text == null) app.message("Could not read the pattern")
                else {
                    val name = KnitFiles.fileName(pattern.displayName, KnitFiles.PATTERN_EXTENSION)
                    if (share) platform.shareFile(name, KnitFiles.MIME, text.encodeToByteArray()) else platform.saveFile(name, KnitFiles.MIME, text.encodeToByteArray())
                }
            } catch (e: Exception) {
                app.message("Export failed: ${e.message ?: "unknown error"}")
            } finally {
                busy = false
            }
        }
    }

    Sheet("Export", onDismiss) {
        Segmented(listOf("PDF to print", "Knit Studio file"), mode, { mode = it }, Modifier.padding(horizontal = PagePadding).padding(top = 8.dp).fillMaxWidth())
        if (mode == 0) {
            SectionHeader("Include")
            Group {
                SwitchRow("Cover page", options.cover, { options = options.copy(cover = it) })
                RowDivider()
                SwitchRow("Materials", options.materials, { options = options.copy(materials = it) })
                RowDivider()
                SwitchRow("Measurements", options.measurements, { options = options.copy(measurements = it) })
                RowDivider()
                SwitchRow("Abbreviations", options.abbreviations, { options = options.copy(abbreviations = it) })
                RowDivider()
                SwitchRow("Written instructions", options.written, { options = options.copy(written = it) })
                RowDivider()
                SwitchRow("Charts", options.charts, { options = options.copy(charts = it) })
                RowDivider()
                SwitchRow("Stitch key", options.legend, { options = options.copy(legend = it) })
                RowDivider()
                SwitchRow("Tint wrong-side rows", options.shadeWsRows, { options = options.copy(shadeWsRows = it) })
                RowDivider()
                SwitchRow("Notes", options.notes, { options = options.copy(notes = it) })
            }
            SectionHeader("Paper")
            Segmented(PaperSize.entries.map { it.label }, options.paper.ordinal, { options = options.copy(paper = PaperSize.entries[it]) }, Modifier.padding(horizontal = PagePadding).fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Segmented(listOf("Portrait", "Landscape"), if (options.landscape) 1 else 0, { options = options.copy(landscape = it == 1) }, Modifier.padding(horizontal = PagePadding).fillMaxWidth())
            SectionFooter("For printing and reading. Charts are vector graphics, so they stay sharp at any size. A PDF cannot be imported back into Knit Studio — for that, use “Knit Studio file”.")
            Spacer(Modifier.height(18.dp))
            Row(Modifier.padding(horizontal = PagePadding), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("Share PDF", { pdf(true) }, Modifier.weight(1f), icon = Icons.Share, enabled = !busy)
                SoftButton("Save to Files", { pdf(false) }, Modifier.weight(1f), icon = Icons.Import, enabled = !busy)
            }
        } else {
            SectionHeader("Knit Studio file (.knit)")
            Group {
                SwitchRow("Include knitting progress", includeProgress, { includeProgress = it }, "Row position, counters and notes")
            }
            SectionFooter("To keep a copy you can import again, or to move the pattern to another phone. It holds everything — charts, rows, sizes, materials, photos and your own stitches. Open it with New › Import a Knit Studio file.")
            Spacer(Modifier.height(18.dp))
            Row(Modifier.padding(horizontal = PagePadding), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("Share file", { file(true) }, Modifier.weight(1f), icon = Icons.Share, enabled = !busy)
                SoftButton("Save to Files", { file(false) }, Modifier.weight(1f), icon = Icons.Import, enabled = !busy)
            }
        }
    }
}
