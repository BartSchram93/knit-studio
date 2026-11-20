package studio.knit.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.knit.chart.CellRect
import studio.knit.chart.cell
import studio.knit.chart.rowNumber
import studio.knit.chart.sideOf
import studio.knit.chart.stitchNumber
import studio.knit.model.Chart
import studio.knit.model.Direction
import studio.knit.model.Side
import studio.knit.model.StitchDef
import studio.knit.stitch.Glyphs
import studio.knit.stitch.Prim
import studio.knit.stitch.StitchLibrary
import studio.knit.ui.theme.Knit

@Immutable
data class ChartStyle(
    val paper: Color, val grid: Color, val ink: Color, val shade: Color, val accent: Color,
    val numbers: Color, val background: Color, val wsRow: Color,
    /** Whether wrong-side rows of flat charts get [wsRow] as their background. */
    val shadeWs: Boolean = true,
)

@Composable
fun rememberChartStyle(): ChartStyle {
    val c = Knit.colors
    val settings by studio.knit.ui.app.LocalApp.current.settings.collectAsState()
    return remember(c, settings.shadeWsRows) {
        ChartStyle(c.chartPaper, c.chartGrid, c.chartInk, c.chartShade, c.accent, c.secondaryLabel, c.background, c.chartWsRow, settings.shadeWsRows)
    }
}

