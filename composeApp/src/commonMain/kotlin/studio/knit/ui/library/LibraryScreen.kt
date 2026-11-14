package studio.knit.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import studio.knit.io.KnitFiles
import studio.knit.model.Category
import studio.knit.model.Pattern
import studio.knit.model.PatternCollection
import studio.knit.ui.app.LocalApp
import studio.knit.ui.app.LocalNav
import studio.knit.ui.app.PatternRowContent
import studio.knit.ui.components.CardShape
import studio.knit.ui.components.Chip
import studio.knit.ui.components.ConfirmDialog
import studio.knit.ui.components.EmptyState
import studio.knit.ui.components.FieldShape
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
import studio.knit.ui.components.PrimaryButton
import studio.knit.ui.components.PromptDialog
import studio.knit.ui.components.RowDivider
import studio.knit.ui.components.SectionFooter
import studio.knit.ui.components.Sheet
import studio.knit.ui.components.pressable
import studio.knit.ui.home.CreateSheet
import studio.knit.ui.platform.rememberPlatformActions
import studio.knit.ui.theme.Knit
import studio.knit.util.newId
import studio.knit.util.nowMillis

private enum class StateFilter(val label: String) { ALL("All"), FAVOURITES("Favourites"), KNITTING("Knitting"), DRAFTS("Drafts"), ARCHIVED("Archived") }
private enum class SortOrder(val label: String) { RECENT("Recently changed"), NAME("Name"), CREATED("Date created"), CATEGORY("Category") }

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    Row(modifier.clip(FieldShape).background(Knit.colors.fill).padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        KIcon(Icons.Search, Knit.colors.secondaryLabel, 18.dp)
        Spacer(Modifier.width(7.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = Knit.type.body, color = Knit.colors.tertiaryLabel, maxLines = 1)
            BasicTextField(
                value, onChange, Modifier.fillMaxWidth().semantics { contentDescription = placeholder },
                textStyle = Knit.type.body.copy(color = Knit.colors.label), singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), cursorBrush = SolidColor(Knit.colors.accent),
            )
        }
        if (value.isNotEmpty()) KIcon(Icons.Close, Knit.colors.secondaryLabel, 16.dp, Modifier.pressable { onChange("") }, "Clear search")
    }
}

