package studio.knit.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import studio.knit.ui.app.Cheer
import studio.knit.ui.app.LocalApp
import studio.knit.ui.theme.Knit

/**
 * Shows cheers one after another over every screen: big ones with confetti, small ones as a
 * card that slides in at the top and goes away by itself.
 */
@Composable
fun CheerHost() {
    val app = LocalApp.current
    val queue by app.achievements.cheers.collectAsState()
    val current = queue.firstOrNull()
    var visible by remember { mutableStateOf(false) }

    if (current != null && current.big) {
        CelebrationOverlay(Celebration(current.title, current.message, current.id, current.emoji)) { app.achievements.shown(current) }
        return
    }
    LaunchedEffect(current?.id) {
        if (current == null) return@LaunchedEffect
        visible = true
        delay(3200)
        visible = false
        delay(400)
        app.achievements.shown(current)
    }
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(visible && current != null, enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()) {
            val c = current ?: return@AnimatedVisibility
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().shadow(12.dp, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp)).background(Knit.colors.surface).pressable { visible = false }
                    .padding(horizontal = 14.dp, vertical = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(Knit.colors.accentSoft), contentAlignment = Alignment.Center) {
                    Text(c.emoji, style = Knit.type.title3)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.title, style = Knit.type.headline, color = Knit.colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(c.message, style = Knit.type.footnote, color = Knit.colors.secondaryLabel, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