/** Draws one chart symbol into a box [width] cells wide with its top-left corner at ([x], [y]). */
fun DrawScope.drawGlyph(
    key: String, width: Int, x: Float, y: Float, cell: Float, color: Color, shade: Color, measurer: TextMeasurer?,
) {
    val sw = (cell * 0.09f).coerceIn(1.2f, 5f)
    for (p in Glyphs.prims(key, width)) when (p) {
        is Prim.Line -> drawLine(color, Offset(x + p.x1 * cell, y + p.y1 * cell), Offset(x + p.x2 * cell, y + p.y2 * cell), sw, StrokeCap.Round)
        is Prim.Poly -> {
            val path = Path()
            var i = 0
            while (i + 1 < p.points.size) {
                val px = x + p.points[i] * cell
                val py = y + p.points[i + 1] * cell
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                i += 2
            }
            if (p.closed) path.close()
            drawPath(path, color, style = if (p.filled) Fill else Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        is Prim.Dot -> drawCircle(color, p.r * cell, Offset(x + p.cx * cell, y + p.cy * cell), style = if (p.filled) Fill else Stroke(sw))
        is Prim.Label -> if (measurer != null && cell >= 9f) {
            val factor = when (p.text.length) { 1 -> 0.6f; 2 -> 0.46f; else -> 0.36f }
            val style = TextStyle(color = color, fontSize = (cell * factor / density / fontScale).sp, fontWeight = FontWeight.Bold)
            val layout = measurer.measure(p.text, style, maxLines = 1)
            drawText(layout, topLeft = Offset(x + (width * cell - layout.size.width) / 2f, y + (cell - layout.size.height) / 2f))
        }
        Prim.Shade -> drawRect(shade, Offset(x, y), Size(cell * width, cell))
    }
}

/**
 * Renders a rectangular part of a chart.
 *
 * @param origin top-left corner of the full grid in canvas pixels.
 * @param cell size of one cell in pixels.
 * @param rows/cols the visible range, so large charts only draw what is on screen.
 * @param highlightRow when set, that row is emphasised and all others are dimmed (knitting mode).
 */
fun DrawScope.drawChart(
    chart: Chart, library: StitchLibrary, style: ChartStyle, measurer: TextMeasurer?,
    origin: Offset, cell: Float,
    rows: IntRange = 0 until chart.rows, cols: IntRange = 0 until chart.cols,
    selection: CellRect? = null, highlightRow: Int? = null, doneBelow: Int? = null,
    numbers: Boolean = false, detail: Boolean = true,
) {
    if (chart.cells.size != chart.cols * chart.rows || rows.isEmpty() || cols.isEmpty()) return
    fun top(row: Int) = origin.y + (chart.rows - 1 - row) * cell
    fun left(col: Int) = origin.x + col * cell
    val w = chart.cols * cell
    val h = chart.rows * cell

    drawRect(style.paper, origin, Size(w, h))
    // Wrong-side rows are read in the opposite direction; a faint tint makes them easy to spot.
    if (style.shadeWs && chart.construction == studio.knit.model.Construction.FLAT) {
        for (r in rows) if (chart.sideOf(r) == Side.WS) {
            drawRect(style.wsRow, Offset(left(cols.first), top(r)), Size((cols.last - cols.first + 1) * cell, cell))
        }
    }

    // Cells.
    for (r in rows) for (c in cols) {
        val data = chart.cell(r, c)
        val x = left(c)
        val y = top(r)
        val fill = chart.palette.getOrNull(data.color)?.let { Color(it.argb) }
        if (fill != null) drawRect(fill, Offset(x, y), Size(cell + 0.5f, cell + 0.5f))
        if (data.part != 0) {
            // Draw a wide stitch whose head is scrolled out of view.
            if (c == cols.first) {
                val def = library.require(data.stitch)
                if (detail) drawGlyph(def.symbol, def.width.coerceAtLeast(1), left(c - data.part), y, cell, style.ink, style.shade, measurer)
            }
            continue
        }
        val def = library.require(data.stitch)
        if (def.symbol == "blank") continue
        val ink = if (fill != null && fill.luminance() < 0.4f) Color.White else if (fill != null) Color(0xFF1C1C1E) else style.ink
        if (detail || def.symbol == "shade") drawGlyph(def.symbol, def.width.coerceAtLeast(1), x, y, cell, ink, style.shade, measurer)
    }

    // Grid lines. Inner lines of a wide stitch are left out so a cable reads as one symbol.
    if (cell >= 4f) {
        val thin = if (cell < 14f) 0.6f else 1f
        for (r in rows.first..rows.last + 1) {
            val y = origin.y + (chart.rows - r) * cell
            drawLine(style.grid, Offset(left(cols.first), y), Offset(left(cols.last + 1), y), thin)
        }
        for (c in cols.first..cols.last + 1) {
            val x = left(c)
            if (c == 0 || c == chart.cols) continue
            var start = -1
            for (r in rows.first..rows.last + 1) {
                val draw = r <= rows.last && chart.cell(r, c).part == 0
                if (draw && start < 0) start = r
                if (!draw && start >= 0) {
                    drawLine(style.grid, Offset(x, top(r - 1)), Offset(x, top(start) + cell), thin)
                    start = -1
                }
            }
        }
        // Every tenth line is heavier to make counting easy.
        if (chart.cols > 12 || chart.rows > 12) {
            val heavy = style.ink.copy(alpha = 0.45f)
            for (c in cols.first..cols.last + 1) {
                val n = if (chart.rsDirection == Direction.RIGHT_TO_LEFT) chart.cols - c else c
                if (n % 10 == 0 && c != 0 && c != chart.cols) drawLine(heavy, Offset(left(c), top(rows.last)), Offset(left(c), top(rows.first) + cell), thin * 1.8f)
            }
            for (r in rows) if (r % 10 == 0 && r != 0) drawLine(heavy, Offset(left(cols.first), top(r) + cell), Offset(left(cols.last + 1), top(r) + cell), thin * 1.8f)
        }
    }
    drawRect(style.ink.copy(alpha = 0.7f), origin, Size(w, h), style = Stroke(if (cell < 8f) 1f else 1.6f))

    // Knitting mode: fade finished rows, dim the rest, frame the current row.
    if (highlightRow != null) {
        for (r in rows) {
            if (r == highlightRow) continue
            val alpha = if (doneBelow != null && r < doneBelow) 0.72f else 0.45f
            drawRect(style.paper.copy(alpha = alpha), Offset(left(cols.first), top(r)), Size((cols.last - cols.first + 1) * cell, cell))
        }
        if (highlightRow in 0 until chart.rows) {
            drawRect(style.accent.copy(alpha = 0.10f), Offset(origin.x, top(highlightRow)), Size(w, cell))
            drawRect(style.accent, Offset(origin.x, top(highlightRow)), Size(w, cell), style = Stroke((cell * 0.12f).coerceIn(2f, 6f)))
        }
    }

    // Repeat boxes.
    for (rep in chart.repeats) {
        val sw = (cell * 0.12f).coerceIn(2f, 6f)
        drawRoundRect(
            style.accent, Offset(left(rep.colStart), top(rep.rowEnd)),
            Size((rep.colEnd - rep.colStart + 1) * cell, (rep.rowEnd - rep.rowStart + 1) * cell),
            CornerRadius(sw, sw), style = Stroke(sw),
        )
    }

    // Selection.
    if (selection != null) {
        val tl = Offset(left(selection.col0), top(selection.row1))
        val size = Size(selection.cols * cell, selection.rows * cell)
        drawRect(style.accent.copy(alpha = 0.16f), tl, size)
        drawRect(style.accent, tl, size, style = Stroke(2.5f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 7f))))
    }

    // Row and stitch numbers in the margins.
    if (numbers && measurer != null && cell >= 12f) {
        val textStyle = TextStyle(color = style.numbers, fontSize = (cell * 0.42f / density / fontScale).coerceIn(8f, 13f).sp)
        for (r in rows) {
            val label = measurer.measure(chart.rowNumber(r).toString(), textStyle, maxLines = 1)
            val startsRight = (chart.sideOf(r) == Side.RS) == (chart.rsDirection == Direction.RIGHT_TO_LEFT)
            val y = top(r) + (cell - label.size.height) / 2f
            if (startsRight) drawText(label, topLeft = Offset(origin.x + w + cell * 0.25f, y))
            else drawText(label, topLeft = Offset(origin.x - cell * 0.25f - label.size.width, y))
        }
        val step = if (cell < 22f) 5 else 1
        for (c in cols) {
            val n = chart.stitchNumber(c)
            if (n != 1 && n % step != 0) continue
            val label = measurer.measure(n.toString(), textStyle, maxLines = 1)
            drawText(label, topLeft = Offset(left(c) + (cell - label.size.width) / 2f, origin.y + h + cell * 0.2f))
        }
    }
}