@Composable
fun LibraryScreen() {
    val app = LocalApp.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val platform = rememberPlatformActions()
    val all by app.patterns.collectAsState()
    val library by app.library.collectAsState()
    val collections by app.repo.collections.collectAsState(emptyList())

    var query by rememberSaveable { mutableStateOf("") }
    var stateFilter by rememberSaveable { mutableStateOf(StateFilter.ALL) }
    var category by rememberSaveable { mutableStateOf<Category?>(null) }
    var collectionId by rememberSaveable { mutableStateOf<String?>(null) }
    var sort by rememberSaveable { mutableStateOf(SortOrder.RECENT) }
    var creating by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<Pattern?>(null) }
    var deleting by remember { mutableStateOf<Pattern?>(null) }
    var moving by remember { mutableStateOf<Pattern?>(null) }
    var managing by remember { mutableStateOf(false) }
    val now = remember(all) { nowMillis() }

    val shown = remember(all, query, stateFilter, category, collectionId, sort) {
        val q = query.trim().lowercase()
        all.orEmpty().asSequence()
            .filter { if (stateFilter == StateFilter.ARCHIVED) it.archived else !it.archived }
            .filter {
                when (stateFilter) {
                    StateFilter.FAVOURITES -> it.favorite
                    StateFilter.KNITTING -> it.progress != null && it.progress?.finishedAt == null
                    StateFilter.DRAFTS -> it.draft
                    else -> true
                }
            }
            .filter { category == null || it.category == category }
            .filter { collectionId == null || it.collectionId == collectionId }
            .filter {
                q.isEmpty() || it.name.lowercase().contains(q) || it.description.lowercase().contains(q) || it.notes.lowercase().contains(q) ||
                    it.category.label.lowercase().contains(q) || it.yarns.any { y -> y.displayName.lowercase().contains(q) }
            }
            .let { seq ->
                when (sort) {
                    SortOrder.RECENT -> seq.sortedByDescending { it.updatedAt }
                    SortOrder.NAME -> seq.sortedBy { it.displayName.lowercase() }
                    SortOrder.CREATED -> seq.sortedByDescending { it.createdAt }
                    SortOrder.CATEGORY -> seq.sortedWith(compareBy({ it.category.ordinal }, { it.displayName.lowercase() }))
                }
            }.toList()
    }

    Page(
        "Patterns",
        actions = {
            MenuButton(SortOrder.entries.map { o -> MenuItem(o.label, if (o == sort) Icons.Check else null) { sort = o } }, icon = Icons.Sort, description = "Sort")
            IconBtn(Icons.Plus, "New pattern", { creating = true }, size = 26.dp)
        },
    ) {
        item { SearchField(query, { query = it }, "Search patterns", Modifier.padding(horizontal = PagePadding).fillMaxWidth()) }
        item {
            Row(Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState()).padding(horizontal = PagePadding), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (f in StateFilter.entries) Chip(f.label, stateFilter == f, { stateFilter = f })
            }
        }
        item {
            Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()).padding(horizontal = PagePadding), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                var catOpen by remember { mutableStateOf(false) }
                Box {
                    Chip(category?.label ?: "Category", category != null, { catOpen = true }, leading = { KIcon(Icons.Filter, if (category != null) Knit.colors.onAccent else Knit.colors.secondaryLabel, 15.dp) })
                    Menu(catOpen, { catOpen = false }, listOf(MenuItem("All categories", if (category == null) Icons.Check else null) { category = null }) +
                        Category.entries.map { c -> MenuItem(c.label, if (c == category) Icons.Check else null) { category = c } })
                }
                var colOpen by remember { mutableStateOf(false) }
                Box {
                    Chip(collections.firstOrNull { it.id == collectionId }?.name ?: "Collection", collectionId != null, { colOpen = true },
                        leading = { KIcon(Icons.Folder, if (collectionId != null) Knit.colors.onAccent else Knit.colors.secondaryLabel, 15.dp) })
                    Menu(colOpen, { colOpen = false }, listOf(MenuItem("All collections", if (collectionId == null) Icons.Check else null) { collectionId = null }) +
                        collections.map { c -> MenuItem(c.name, if (c.id == collectionId) Icons.Check else null) { collectionId = c.id } } +
                        MenuItem("Edit collections…", Icons.Pencil) { managing = true })
                }
            }
        }
        item { Spacer(Modifier.height(14.dp)) }

        if (all != null && shown.isEmpty()) {
            item {
                if (all.orEmpty().isEmpty()) {
                    EmptyState(Icons.Library, "No patterns yet", "Everything you create is kept here, on this device.") {
                        PrimaryButton("New pattern", { creating = true }, icon = Icons.Plus)
                    }
                } else EmptyState(Icons.Search, "Nothing found", "No pattern matches the current search and filters.")
            }
        }
        items(shown, key = { it.id }) { p ->
            Box(Modifier.padding(horizontal = PagePadding).padding(bottom = 8.dp)) {
                PatternRowContent(
                    p, library, now,
                    Modifier.clip(CardShape).background(Knit.colors.surface).pressable(onLongClick = { menuFor = p }) { nav.openPattern(p.id) },
                )
                Box(Modifier.align(Alignment.CenterEnd)) {
                    Menu(menuFor?.id == p.id, { menuFor = null }, patternMenu(
                        p,
                        onFavourite = { scope.launch { app.repo.save(p.copy(favorite = !p.favorite), touch = false) } },
                        onDuplicate = { scope.launch { app.repo.duplicate(p.id)?.let { app.message("Duplicated") } } },
                        onArchive = { scope.launch { app.repo.save(p.copy(archived = !p.archived), touch = false); app.message(if (p.archived) "Restored" else "Archived") } },
                        onMove = { moving = p },
                        onExport = {
                            scope.launch {
                                app.repo.exportPattern(p.id)?.let { platform.shareFile(KnitFiles.fileName(p.displayName, KnitFiles.PATTERN_EXTENSION), KnitFiles.MIME, it.encodeToByteArray()) }
                            }
                        },
                        onDelete = { deleting = p },
                    ))
                }
            }
        }
        if (shown.isNotEmpty()) item { SectionFooter("${shown.size} pattern${if (shown.size == 1) "" else "s"}  ·  touch and hold for more") }
    }

    if (creating) CreateSheet { creating = false }
    deleting?.let { p ->
        ConfirmDialog("Delete “${p.displayName}”?", "The pattern, its charts, rows and knitting progress will be removed from this device. This cannot be undone.",
            "Delete", { scope.launch { app.release(p.id); app.repo.delete(p.id) } }, { deleting = null }, destructive = true)
    }
    moving?.let { p ->
        Sheet("Move to collection", { moving = null }) {
            Spacer(Modifier.height(8.dp))
            Group {
                ListRow("None", trailing = { if (p.collectionId == null) KIcon(Icons.Check, Knit.colors.accent, 18.dp) }) {
                    scope.launch { app.repo.save(p.copy(collectionId = null), touch = false) }; moving = null
                }
                for (c in collections) {
                    RowDivider()
                    ListRow(c.name, trailing = { if (p.collectionId == c.id) KIcon(Icons.Check, Knit.colors.accent, 18.dp) }) {
                        scope.launch { app.repo.save(p.copy(collectionId = c.id), touch = false) }; moving = null
                    }
                }
            }
            if (collections.isEmpty()) SectionFooter("Create collections from the Collection filter to group patterns — by recipient, season or anything else.")
        }
    }
    if (managing) CollectionsSheet(collections) { managing = false }
}

