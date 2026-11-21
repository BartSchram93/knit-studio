package studio.knit.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import studio.knit.companion.overallProgress
import studio.knit.companion.statusLine
import studio.knit.model.Pattern
import studio.knit.stitch.StitchLibrary
import studio.knit.ui.app.LocalApp
import studio.knit.ui.app.LocalNav
import studio.knit.ui.app.PatternRowContent
import studio.knit.ui.app.PatternThumb
import studio.knit.ui.app.ProgressBar
import studio.knit.ui.components.Card
import studio.knit.ui.components.EmptyState
import studio.knit.ui.components.Group
import studio.knit.ui.components.IconBtn
import studio.knit.ui.components.Icons
import studio.knit.ui.components.Page
import studio.knit.ui.components.PagePadding
import studio.knit.ui.components.PrimaryButton
import studio.knit.ui.components.RowDivider
import studio.knit.ui.components.SectionHeader
import studio.knit.ui.components.GreetingCard
import studio.knit.ui.components.greetingNow
import studio.knit.ui.components.todayLine
import studio.knit.ui.components.TextBtn
import studio.knit.ui.components.pressable
import studio.knit.ui.theme.Knit
import studio.knit.util.nowMillis

@Composable
fun HomeScreen(onSeeAll: () -> Unit) {
    val app = LocalApp.current
    val nav = LocalNav.current
    val all by app.patterns.collectAsState()
    val library by app.library.collectAsState()
    var creating by remember { mutableStateOf(false) }
    val now = remember(all) { nowMillis() }

    val active = all?.filter { !it.archived }.orEmpty()
    val knitting = active.filter { it.progress != null && it.progress?.finishedAt == null }.sortedByDescending { it.progress?.lastKnitAt ?: 0 }
    val recent = active.take(4)
    val favourites = active.filter { it.favorite }
    val drafts = active.filter { it.draft && it.progress == null }.filterNot { it in recent }.take(5)

    Page("Knit Studio", actions = { IconBtn(Icons.Plus, "New pattern", { creating = true }, size = 26.dp) }) {
        if (all == null) return@Page
        if (active.isEmpty()) {
            item {
                EmptyState(
                    Icons.Yarn, "Start your first pattern",
                    "Draw a chart, write out rows, or begin from a template sized to your gauge.",
                    Modifier.padding(top = 40.dp),
                ) { PrimaryButton("New pattern", { creating = true }, icon = Icons.Plus) }
            }
            return@Page
        }

        item {
            val last = knitting.firstOrNull()
            val subtitle = todayLine(active, now)
                ?: last?.let { "Pick up ${it.displayName} where you left off." }
                ?: "What will you make today?"
            GreetingCard(
                greetingNow(), subtitle, Modifier.padding(horizontal = PagePadding).padding(top = 4.dp, bottom = 4.dp),
                action = last?.let { p -> { PrimaryButton("Continue ${p.displayName}", { nav.knit(p.id) }, icon = Icons.Play) } },
            )
        }

        if (knitting.isNotEmpty()) {
            item { SectionHeader("Continue knitting") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = PagePadding), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(knitting, key = { it.id }) { p -> KnittingCard(p, library) { nav.knit(p.id) } }
                }
            }
        }

        item { SectionHeader("Recent", action = { TextBtn("See all", onSeeAll) }) }
        item {
            Group {
                recent.forEachIndexed { i, p ->
                    if (i > 0) RowDivider(83.dp)
                    PatternRowContent(p, library, now, Modifier.pressable { nav.openPattern(p.id) })
                }
            }
        }

        if (favourites.isNotEmpty()) {
            item { SectionHeader("Favourites") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = PagePadding), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(favourites, key = { it.id }) { p ->
                        Column(Modifier.width(96.dp).pressable { nav.openPattern(p.id) }, horizontalAlignment = Alignment.CenterHorizontally) {
                            PatternThumb(p, library, 96.dp)
                            Spacer(Modifier.height(6.dp))
                            Text(p.displayName, style = Knit.type.footnote, color = Knit.colors.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        if (drafts.isNotEmpty()) {
            item { SectionHeader("Drafts") }
            item {
                Group {
                    drafts.forEachIndexed { i, p ->
                        if (i > 0) RowDivider(83.dp)
                        PatternRowContent(p, library, now, Modifier.pressable { nav.openPattern(p.id) })
                    }
                }
            }
        }
    }
    if (creating) CreateSheet { creating = false }
}

@Composable
private fun KnittingCard(pattern: Pattern, library: StitchLibrary, onClick: () -> Unit) {
    val status = remember(pattern, library) { statusLine(pattern, library) }
    val fraction = remember(pattern, library) { overallProgress(pattern, library) }
    Card(Modifier.width(264.dp), onClick = onClick, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PatternThumb(pattern, library, 52.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(pattern.displayName, style = Knit.type.headline, color = Knit.colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(status ?: "Ready to start", style = Knit.type.footnote, color = Knit.colors.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressBar(fraction ?: 0f, Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            Text(if (fraction != null) "${(fraction * 100).toInt()}%" else "—", style = Knit.type.footnote, color = Knit.colors.secondaryLabel)
        }
    }
}
