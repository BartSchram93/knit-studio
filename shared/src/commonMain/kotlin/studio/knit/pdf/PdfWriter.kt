package studio.knit.pdf

import kotlin.math.roundToInt

/**
 * A small, dependency-free PDF 1.4 writer.
 *
 * It supports exactly what a knitting pattern needs: vector lines and shapes, text in the
 * built-in Helvetica family (no font embedding, so files stay tiny and print sharply at any
 * size) and JPEG images. Because it is plain Kotlin it produces byte-identical documents on
 * Android and iOS.
 *
 * Coordinates are in points (1/72 in) with the origin at the **top-left** of the page.
 */
class PdfWriter(val pageWidth: Float, val pageHeight: Float) {

    enum class Font(val pdfName: String, val resource: String) {
        REGULAR("Helvetica", "F1"), BOLD("Helvetica-Bold", "F2"), ITALIC("Helvetica-Oblique", "F3"),
    }

    private class Page {
        val content = StringBuilder()
        val images = LinkedHashSet<Int>()
    }

    private class Image(val bytes: ByteArray, val width: Int, val height: Int, val components: Int)

    private val pages = ArrayList<Page>()
    private val images = ArrayList<Image>()
    private var cur: Page = Page().also { pages += it }

    val pageCount: Int get() = pages.size
    val currentPage: Int get() = pages.indexOf(cur)

    fun newPage() { cur = Page().also { pages += it } }

    /** Directs subsequent drawing to an existing page (used to add footers at the end). */
    fun selectPage(index: Int) { cur = pages[index] }

    // --- State ---------------------------------------------------------------------------------

    private fun n(v: Float): String {
        val r = (v * 100).roundToInt()
        val whole = r / 100
        val frac = kotlin.math.abs(r % 100)
        val sign = if (r < 0 && whole == 0) "-" else ""
        return if (frac == 0) "$sign$whole" else "$sign$whole.${frac.toString().padStart(2, '0').trimEnd('0')}"
    }

    private fun y(top: Float) = pageHeight - top

    private fun rgb(argb: Long): String {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        return "${n(r)} ${n(g)} ${n(b)}"
    }

    fun strokeColor(argb: Long) { cur.content.append(rgb(argb)).append(" RG\n") }
    fun fillColor(argb: Long) { cur.content.append(rgb(argb)).append(" rg\n") }
    fun lineWidth(w: Float) { cur.content.append(n(w)).append(" w\n") }
    fun roundCaps() { cur.content.append("1 J 1 j\n") }
    fun dash(on: Float, off: Float) { cur.content.append("[${n(on)} ${n(off)}] 0 d\n") }
    fun solid() { cur.content.append("[] 0 d\n") }

    // --- Shapes --------------------------------------------------------------------------------

    fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
        cur.content.append("${n(x1)} ${n(y(y1))} m ${n(x2)} ${n(y(y2))} l S\n")
    }

    fun rect(x: Float, top: Float, w: Float, h: Float, fill: Boolean = false, stroke: Boolean = true) {
        if (!fill && !stroke) return
        cur.content.append("${n(x)} ${n(y(top + h))} ${n(w)} ${n(h)} re ${op(fill, stroke)}\n")
    }

    /** Points as x0, y0, x1, y1, … */
    fun polyline(points: List<Float>, closed: Boolean = false, fill: Boolean = false, stroke: Boolean = true) {
        if (points.size < 4) return
        val sb = cur.content
        sb.append("${n(points[0])} ${n(y(points[1]))} m ")
        var i = 2
        while (i + 1 < points.size) { sb.append("${n(points[i])} ${n(y(points[i + 1]))} l "); i += 2 }
        if (closed) sb.append("h ")
        sb.append(op(fill, stroke)).append('\n')
    }

    fun circle(cx: Float, cy: Float, r: Float, fill: Boolean = false, stroke: Boolean = true) {
        val k = 0.5523f * r
        val yy = y(cy)
        cur.content.append(
            "${n(cx + r)} ${n(yy)} m " +
                "${n(cx + r)} ${n(yy + k)} ${n(cx + k)} ${n(yy + r)} ${n(cx)} ${n(yy + r)} c " +
                "${n(cx - k)} ${n(yy + r)} ${n(cx - r)} ${n(yy + k)} ${n(cx - r)} ${n(yy)} c " +
                "${n(cx - r)} ${n(yy - k)} ${n(cx - k)} ${n(yy - r)} ${n(cx)} ${n(yy - r)} c " +
                "${n(cx + k)} ${n(yy - r)} ${n(cx + r)} ${n(yy - k)} ${n(cx + r)} ${n(yy)} c ${op(fill, stroke)}\n",
        )
    }

    private fun op(fill: Boolean, stroke: Boolean) = when {
        fill && stroke -> "B"
        fill -> "f"
        else -> "S"
    }

    // --- Text ----------------------------------------------------------------------------------

    /** Draws [text] with its baseline at [baseline]. */
    fun text(x: Float, baseline: Float, text: String, font: Font = Font.REGULAR, size: Float = 10f) {
        if (text.isEmpty()) return
        cur.content.append("BT /${font.resource} ${n(size)} Tf ${n(x)} ${n(y(baseline))} Td <${hex(text)}> Tj ET\n")
    }

    fun textCentered(cx: Float, baseline: Float, text: String, font: Font = Font.REGULAR, size: Float = 10f) =
        text(cx - textWidth(text, font, size) / 2f, baseline, text, font, size)

    fun textRight(right: Float, baseline: Float, text: String, font: Font = Font.REGULAR, size: Float = 10f) =
        text(right - textWidth(text, font, size), baseline, text, font, size)

    fun textWidth(text: String, font: Font, size: Float): Float {
        val table = if (font == Font.BOLD) BOLD_WIDTHS else REGULAR_WIDTHS
        var w = 0
        for (ch in text) {
            val code = winAnsi(ch)
            w += if (code in 32..126) table[code - 32] else 556
        }
        return w * size / 1000f
    }

    /** Breaks [text] into lines no wider than [maxWidth]. Honours explicit line breaks. */
    fun wrap(text: String, font: Font, size: Float, maxWidth: Float): List<String> {
        val out = ArrayList<String>()
        for (paragraph in text.split('\n')) {
            val words = paragraph.split(' ').filter { it.isNotEmpty() }
            if (words.isEmpty()) { out += ""; continue }
            var line = StringBuilder()
            for (word in words) {
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (textWidth(candidate, font, size) <= maxWidth || line.isEmpty()) {
                    line = StringBuilder(candidate)
                } else {
                    out += line.toString()
                    line = StringBuilder(word)
                }
            }
            out += line.toString()
        }
        return out
    }

    private fun hex(text: String): String {
        val sb = StringBuilder(text.length * 2)
        for (ch in text) {
            val code = winAnsi(ch)
            sb.append(HEX[code shr 4]).append(HEX[code and 15])
        }
        return sb.toString()
    }

    // --- Images --------------------------------------------------------------------------------

    /** Size of a JPEG in pixels, or null if [bytes] is not a baseline/progressive JPEG. */
    fun jpegSize(bytes: ByteArray): Pair<Int, Int>? = parseJpeg(bytes)?.let { it.first to it.second }

    /** Draws a JPEG scaled into the given box. Returns false (drawing nothing) for other formats. */
    fun jpeg(bytes: ByteArray, x: Float, top: Float, w: Float, h: Float): Boolean {
        val info = parseJpeg(bytes) ?: return false
        var index = images.indexOfFirst { it.bytes === bytes }
        if (index < 0) { images += Image(bytes, info.first, info.second, info.third); index = images.size - 1 }
        cur.images += index
        cur.content.append("q ${n(w)} 0 0 ${n(h)} ${n(x)} ${n(y(top + h))} cm /Im$index Do Q\n")
        return true
    }

    private fun parseJpeg(b: ByteArray): Triple<Int, Int, Int>? {
        if (b.size < 4 || b[0] != 0xFF.toByte() || b[1] != 0xD8.toByte()) return null
        var i = 2
        while (i + 9 < b.size) {
            if (b[i] != 0xFF.toByte()) { i++; continue }
            val marker = b[i + 1].toInt() and 0xFF
            if (marker == 0xFF) { i++; continue }
            if (marker in 0xD0..0xD9 || marker == 0x01) { i += 2; continue }
            val len = ((b[i + 2].toInt() and 0xFF) shl 8) or (b[i + 3].toInt() and 0xFF)
            // SOF0..SOF15 except DHT (C4), JPG (C8) and DAC (CC) carry the frame size.
            if (marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
                val height = ((b[i + 5].toInt() and 0xFF) shl 8) or (b[i + 6].toInt() and 0xFF)
                val width = ((b[i + 7].toInt() and 0xFF) shl 8) or (b[i + 8].toInt() and 0xFF)
                val comps = b[i + 9].toInt() and 0xFF
                return if (width > 0 && height > 0) Triple(width, height, comps) else null
            }
            i += 2 + len
        }
        return null
    }

    // --- Output --------------------------------------------------------------------------------

    private class Out {
        var buf = ByteArray(1 shl 16)
        var size = 0
        fun write(b: ByteArray) {
            if (size + b.size > buf.size) buf = buf.copyOf(maxOf(buf.size * 2, size + b.size))
            b.copyInto(buf, size)
            size += b.size
        }
        fun write(s: String) = write(s.encodeToByteArray())
    }

    fun build(title: String = "", author: String = "Knit Studio"): ByteArray {
        val out = Out()
        val offsets = ArrayList<Int>()
        // Object numbering: 1 catalog, 2 pages, 3-5 fonts, 6 info, then images, then page+content pairs.
        val firstImage = 7
        val firstPage = firstImage + images.size
        val total = firstPage + pages.size * 2 - 1

        fun obj(number: Int, body: () -> Unit) {
            while (offsets.size < number) offsets += 0
            offsets[number - 1] = out.size
            out.write("$number 0 obj\n")
            body()
            out.write("\nendobj\n")
        }

        out.write("%PDF-1.4\n")
        out.write(byteArrayOf('%'.code.toByte(), 0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte(), '\n'.code.toByte()))

        obj(1) { out.write("<< /Type /Catalog /Pages 2 0 R >>") }
        obj(2) {
            val kids = pages.indices.joinToString(" ") { "${firstPage + it * 2} 0 R" }
            out.write("<< /Type /Pages /Count ${pages.size} /Kids [$kids] >>")
        }
        Font.entries.forEachIndexed { i, f ->
            obj(3 + i) { out.write("<< /Type /Font /Subtype /Type1 /BaseFont /${f.pdfName} /Encoding /WinAnsiEncoding >>") }
        }
        obj(6) { out.write("<< /Title <${hex(title)}> /Creator <${hex(author)}> /Producer <${hex("Knit Studio PDF writer")}> >>") }
        images.forEachIndexed { i, img ->
            obj(firstImage + i) {
                val cs = when (img.components) { 1 -> "/DeviceGray"; 4 -> "/DeviceCMYK"; else -> "/DeviceRGB" }
                out.write("<< /Type /XObject /Subtype /Image /Width ${img.width} /Height ${img.height} /ColorSpace $cs /BitsPerComponent 8 /Filter /DCTDecode /Length ${img.bytes.size} >>\nstream\n")
                out.write(img.bytes)
                out.write("\nendstream")
            }
        }
        pages.forEachIndexed { i, page ->
            val pageObj = firstPage + i * 2
            obj(pageObj) {
                val xo = if (page.images.isEmpty()) "" else " /XObject << " + page.images.joinToString(" ") { "/Im$it ${firstImage + it} 0 R" } + " >>"
                out.write(
                    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${n(pageWidth)} ${n(pageHeight)}] " +
                        "/Resources << /Font << /F1 3 0 R /F2 4 0 R /F3 5 0 R >>$xo >> /Contents ${pageObj + 1} 0 R >>",
                )
            }
            obj(pageObj + 1) {
                val bytes = page.content.toString().encodeToByteArray()
                out.write("<< /Length ${bytes.size} >>\nstream\n")
                out.write(bytes)
                out.write("\nendstream")
            }
        }

        val xref = out.size
        out.write("xref\n0 ${total + 1}\n")
        out.write("0000000000 65535 f \n")
        for (o in offsets) out.write(o.toString().padStart(10, '0') + " 00000 n \n")
        out.write("trailer\n<< /Size ${total + 1} /Root 1 0 R /Info 6 0 R >>\nstartxref\n$xref\n%%EOF\n")
        return out.buf.copyOf(out.size)
    }

    companion object {
        private const val HEX = "0123456789ABCDEF"

        /** Maps a character to its WinAnsi (CP-1252) code; unsupported characters become `?`. */
        fun winAnsi(ch: Char): Int {
            val c = ch.code
            if (c in 32..126 || c in 0xA0..0xFF) return c
            return when (ch) {
                '“' -> 0x93; '”' -> 0x94; '‘' -> 0x91; '’' -> 0x92; '–' -> 0x96; '—' -> 0x97
                '…' -> 0x85; '•' -> 0x95; '€' -> 0x80; '™' -> 0x99; '\t' -> 32
                '→' -> 0x3E; '←' -> 0x3C; '✓' -> 0x2A; '≈' -> 0x7E; '½' -> 0xBD
                ' ', ' ', ' ' -> 32
                else -> 0x3F
            }
        }

        // Adobe Helvetica advance widths for codes 32..126, in 1/1000 em.
        private val REGULAR_WIDTHS = intArrayOf(
            278, 278, 355, 556, 556, 889, 667, 191, 333, 333, 389, 584, 278, 333, 278, 278,
            556, 556, 556, 556, 556, 556, 556, 556, 556, 556, 278, 278, 584, 584, 584, 556,
            1015, 667, 667, 722, 722, 667, 611, 778, 722, 278, 500, 667, 556, 833, 722, 778,
            667, 778, 722, 667, 611, 722, 667, 944, 667, 667, 611, 278, 278, 278, 469, 556,
            333, 556, 556, 500, 556, 556, 278, 556, 556, 222, 222, 500, 222, 833, 556, 556,
            556, 556, 333, 500, 278, 556, 500, 722, 500, 500, 500, 334, 260, 334, 584,
        )
        private val BOLD_WIDTHS = intArrayOf(
            278, 333, 474, 556, 556, 889, 722, 238, 333, 333, 389, 584, 278, 333, 278, 278,
            556, 556, 556, 556, 556, 556, 556, 556, 556, 556, 333, 333, 584, 584, 584, 611,
            975, 722, 722, 722, 722, 667, 611, 778, 722, 278, 556, 722, 611, 833, 722, 778,
            667, 778, 722, 667, 611, 722, 667, 944, 667, 667, 611, 333, 278, 333, 584, 556,
            333, 556, 611, 556, 611, 556, 333, 611, 611, 278, 278, 556, 278, 889, 611, 611,
            611, 611, 389, 556, 333, 611, 556, 778, 556, 556, 500, 389, 280, 389, 584,
        )
    }
}