/** A static picture of a chart scaled to fit its bounds. */
@Composable
fun ChartThumbnail(
    chart: Chart, library: StitchLibrary, modifier: Modifier = Modifier,
    highlightRow: Int? = null, doneBelow: Int? = null, numbers: Boolean = false,
) {
    val style = rememberChartStyle()
    val measurer = rememberTextMeasurer(cacheSize = 64)
    Canvas(modifier.semantics { contentDescription = "Chart ${chart.name}, ${chart.cols} stitches by ${chart.rows} rows" }) {
        val gutter = if (numbers) 1.6f else 0f
        val cell = minOf(size.width / (chart.cols + gutter * 2), size.height / (chart.rows + gutter))
        if (cell <= 0f) return@Canvas
        val origin = Offset((size.width - chart.cols * cell) / 2f, (size.height - chart.rows * cell - (if (numbers) cell else 0f)) / 2f)
        drawChart(chart, library, style, measurer, origin, cell, highlightRow = highlightRow, doneBelow = doneBelow, numbers = numbers, detail = cell >= 5f)
    }
}

/** A single stitch symbol in a framed cell, for palettes, legends and the stitch library. */
@Composable
fun StitchSymbol(def: StitchDef, modifier: Modifier = Modifier, cell: Dp = 30.dp, framed: Boolean = true, tint: Color = Knit.colors.chartInk) {
    val style = rememberChartStyle()
    val measurer = rememberTextMeasurer(cacheSize = 16)
    val w = def.width.coerceIn(1, 6)
    // Wide stitches are shown at reduced cell size so every symbol fits the same slot.
    val unit = if (w <= 2) cell else cell * 2 / w
    Box(modifier.size(unit * w, cell).semantics { contentDescription = def.name }) {
        Canvas(Modifier.size(unit * w, unit).align(androidx.compose.ui.Alignment.Center)) {
            val c = unit.toPx()
            if (framed) {
                drawRect(style.paper, size = Size(c * w, c))
            }
            drawGlyph(def.symbol, w, 0f, 0f, c, tint, style.shade, measurer)
            if (framed) drawRect(style.grid, size = Size(c * w, c), style = Stroke(1.2f))
        }
    }
}
