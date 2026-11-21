package studio.knit.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import studio.knit.data.PaperSize
import studio.knit.data.Settings
import studio.knit.data.ThemeMode
import studio.knit.io.KnitFiles
import studio.knit.ui.app.LocalApp
import studio.knit.ui.app.formatDate
import studio.knit.ui.components.ConfirmDialog
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
import studio.knit.ui.components.Segmented
import studio.knit.ui.components.Sheet
import studio.knit.ui.components.SoftButton
import studio.knit.ui.components.SwitchRow
import studio.knit.ui.platform.appVersion
import studio.knit.ui.platform.platformName
import studio.knit.ui.platform.rememberPlatformActions
import studio.knit.ui.theme.Knit
import studio.knit.util.nowMillis

@Composable
fun SettingsScreen() {
    val app = LocalApp.current
    val settings by app.settings.collectAsState()
    val patterns by app.patterns.collectAsState()
    val scope = rememberCoroutineScope()
    val platform = rememberPlatformActions()
    var pendingRestore by remember { mutableStateOf<String?>(null) }
    var checkResult by remember { mutableStateOf<List<String>?>(null) }
    var wiping by remember { mutableStateOf(false) }
    fun set(transform: (Settings) -> Settings) { scope.launch { app.repo.updateSettings(transform) } }

    Page("Settings") {
        item { SectionHeader("Units") }
        item {
            Segmented(listOf("Metric (cm, m, g)", "Imperial (in, yd, oz)"), if (settings.imperial) 1 else 0, { i -> set { it.copy(imperial = i == 1) } }, Modifier.padding(horizontal = PagePadding).fillMaxWidth())
            SectionFooter("Measurements are stored exactly and shown in the system you choose. Gauge is given over ${if (settings.imperial) "4 inches" else "10 cm"}.")
        }
        item { SectionHeader("Knitting terms") }
        item {
            Segmented(listOf("US", "UK"), if (settings.ukTerms) 1 else 0, { i -> set { it.copy(ukTerms = i == 1) } }, Modifier.padding(horizontal = PagePadding).fillMaxWidth())
            SectionFooter(
                if (settings.ukTerms) "Tension, cast off, stocking stitch, aran and chunky. Rows you type are understood in either vocabulary."
                else "Gauge, bind off, stockinette, worsted and bulky. Rows you type are understood in either vocabulary.",
            )
        }
        item { SectionHeader("Appearance") }
        item {
            Segmented(ThemeMode.entries.map { it.label }, settings.theme.ordinal, { i -> set { it.copy(theme = ThemeMode.entries[i]) } }, Modifier.padding(horizontal = PagePadding).fillMaxWidth())
            Spacer(Modifier.height(14.dp))
            Group {
                SwitchRow("Haptic feedback", settings.haptics, { v -> set { it.copy(haptics = v) } }, "A light tick when drawing stitches and counting rows")
                RowDivider()
                SwitchRow("Keep screen on while knitting", settings.keepScreenOn, { v -> set { it.copy(keepScreenOn = v) } })
                RowDivider()
                SwitchRow("Current row on the lock screen", settings.lockScreenRow, { v -> set { it.copy(lockScreenRow = v) } },
                    "While Knit mode is open, the row to knit and Back / Next buttons are shown on the lock screen")
                RowDivider()
                SwitchRow("Tint wrong-side chart rows", settings.shadeWsRows, { v -> set { it.copy(shadeWsRows = v) } },
                    "Rows knitted from the wrong side get a faint colour, so the reading direction is easy to see")
            }
        }
        item { SectionHeader("PDF export") }
        item {
            Segmented(PaperSize.entries.map { it.label }, settings.paper.ordinal, { i -> set { it.copy(paper = PaperSize.entries[i]) } }, Modifier.padding(horizontal = PagePadding).fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Segmented(listOf("Portrait", "Landscape"), if (settings.pdfLandscape) 1 else 0, { i -> set { it.copy(pdfLandscape = i == 1) } }, Modifier.padding(horizontal = PagePadding).fillMaxWidth())
        }

        item { SectionHeader("Backup") }
        item {
            Group {
                ListRow("Back up everything", subtitle = "Patterns, photos, stitches, stash and settings in one file", leading = { KIcon(Icons.Share, Knit.colors.accent, 22.dp) }) {
                    scope.launch {
                        try {
                            app.flushAll()
                            val text = app.repo.exportBackup()
                            platform.shareFile("Knit Studio backup.${KnitFiles.BACKUP_EXTENSION}", KnitFiles.MIME, text.encodeToByteArray())
                        } catch (e: Exception) {
                            app.message("Backup failed: ${e.message ?: "unknown error"}")
                        }
                    }
                }
                RowDivider(54.dp)
                ListRow("Restore from a backup", subtitle = "Open a .knitbackup file", leading = { KIcon(Icons.Import, Knit.colors.accent, 22.dp) }) {
                    platform.pickFile { file ->
                        if (file == null) return@pickFile
                        KnitFiles.identifyOtherFile(file.bytes)?.let { app.message("“${file.name}”: $it"); return@pickFile }
                        val text = runCatching { file.bytes.decodeToString() }.getOrDefault("")
                        when {
                            KnitFiles.isBackup(text) -> pendingRestore = text
                            text.take(400).contains(KnitFiles.PATTERN_FORMAT) -> app.message("“${file.name}” is a single pattern, not a backup. Open it with New › Import a Knit Studio file")
                            else -> app.message("“${file.name}” is not a Knit Studio backup")
                        }
                    }
                }
            }
            val now = remember(settings.lastBackupAt) { nowMillis() }
            SectionFooter(
                (if (settings.lastBackupAt > 0) "Last backup: ${formatDate(settings.lastBackupAt, now)}. " else "No backup made yet. ") +
                    "Everything is stored only on this device, so keep a backup somewhere safe — in your cloud drive or mailed to yourself.",
            )
        }

        item { SectionHeader("Data") }
        item {
            Group {
                ListRow("Check data", subtitle = "Verify the database and every pattern", leading = { KIcon(Icons.CheckCircle, Knit.colors.accent, 22.dp) }) {
                    scope.launch { app.flushAll(); checkResult = app.repo.integrityCheck() }
                }
                RowDivider(54.dp)
                ListRow("Delete everything", titleColor = Knit.colors.error, leading = { KIcon(Icons.Trash, Knit.colors.error, 22.dp) }) { wiping = true }
            }
        }

        item { SectionHeader("About") }
        item {
            Group {
                ListRow("Knit Studio", value = "${appVersion()} · $platformName")
                RowDivider()
                ListRow("Patterns", value = (patterns?.size ?: 0).toString())
                RowDivider()
                ListRow("File format", value = "version ${KnitFiles.VERSION}")
            }
            SectionFooter("A personal knitting studio. No account, no network, no tracking — it works entirely offline.")
        }
    }

    pendingRestore?.let { text ->
        Sheet("Restore backup", { pendingRestore = null }, doneLabel = "Cancel") {
            SectionFooter("Choose how the backup is combined with what is on this device.")
            Spacer(Modifier.height(16.dp))
            SoftButton("Merge — keep what I have", {
                scope.launch {
                    app.repo.restoreBackup(text, replace = false).fold(
                        { app.message("Restored ${it.patterns} pattern${if (it.patterns == 1) "" else "s"}") }, { app.message(it.message ?: "Restore failed") })
                }
                pendingRestore = null
            }, Modifier.padding(horizontal = PagePadding).fillMaxWidth(), icon = Icons.Layers)
            SectionFooter("Patterns in the backup are added. Where a pattern exists in both, the backup's version wins.")
            Spacer(Modifier.height(16.dp))
            PrimaryButton("Replace everything", {
                scope.launch {
                    app.flushAll()
                    app.repo.restoreBackup(text, replace = true).fold(
                        { app.message("Restored ${it.patterns} pattern${if (it.patterns == 1) "" else "s"}") }, { app.message(it.message ?: "Restore failed") })
                }
                pendingRestore = null
            }, Modifier.padding(horizontal = PagePadding).fillMaxWidth(), destructive = true)
            SectionFooter("Everything currently on this device is removed first. If the backup cannot be read, nothing is changed.")
        }
    }
    checkResult?.let { problems ->
        Sheet("Check data", { checkResult = null }) {
            if (problems.isEmpty()) {
                studio.knit.ui.components.EmptyState(Icons.CheckCircle, "All good", "The database and all ${patterns?.size ?: 0} patterns are intact.")
            } else {
                Spacer(Modifier.height(8.dp))
                Group { problems.forEachIndexed { i, p -> if (i > 0) RowDivider(50.dp); ListRow(p, leading = { KIcon(Icons.Warning, Knit.colors.warning, 22.dp) }) } }
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Repair what can be repaired", {
                    scope.launch { val n = app.repo.repair(); app.message(if (n == 0) "Nothing could be repaired automatically" else "Repaired $n pattern${if (n == 1) "" else "s"}"); checkResult = null }
                }, Modifier.padding(horizontal = PagePadding).fillMaxWidth())
                SectionFooter("Repair fixes damaged chart grids and removes broken references. Unreadable patterns are left in place and can still be included in a backup.")
            }
        }
    }
    if (wiping) {
        ConfirmDialog("Delete everything?", "All patterns, photos, your own stitches and the stash are removed from this device. Make a backup first if you might want them back.",
            "Delete everything", { scope.launch { app.repo.deleteEverything(); app.message("Everything was deleted") } }, { wiping = false }, destructive = true)
    }
}
