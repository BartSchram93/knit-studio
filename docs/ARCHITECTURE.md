# Architecture

## Modules

```
┌────────────┐   ┌──────────────────────┐
│ androidApp │   │ iosApp (Xcode/Swift) │      thin platform shells
└─────┬──────┘   └──────────┬───────────┘
      └─────────┬───────────┘
        ┌───────▼────────┐
        │   composeApp   │   shared UI (Compose Multiplatform) + platform bridges
        └───────┬────────┘
        ┌───────▼────────┐
        │     shared     │   knitting engine, model, database, files, PDF — no UI imports
        └────────────────┘
```

Targets: Android, iOS (arm64 device, arm64 simulator) and a JVM target. The JVM target is not a
product: it runs the engine tests in seconds and renders the real UI headlessly for UI tests and
screenshots.

AGP 9 does not allow an Android application module to also be a Kotlin Multiplatform module,
hence the separate `androidApp`. `shared` and `composeApp` use
`com.android.kotlin.multiplatform.library`.

## `shared` — packages

| Package | Responsibility |
|---|---|
| `model` | Immutable, serialisable data classes: `Pattern`, `Chart`, `Cell`, `ChartRepeat`, `Section`, `Row`, `StitchDef`, `Yarn`, `Needle`, `Gauge`, `Measurement`, `Progress`, `Counter` |
| `stitch` | Built-in stitch library; `Glyphs`: chart symbols as resolution-independent primitives |
| `chart` | Pure chart operations: reading order, placement of wide stitches, fill, copy/paste, insert/delete, resize, mirror, repeats |
| `written` | `RowParser` (text → tree), `RowEvaluator` (stitch arithmetic), `RowPrinter` (tree → text), `evaluateSection` |
| `convert` | Chart → rows and rows → chart, each returning `ConversionNote`s for what could not be expressed |
| `validate` | `Validator`: located errors, warnings and suggestions |
| `calc` | Gauge, cast-on, yarn, needle sizes, unit formatting |
| `companion` | Knitting progress: tracks, positions, counters |
| `template` | Patterns computed from gauge and measurements |
| `io` | Versioned `.knit` / `.knitbackup` formats, import repair |
| `pdf` | `PdfWriter` (PDF 1.4 primitives) and `PatternPdf` (document layout) |
| `data` | SQLDelight database, `KnitRepository`, settings |

### The core idea: stitches have arithmetic

Every `StitchDef` states how many stitches it **consumes** and **produces**, how many chart cells
it is **wide**, and which stitch is actually worked when its symbol appears on a **wrong-side**
row. From those four facts follow stitch counts, validation, chart → text, text → chart and yarn
estimates. No feature treats a pattern as plain text.

### Charts

Cells are stored exactly as drawn — bottom row first, left to right — never in knitting order.
Knitting order is derived (`readingOrder`) from the construction (flat/round), the side of the
first row and the right-side reading direction. A stitch wider than one cell (a cable) stores its
id in every cell it covers with a `part` index, so it moves, erases and validates as a unit.

### Written rows

The text the knitter typed is the source of truth and is stored verbatim. The parser turns it
into a small tree (`Stitch`, `Group`, `Color`, `Unknown`) on demand; the evaluator then works
through the row as a knitter would, tracking stitches left on the needle. Unknown text is kept
and reported, never dropped.

### Never silently wrong

- Conversion functions return notes alongside results; the UI shows them.
- A written section generated from a chart carries `generated = true` and follows the chart until
  the user edits a row; after that, differences are reported by the validator instead of being
  overwritten.
- The validator only reports. Repairs happen in exactly two explicit places: file import
  (structural damage only) and Settings › Check data › Repair.

## Persistence

SQLDelight, schema version 2 (`shared/src/commonMain/sqldelight`). A pattern is stored as one
JSON document — the same serialisation as a `.knit` file — plus the columns needed for ordering.
This keeps the SQL schema stable while the model evolves; model changes are handled by the JSON
format version. `1.sqm` migrates version 1 databases (tested). Images are stored as BLOBs so no
file-system code is needed on either platform.

`KnitRepository` is the only entry point to storage and exposes `Flow`s. `PatternEditor`
(composeApp) holds the pattern being edited in memory, applies edits immediately and saves 350 ms
after the last change, and again when the app goes to the background.

## File formats

See [FILE_FORMAT.md](FILE_FORMAT.md). Both platforms run the same encoder and decoder, and
re-exporting an imported file is byte-identical (tested), which is what makes Android ⇄ iOS
transfer lossless.

## PDF

`PdfWriter` is a ~300-line PDF 1.4 writer: pages, vector paths, the built-in Helvetica family
with exact glyph widths for wrapping, WinAnsi text, and JPEG embedding. `PatternPdf` lays out the
document. Charts are drawn from the same `Glyphs` primitives the screen uses, so the print-out
matches the editor and stays sharp at any size. No platform PDF APIs are involved.

## `composeApp`

| Package | Contents |
|---|---|
| `theme`, `components` | Design tokens, the icon set, pages with collapsing large titles, grouped lists, sheets, fields |
| `app` | `AppContainer` (manual dependency injection), `PatternEditor`, navigation |
| `home`, `library` | Home, pattern library, create sheet |
| `pattern` | Pattern hub and its five tabs |
| `chart` | `ChartRenderer` (shared by editor, thumbnails and knitting mode), `ChartEditorState`, editor screen and sheets |
| `written` | Section editor |
| `companion` | Knitting mode |
| `tools`, `settings`, `export` | Calculators, stitch library, stash, settings, export sheet |
| `platform` | `expect`/`actual` bridges: share, save, pick file, pick photo, keep screen on, decode image |

State is plain Compose state plus `StateFlow`s from the container. There is no DI framework and
no ViewModel layer: the app has one long-lived container and screen-local state, which keeps the
code small.

Chart editor gestures are handled by one `pointerInput` block: one finger uses the current tool,
a second finger always switches to pan-and-zoom, and a stationary press opens the cell menu. A
stroke edits a preview chart and is committed as a single undo step.

## Testing

| Suite | Runs on | Covers |
|---|---|---|
| `shared` commonTest | JVM (and iOS simulator in CI) | parser, evaluator, charts, conversion, validator, calculators, files, templates, companion |
| `shared` jvmTest | JVM | repository, schema migration, two-device export/import, PDF structure |
| `composeApp` jvmTest | JVM, headless | end-to-end workflows through the real UI; screenshots |
| `androidApp` test | Robolectric | real Application/Activity start-up, Android SQLite driver |

## Decisions

| Decision | Why |
|---|---|
| JSON documents in SQLite rather than normalised tables | One serialisation for database and files; schema stays stable |
| Own PDF writer | Identical output on both platforms, vector charts, no dependency |
| Own icon set as vector paths | Same look on both platforms, no icon font |
| Text as source of truth for rows | Knitters think in pattern text; nothing they typed is reformatted without asking |
| No ViewModels / DI framework | One container suffices; fewer moving parts |
| JVM target for tests | Seconds instead of minutes; makes UI tests possible without devices |
