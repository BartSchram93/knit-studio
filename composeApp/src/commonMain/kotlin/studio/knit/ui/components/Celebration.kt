package studio.knit.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import studio.knit.ui.theme.Knit
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** What is being celebrated. */
data class Celebration(val title: String, val message: String, val id: Long, val emoji: String = "🎉")

private class Piece(val x: Float, val vx: Float, val vy: Float, val spin: Float, val size: Float, val color: Int, val round: Boolean)

/**
 * Confetti in the palette's colours bursting from the top, with a short congratulation. Goes away
 * by itself after a few seconds or when tapped.
 */
@Composable
fun CelebrationOverlay(celebration: Celebration, onDone: () -> Unit) {
    val style = studio.knit.ui.app.LocalApp.current.settings.value.confetti
    val progress = remember(celebration.id) { Animatable(0f) }
    val pieces = remember(celebration.id) {
        val r = Random(celebration.id)
        List(90) {
            val angle = (r.nextFloat() * 140f + 20f) * (kotlin.math.PI.toFloat() / 180f)
            val speed = 0.55f + r.nextFloat() * 0.75f
            Piece(
                x = 0.5f + (r.nextFloat() - 0.5f) * 0.2f, vx = cos(angle) * speed * 0.6f, vy = -sin(angle) * speed,
                spin = (r.nextFloat() - 0.5f) * 900f, size = 6f + r.nextFloat() * 8f, color = r.nextInt(6), round = r.nextBoolean(),
            )
        }
    }
    LaunchedEffect(celebration.id) {
        progress.animateTo(1f, tween(2600, easing = LinearEasing))
        delay(600)
        onDone()
    }
    val colors = Knit.colors.festive
    Box(Modifier.fillMaxSize().pressable(onClick = onDone)) {
        Canvas(Modifier.fillMaxSize()) {
            val t = progress.value
            for (p in pieces) {
                // Thrown up, then falling with gravity.
                val x = (p.x + p.vx * t) * size.width
                val y = size.height * (0.18f + p.vy * t * 0.55f + 1.25f * t * t)
                val alpha = (1f - ((t - 0.7f) / 0.3f).coerceIn(0f, 1f))
                val c = colors[p.color % colors.size].copy(alpha = alpha)
                rotate(p.spin * t, Offset(x, y)) { piece(style, p.round, c, Offset(x, y), p.size * density) }
            }
        }
        Column(
            Modifier.align(Alignment.Center).padding(32.dp).shadow(16.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp))
                .background(Knit.colors.surface).padding(horizontal = 28.dp, vertical = 22.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(celebration.emoji, style = Knit.type.title1)
            Spacer(Modifier.height(6.dp))
            Text(celebration.title, style = Knit.type.title2, color = Knit.colors.label, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(celebration.message, style = Knit.type.subhead, color = Knit.colors.secondaryLabel, textAlign = TextAlign.Center)
        }
    }
}

/** One piece of confetti in the chosen style. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.piece(
    style: studio.knit.data.ConfettiStyle, round: Boolean, c: androidx.compose.ui.graphics.Color, at: Offset, size: Float,
) {
    when (style) {
        studio.knit.data.ConfettiStyle.CLASSIC ->
            if (round) drawCircle(c, size / 2.2f, at) else drawRect(c, Offset(at.x - size / 2, at.y - size / 4), Size(size, size / 2))
        studio.knit.data.ConfettiStyle.YARN -> {
            // A little ball of yarn: a circle with wound strands.
            val r = size * 0.7f
            drawCircle(c, r, at)
            val line = c.copy(alpha = c.alpha * 0.5f).let { androidx.compose.ui.graphics.Color(1f, 1f, 1f, it.alpha) }
            drawArc(line, 200f, 140f, false, Offset(at.x - r * 0.7f, at.y - r * 0.7f), Size(r * 1.4f, r * 1.4f), style = androidx.compose.ui.graphics.drawscope.Stroke(size * 0.12f))
            drawArc(line, 20f, 140f, false, Offset(at.x - r * 0.45f, at.y - r * 0.45f), Size(r * 0.9f, r * 0.9f), style = androidx.compose.ui.graphics.drawscope.Stroke(size * 0.12f))
        }
        studio.knit.data.ConfettiStyle.STARS -> {
            val path = androidx.compose.ui.graphics.Path()
            for (i in 0 until 10) {
                val radius = if (i % 2 == 0) size * 0.8f else size * 0.35f
                val a = (i * 36 - 90) * (kotlin.math.PI.toFloat() / 180f)
                val px = at.x + kotlin.math.cos(a) * radius
                val py = at.y + kotlin.math.sin(a) * radius
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()
            drawPath(path, c)
        }
        studio.knit.data.ConfettiStyle.LEAVES -> {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(at.x, at.y - size * 0.8f)
                quadraticTo(at.x + size * 0.7f, at.y, at.x, at.y + size * 0.8f)
                quadraticTo(at.x - size * 0.7f, at.y, at.x, at.y - size * 0.8f)
                close()
            }
            drawPath(path, c)
        }
    }
}
