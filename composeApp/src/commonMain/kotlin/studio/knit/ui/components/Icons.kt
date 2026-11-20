package studio.knit.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The app's own icon set: light line icons on a 24-unit grid, drawn once and used on both
 * platforms so the product looks the same everywhere without bundling an icon font.
 */
object Icons {
    private fun stroke(name: String, vararg paths: String, width: Float = 1.8f): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            for (p in paths) addPath(
                pathData = addPathNodes(p), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            )
        }.build()

    private fun solid(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            for (p in paths) addPath(pathData = addPathNodes(p), fill = SolidColor(Color.Black))
        }.build()

    private const val CIRCLE9 = "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z"

    val Home = stroke("home", "M4 11L12 4l8 7v9h-5.5v-5.5h-5V20H4z")
    val Library = stroke("library", "M4 4h7v7H4z", "M13 4h7v7h-7z", "M4 13h7v7H4z", "M13 13h7v7h-7z")
    val Tools = stroke("tools", "M4 7h8", "M16 7h4", "M4 17h4", "M12 17h8", "M14 5a2 2 0 1 0 0 4a2 2 0 1 0 0-4z", "M10 15a2 2 0 1 0 0 4a2 2 0 1 0 0-4z")
    val Settings = stroke(
        "settings", "M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6z",
        "M12 3v2.5", "M12 18.5V21", "M3 12h2.5", "M18.5 12H21", "M5.6 5.6l1.8 1.8", "M16.6 16.6l1.8 1.8", "M5.6 18.4l1.8-1.8", "M16.6 7.4l1.8-1.8",
    )
    val Plus = stroke("plus", "M12 5v14", "M5 12h14", width = 2f)
    val Minus = stroke("minus", "M5 12h14", width = 2f)
    val ChevronRight = stroke("chevron-right", "M9 5l7 7-7 7", width = 2f)
    val ChevronLeft = stroke("chevron-left", "M15 5l-7 7 7 7", width = 2.2f)
    val ChevronDown = stroke("chevron-down", "M5 9l7 7 7-7", width = 2f)
    val Close = stroke("close", "M6 6l12 12", "M18 6L6 18", width = 2f)
    val Check = stroke("check", "M5 12.5l4.5 4.5L19 7.5", width = 2.2f)
    val Search = stroke("search", "M11 5a6 6 0 1 0 0 12a6 6 0 1 0 0-12z", "M15.5 15.5L20 20")
    private const val STAR = "M12 3.5l2.6 5.4 5.9.8-4.3 4.1 1 5.9L12 16.9l-5.2 2.8 1-5.9-4.3-4.1 5.9-.8z"
    val Star = stroke("star", STAR)
    val StarFilled = solid("star-filled", STAR)
    val Trash = stroke("trash", "M5 7h14", "M9 7V5h6v2", "M7 7l1 13h8l1-13", "M10 11v5", "M14 11v5")
    val Copy = stroke("copy", "M9 9h10v11H9z", "M5 15V4h10")
    val Undo = stroke("undo", "M9 7L5 11l4 4", "M5 11h9a5 5 0 0 1 0 10h-3", width = 2f)
    val Redo = stroke("redo", "M15 7l4 4-4 4", "M19 11h-9a5 5 0 0 0 0 10h3", width = 2f)
    val Pencil = stroke("pencil", "M4 20l1-4L16 5l3 3L8 19z", "M14 7l3 3")
    val Eraser = stroke("eraser", "M5 15l8-8 6 6-6 6H9z", "M10 10l6 6", "M5 20h14")
    val Select = stroke("select", "M4 7V4h3", "M10.5 4h3", "M17 4h3v3", "M20 10.5v3", "M20 17v3h-3", "M13.5 20h-3", "M7 20H4v-3", "M4 13.5v-3")
    val Move = stroke("move", "M12 3v18", "M3 12h18", "M9 6l3-3 3 3", "M9 18l3 3 3-3", "M6 9l-3 3 3 3", "M18 9l3 3-3 3")
    val Grid = stroke("grid", "M4 4h16v16H4z", "M4 9.33h16", "M4 14.67h16", "M9.33 4v16", "M14.67 4v16", width = 1.6f)
    val Rows = stroke("rows", "M4 6h16", "M4 12h16", "M4 18h10")
    val More = solid(
        "more", "M6 10.5a1.5 1.5 0 1 0 0 3a1.5 1.5 0 1 0 0-3z", "M12 10.5a1.5 1.5 0 1 0 0 3a1.5 1.5 0 1 0 0-3z",
        "M18 10.5a1.5 1.5 0 1 0 0 3a1.5 1.5 0 1 0 0-3z",
    )
    val Play = solid("play", "M8 5l11 7-11 7z")
    val Warning = stroke("warning", "M12 4l9 16H3z", "M12 10v4.5", "M12 17.3v.2")
    val Info = stroke("info", CIRCLE9, "M12 11v6", "M12 7.5v.2")
    val Error = stroke("error", CIRCLE9, "M12 7v6", "M12 16.3v.2")
    val CheckCircle = stroke("check-circle", CIRCLE9, "M8 12.5l3 3 5-6")
    val Share = stroke("share", "M12 3v12", "M8 7l4-4 4 4", "M7 11H5v9h14v-9h-2")
    val Archive = stroke("archive", "M4 5h16v4H4z", "M6 9v10h12V9", "M10 13h4")
    val Palette = stroke("palette", "M12 3.5c3 3.5 6 6.8 6 10a6 6 0 0 1-12 0c0-3.2 3-6.5 6-10z")
    val Ruler = stroke("ruler", "M3 15L15 3l6 6L9 21z", "M7 13l2 2", "M10 10l2 2", "M13 7l2 2")
    val Calculator = stroke("calculator", "M6 3h12v18H6z", "M9 7h6", "M9 11.5h.2", "M12 11.5h.2", "M15 11.5h.2", "M9 15h.2", "M12 15h.2", "M15 15h.2", "M9 18h.2", "M12 18h3.2")
    val Yarn = stroke("yarn", "M12 4a8 8 0 1 0 0 16a8 8 0 1 0 0-16z", "M6 8c4 1 8 5 9 11", "M4.5 13c4 0 7 2 9 6.5", "M9 4.6c5 1 9 5 10 10")
    /** A coiled cable joining two connectors. */
    val Cable = stroke("cable", "M5 19h3", "M16 19h3", "M8 19c0-5 2-8 4-8s4 3 4 8", "M12 11V6", "M10 6h4")
    val Needle = stroke("needle", "M4 20L18.5 5.5", "M17 4l3 3", "M9 20L20 9")
    val Filter = stroke("filter", "M4 6h16", "M7 12h10", "M10 18h4", width = 2f)
    val Sort = stroke("sort", "M7 5v14", "M4 16l3 3 3-3", "M17 19V5", "M14 8l3-3 3 3")
    val Folder = stroke("folder", "M3 7V5h6l2 2h10v12H3z")
    val Import = stroke("import", "M12 4v11", "M8 11l4 4 4-4", "M5 19h14")
    val Document = stroke("document", "M7 3h7l4 4v14H7z", "M14 3v4h4", "M10 12h5", "M10 16h5")
    val Repeat = stroke("repeat", "M5 10V8a2 2 0 0 1 2-2h11", "M15 3l3 3-3 3", "M19 14v2a2 2 0 0 1-2 2H6", "M9 21l-3-3 3-3")
    val FlipH = stroke("flip-h", "M12 3v18", "M9 7l-5 5 5 5z", "M15 7l5 5-5 5z")
    val FlipV = stroke("flip-v", "M3 12h18", "M7 9l5-5 5 5z", "M7 15l5 5 5-5z")
    val Fit = stroke("fit", "M4 9V4h5", "M20 9V4h-5", "M4 15v5h5", "M20 15v5h-5")
    val Photo = stroke("photo", "M4 5h16v14H4z", "M4 16l5-5 4 4 3-3 4 4", "M15 8.5a1.5 1.5 0 1 0 0 3a1.5 1.5 0 1 0 0-3z")
    val Note = stroke("note", "M5 4h14v12l-4 4H5z", "M15 20v-4h4")
    val Counter = stroke("counter", "M8 4v16", "M16 4v16", "M4 9h16", "M4 15h16")
    val Book = stroke("book", "M12 6c-2-1.5-5-2-8-1.5v13c3-.5 6 0 8 1.5 2-1.5 5-2 8-1.5v-13c-3-.5-6 0-8 1.5z", "M12 6v13")
    val Sparkle = stroke("sparkle", "M12 4l1.8 5.2L19 11l-5.2 1.8L12 18l-1.8-5.2L5 11l5.2-1.8z")
    val Paste = stroke("paste", "M8 5H6v15h12V5h-2", "M9 3h6v4H9z")
    val Scissors = stroke("scissors", "M6 5a2 2 0 1 0 0 4a2 2 0 1 0 0-4z", "M6 15a2 2 0 1 0 0 4a2 2 0 1 0 0-4z", "M8 8l12 9", "M8 16l12-9")
    val Fill = stroke("fill", "M5 12l7-7 7 7-7 7z", "M5 12h14")
    val Layers = stroke("layers", "M12 4l9 5-9 5-9-5z", "M3 14l9 5 9-5")
}
