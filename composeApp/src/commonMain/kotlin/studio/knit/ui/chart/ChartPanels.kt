package studio.knit.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.knit.chart.rowNumber
import studio.knit.model.Chart
import studio.knit.model.Direction
import studio.knit.stitch.StitchLibrary
import studio.knit.ui.theme.Knit
import kotlin.math.floor

/** Stitch ranges ("1–20", "21–40", …) a chart of [cols] stitches is cut into. */
fun panelRanges(cols: Int, perPanel: Int): List<IntRange> {
    val step = perPanel.coerceAtLeast(1)
    return (1..cols step step).map { a -> a..minOf(cols, a + step - 1) }
}

/** Columns holding stitches [stitches] (stitch 1 is on the right for right-to-left charts). */
fun Chart.columnsFor(stitches: IntRange): IntRange =
    if (rsDirection == Direction.RIGHT_TO_LEFT) (cols - stitches.last)..(cols - stitches.first)
    else (stitches.first - 1)..(stitches.last - 1)

/** How many stitches fit in one panel of [width] at a readable cell size. */
fun stitchesPerPanel(width: Dp, cell: Dp = 26.dp, cols: Int): Int {
    val fit = ((width.value - 2 * MARGIN_CELLS * cell.value) / cell.value).toInt().coerceAtLeast(5)
    if (fit >= cols) return cols
    // Round down to a multiple of 5 so panel borders fall on easy-to-count stitches.
    return (fit / 5 * 5).coerceAtLeast(5)
}

private const val MARGIN_CELLS = 1.3f

/**
 * A wide chart cut into panels of stitches stacked under each other, so it can be read on a
 * phone without panning sideways: "Stitches 1–20", then "21–40", … Each panel has its own row
 * and stitch numbers. Only [rows] are shown (knitting mode shows a few around the current row).
 * [onTap] receives (row, col) when a cell is tapped.
 */
@Composable
fun ChartPanels(
    chart: Chart, library: StitchLibrary, modifier: Modifier = Modifier,
    rows: IntRange = 0 until chart.rows,
    highlightRow: Int? = null, doneBelow: Int? = null,
    cellSize: Dp = 26.dp,
    onTap: ((Int, Int) -> Unit)? = null,
) {
    if (chart.cells.size != chart.cols * chart.rows || rows.isEmpty()) return
    val style = rememberChartStyle()
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val per = stitchesPerPanel(maxWidth, cellSize, chart.cols)
        // Cells grow to fill the width when a panel holds fewer stitches than would fit.
        val cell = minOf(maxWidth.value / (per + 2 * MARGIN_CELLS), cellSize.value * 1.6f).dp
        val density = LocalDensity.current
        val cellPx = with(density) { cell.toPx() }
        Column {
            for (stitches in panelRanges(chart.cols, per)) {
                val cols = chart.columnsFor(stitches)
                if (chart.cols > per) {
                    Text(
                        "Stitches ${stitches.first}–${stitches.last}",
                        style = Knit.type.footnote, color = Knit.colors.secondaryLabel,
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                    )
                }
                val widthDp = cell * (cols.last - cols.first + 1 + 2 * MARGIN_CELLS)
                val heightDp = cell * (rows.last - rows.first + 1 + 1.1f)
                Canvas(
                    Modifier.width(widthDp).height(heightDp).then(
                        if (onTap == null) Modifier
                        else Modifier.pointerInput(chart, rows, cols, cellPx) {
                            detectTapGestures { pos ->
                                val c = cols.first + floor((pos.x - MARGIN_CELLS * cellPx) / cellPx).toInt()
                                val r = rows.last - floor(pos.y / cellPx).toInt()
                                if (c in cols && r in rows) onTap(r, c)
                            }
                        },
                    ),
                ) {
                    val left = MARGIN_CELLS * cellPx
                    val gridW = (cols.last - cols.first + 1) * cellPx
                    val gridH = (rows.last - rows.first + 1) * cellPx
                    val origin = Offset(left - cols.first * cellPx, -(chart.rows - 1 - rows.last) * cellPx)
                    clipRect(left, 0f, left + gridW, gridH) {
                        drawChart(chart, library, style, measurer, origin, cellPx, rows = rows, cols = cols,
                            highlightRow = highlightRow, doneBelow = doneBelow, numbers = false)
                    }
                    drawRect(style.ink.copy(alpha = 0.7f), Offset(left, 0f), Size(gridW, gridH), style = Stroke(1.6f))
                    highlightRow?.takeIf { it in rows }?.let { r ->
                        val y = (rows.last - r) * cellPx
                        drawRect(style.accent, Offset(left, y), Size(gridW, cellPx), style = Stroke((cellPx * 0.12f).coerceIn(2f, 6f)))
                    }
                    // Row numbers on both sides, stitch numbers underneath.
                    val text = TextStyle(color = style.numbers, fontSize = (cellPx * 0.42f / density.density / density.fontScale).coerceIn(8f, 13f).sp)
                    for (r in rows) {
                        val label = measurer.measure(chart.rowNumber(r).toString(), text, maxLines = 1)
                        val y = (rows.last - r) * cellPx + (cellPx - label.size.height) / 2f
                        drawText(label, topLeft = Offset(left - cellPx * 0.2f - label.size.width, y))
                        drawText(label, topLeft = Offset(left + gridW + cellPx * 0.2f, y))
                    }
                    for (c in cols) {
                        val n = if (chart.rsDirection == Direction.RIGHT_TO_LEFT) chart.cols - c else c + 1
                        if (n != stitches.first && n != stitches.last && n % 5 != 0) continue
                        val label = measurer.measure(n.toString(), text, maxLines = 1)
                        drawText(label, topLeft = Offset(left + (c - cols.first) * cellPx + (cellPx - label.size.width) / 2f, gridH + cellPx * 0.1f))
                    }
                }
            }
        }
    }
}