private fun patternMenu(
    p: Pattern, onFavourite: () -> Unit, onDuplicate: () -> Unit, onArchive: () -> Unit, onMove: () -> Unit,
    onExport: () -> Unit, onDelete: () -> Unit,
): List<MenuItem> = listOf(
    MenuItem(if (p.favorite) "Remove from favourites" else "Add to favourites", if (p.favorite) Icons.StarFilled else Icons.Star, onClick = onFavourite),
    MenuItem("Duplicate", Icons.Copy, onClick = onDuplicate),
    MenuItem("Move to collection", Icons.Folder, onClick = onMove),
    MenuItem("Share pattern file", Icons.Share, onClick = onExport),
    MenuItem(if (p.archived) "Restore from archive" else "Archive", Icons.Archive, onClick = onArchive),
    MenuItem("Delete", Icons.Trash, destructive = true, onClick = onDelete),
)

@Composable
private fun CollectionsSheet(collections: List<PatternCollection>, onDismiss: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<PatternCollection?>(null) }
    var deleting by remember { mutableStateOf<PatternCollection?>(null) }
    Sheet("Collections", onDismiss) {
        Spacer(Modifier.height(8.dp))
        Group {
            collections.forEach { c ->
                ListRow(c.name, onClick = { renaming = c }, trailing = {
                    IconBtn(Icons.Trash, "Delete ${c.name}", { deleting = c }, tint = Knit.colors.error, size = 20.dp)
                })
                RowDivider()
            }
            ListRow("New collection", titleColor = Knit.colors.accent, leading = { KIcon(Icons.Plus, Knit.colors.accent, 20.dp) }) { adding = true }
        }
        SectionFooter("Deleting a collection keeps its patterns.")
    }
    if (adding) PromptDialog("New collection", "", "Add", { name -> scope.launch { app.repo.saveCollection(PatternCollection(newId(), name, collections.size)) } }, { adding = false }, placeholder = "Name")
    renaming?.let { c -> PromptDialog("Rename collection", c.name, "Save", { name -> scope.launch { app.repo.saveCollection(c.copy(name = name)) } }, { renaming = null }) }
    deleting?.let { c -> ConfirmDialog("Delete “${c.name}”?", "Its patterns stay in your library.", "Delete", { scope.launch { app.repo.deleteCollection(c.id) } }, { deleting = null }, destructive = true) }
}
