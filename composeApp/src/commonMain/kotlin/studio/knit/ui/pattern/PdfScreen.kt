package studio.knit.ui.pattern

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import studio.knit.companion.bump
import studio.knit.companion.startProgress
import studio.knit.model.AttachedFile
import studio.knit.model.Counter
import studio.knit.model.Pattern
import studio.knit.ui.app.LocalApp
import studio.knit.ui.app.LocalNav
import studio.knit.ui.app.PatternEditor
import studio.knit.ui.app.rememberHaptic
import studio.knit.ui.components.EmptyState
import studio.knit.ui.components.FixedPage
import studio.knit.ui.components.IconBtn
import studio.knit.ui.components.Icons
import studio.knit.ui.components.KIcon
import studio.knit.ui.components.MenuButton
import studio.knit.ui.components.MenuItem
import studio.knit.ui.components.pressable
import studio.knit.ui.platform.KeepScreenOn
import studio.knit.ui.companion.knitActivity
import studio.knit.stats.logRows
import studio.knit.ui.platform.PdfView
import studio.knit.ui.platform.rememberPlatformActions
import studio.knit.ui.theme.Knit
import studio.knit.util.nowMillis

/** The row counter kept for a PDF, stored with the pattern's knitting progress. */
fun pdfCounterId(file: AttachedFile) = "pdf-rows-${file.id}"

/**
 * Reading a PDF pattern while knitting: the document fills the screen, with a row counter in
 * thumb reach underneath. The count is saved with the pattern and survives closing the app.
 */
@Composable
fun PdfScreen(patternId: String, fileId: String) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val editor = remember(patternId) { app.editor(patternId) }
    val pattern by editor.state.collectAsState()
    val loaded by editor.loaded.collectAsState()
    val settings by app.settings.collectAsState()
    val platform = rememberPlatformActions()
    val scope = rememberCoroutineScope()
    val haptic = rememberHaptic()
    KeepScreenOn(settings.keepScreenOn)

    val file = pattern?.files?.firstOrNull { it.id == fileId }
    val bytes by produceState<ByteArray?>(null, fileId) { value = app.repo.fileBytes(fileId) }
    if (file == null) {
        FixedPage("PDF", onBack = nav::back) { if (loaded) EmptyState(Icons.Document, "PDF not found", "It may have been removed from the pattern.") }
        return
    }
    val counter = pattern?.progress?.counters?.firstOrNull { it.id == pdfCounterId(file) }
    val timer = studio.knit.ui.companion.rememberKnitTimer(editor)
    fun bumpRow(delta: Int) {
        timer.touch()
        editor.update { p ->
            val progress = p.progress ?: startProgress(p, nowMillis())
            val existing = progress.counters.firstOrNull { it.id == pdfCounterId(file) }
                ?: Counter(id = pdfCounterId(file), name = "Rows · ${file.name.removeSuffix(".pdf")}")
            val next = existing.bump(delta)
            val day = studio.knit.stats.dayKey(nowMillis())
            p.copy(progress = progress.logRows(day, next.value - existing.value).copy(
                lastKnitAt = nowMillis(),
                counters = if (progress.counters.any { it.id == next.id }) progress.counters.map { if (it.id == next.id) next else it } else progress.counters + next,
            ))
        }
        haptic(false)
    }

    Box(Modifier.knitActivity(timer)) {
    FixedPage(
        nameFromPdf(file), onBack = nav::back,
        actions = {
            MenuButton(listOf(
                MenuItem("Share PDF", Icons.Share) { scope.launch { app.repo.fileBytes(file.id)?.let { platform.shareFile(file.name, file.mime, it) } } },
                MenuItem("Reset row counter", Icons.Undo) { bumpRow(-(counter?.value ?: 0)) },
            ))
        },
    ) {
        val data = bytes
        Box(Modifier.weight(1f).fillMaxWidth().background(Knit.colors.background)) {
            when {
                data == null && !loaded -> Unit
                data == null -> EmptyState(Icons.Warning, "The PDF file is missing", "Remove it from the pattern and add it again. Settings › Check data lists missing files.")
                else -> PdfView(data, Modifier.fillMaxSize())
            }
        }
        // Row counter.
        Column(Modifier.fillMaxWidth().background(Knit.colors.surface).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
            pattern?.progress?.let { studio.knit.ui.companion.TodayLine(it, timer) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(Knit.colors.fill).pressable(enabled = (counter?.value ?: 0) > 0) { bumpRow(-1) }
                        .semantics { contentDescription = "One row back" },
                    contentAlignment = Alignment.Center,
                ) { KIcon(Icons.Minus, Knit.colors.label, 24.dp) }
                Column(Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("ROW", style = Knit.type.overline, color = Knit.colors.secondaryLabel)
                    Text("${counter?.value ?: 0}", style = Knit.type.title1, color = Knit.colors.label)
                }
                Box(
                    Modifier.width(120.dp).height(56.dp).clip(RoundedCornerShape(16.dp)).background(Knit.colors.accent).pressable { bumpRow(1) }
                        .semantics { contentDescription = "Row done" },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        KIcon(Icons.Plus, Knit.colors.onAccent, 22.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("Row", style = Knit.type.headline, color = Knit.colors.onAccent)
                    }
                }
            }
        }
    }
    }
}

/** Opens a picker for a PDF and returns the stored attachment, or reports why it was refused. */
suspend fun storePickedPdf(app: studio.knit.ui.app.AppContainer, picked: studio.knit.ui.platform.PickedFile): AttachedFile? {
    val b = picked.bytes
    val isPdf = b.size >= 4 && b[0] == '%'.code.toByte() && b[1] == 'P'.code.toByte() && b[2] == 'D'.code.toByte() && b[3] == 'F'.code.toByte()
    if (!isPdf) {
        app.message("“${picked.name}” is not a PDF" + (studio.knit.io.KnitFiles.identifyOtherFile(b)?.let { ": $it" } ?: ""))
        return null
    }
    if (b.size > 150_000_000) { app.message("“${picked.name}” is larger than 150 MB and cannot be added"); return null }
    val name = if (picked.name.endsWith(".pdf", ignoreCase = true)) picked.name else picked.name + ".pdf"
    return app.repo.storeFile(name, b)
}

/** Pattern name from a PDF's file name: "Fjord_sjaal-v2.pdf" → "Fjord sjaal v2". */
fun nameFromPdf(file: AttachedFile): String =
    file.name.removeSuffix(".pdf").removeSuffix(".PDF").replace('_', ' ').replace('-', ' ').replace(Regex("\\s+"), " ").trim()

fun Pattern.hasPdf(): Boolean = files.any { it.mime == "application/pdf" }

/** Removes a PDF from a pattern and deletes the file. */
fun removePdf(app: studio.knit.ui.app.AppContainer, editor: PatternEditor, file: AttachedFile, scope: kotlinx.coroutines.CoroutineScope) {
    editor.update { p -> p.copy(files = p.files.filterNot { it.id == file.id }, progress = p.progress?.let { pr -> pr.copy(counters = pr.counters.filterNot { it.id == pdfCounterId(file) }) }) }
    scope.launch { editor.flushAndWait(); app.repo.deleteFile(file.id) }
}
