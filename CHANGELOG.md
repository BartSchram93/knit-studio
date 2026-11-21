# Changelog

## Unreleased

### Added
- **Wide charts**: shown as panels of stitches (1–20, 21–40, …) under each other, each with its own
  row and stitch numbers — in Knit mode around the current row (with a "Whole chart" switch) and in
  the chart editor (*Show in panels*, tap to draw). Charts can be up to 500 stitches wide. Knit mode
  shows a chart row written out, with repeated runs grouped ("(k4, k2tog, yo) 15 times").
- **Rename stitches**: built-in stitches can get your own name and abbreviation (Tools › Stitch
  library › a stitch › Rename). The original spellings keep being understood; written rows,
  knitting mode and PDFs use yours. *Use the original name* undoes it.
- PDF reading: written-out slip decreases ("sl1, k2tog, psso"), 2/1/2 and 1/2 cables, stitches
  between asterisks worked once, "turn and cast on", edge stitches given once for all chart rows
  ("RS rows: …, work chart, …"), headers split over two lines, "Chart 1" parts, and clearer notes
  for rows continued in another chart.
- Lock screen: shows the real instruction (charts written out), RS/WS and today's knitting.
- New stitches: the stitch count is set as **Change in stitch count** (−1 decrease, +1 increase) and
  **Worked over** (stitches used), instead of "stitches used" and "stitches made".
- **Rows from a PDF**: *Read rows from PDF* (PDF view menu, the Rows tab and Knit mode's PDF link)
  reads the PDF's text on the phone (PDFKit on iPhone, PdfBox-Android on Android) and finds rows
  and rounds ("Row 1 (RS): …", "Rnd 3: …", "Next row: …", "Set-up row", "Rows 2, 3, 4: …",
  "as Row 2", "Row 2 and all WS rows"), repeat sentences ("Repeat Rows 1 to 4 a total of 4 times",
  "Repeat these 4 rows 15 times"), parts, cast-on counts, sizes and marker set-ups. A review
  screen lists everything found plus what to check, and adds the chosen parts as written
  sections. Rows the app does not understand are tracked but marked "not checked"; count
  differences in imported rows are warnings. Scanned PDFs without text are explained. Rule-based:
  no AI, nothing leaves the phone.
- **Markers**: "knit to marker", "to 2 sts before marker", "sm", "pm", "rm" and named markers
  ("slip M2") are tracked from row to row (mirrored on flat rows) and checked. Sections have a
  "markers after cast-on" setting ("2, 2, 2").
- Parser: "rib to end", "work the stitches as they appear", "knit the knits and purl the purls",
  "until N sts remain", "bind off all sts knitwise", M3in1, 2/2 purl crosses (T4F/T4B), "sl1 st wyf".
- **Lock-screen knitting**: while Knit mode is open, the current row is shown on the lock screen
  with **Back** and **Next row** — an ongoing, silent notification on Android (asks for the
  notification permission once on Android 13+), a Live Activity on iPhone (iOS 17+, also in the
  Dynamic Island). Settings › *Current row on the lock screen* turns it off. On iPhone this adds
  one small app extension, which uses one more of AltStore's free App IDs.
- **Knitting stats**: active knitting time is measured automatically in knitting mode and the PDF
  viewer (pausing after 20 minutes without touching the screen), and rows are logged per day.
  Each pattern shows its totals, rows per knitting day, rows per hour, streak and a 14-day chart;
  Tools › Knitting stats shows the same across all patterns and per pattern.
- **PDF patterns**: add a PDF (New › PDF pattern, or to an existing pattern) and read it in the
  app with the phone's own PDF viewer (PDFKit on iPhone, PdfRenderer on Android), with a saved row
  counter underneath. PDFs are stored as files in the app's private storage (not the database,
  which cannot hold large files on Android) and travel with .knit files and backups. Choosing a
  PDF under "Import" now creates a PDF pattern.
- **AltStore source**: every build on `main` publishes `altstore-source.json` with the "latest"
  pre-release. Added once in AltStore › Sources, new versions appear as one-tap updates.
- Needles have a **Brand** field. The stash groups needles into foldable folders per brand, with
  a by-size view, quick brand choice and "Add <brand> needle" that picks the next free size.
  Brands typed into the notes earlier can be moved to the new field in one step (only after
  confirming; nothing changes on its own).
- **Photo of the yarn** in the stash, shown instead of the colour dot in the list. Photos are
  included in backups and removed together with the yarn.
- The export sheet separates **PDF to print** from **Knit Studio file** and says which one can be imported again.
- Clear import messages: picking a PDF, zip, photo or a damaged file now says what the file is,
  with its name. The import button is labelled "Import a Knit Studio file". Files with a
  byte-order mark or trailing NUL bytes are accepted.
- **Cables for interchangeable needles**: cables are stash items of their own (length, connector,
  material, number), shown inside the brand folder next to the tips, with "Add <brand> cable".
  For interchangeable needles the length field is now labelled **Tip length**. Cables are
  included in backups; older backups restore unchanged.
- Wrong-side rows of flat charts get a faint tint on screen and in PDFs (Settings › Appearance,
  and an option in PDF export), with an entry in the PDF stitch key.
- **skpo** (slip 1, knit 1, pass over) is its own stitch with its own instructions. It was
  previously treated as another name for ssk. Same stitch count and chart symbol; rows already
  written as "skpo" keep their wording.
- iPhone installation with AltStore Classic and a free Apple ID: `IOS_ALTSTORE_SETUP.md`.
- `scripts/build-ios-ipa.sh`: builds the unsigned IPA and checks bundle ID, version, architecture,
  app name, extensions, provisioning and entitlements.
- CI publishes a rolling **latest** pre-release with `KnitStudio.ipa` and `KnitStudio.apk` at fixed
  download links; the Release workflow now attaches the IPA too.
- Settings › About shows the installed version and build number.
- Simulator test for the iOS database location.

### Changed
- iPhone builds are versioned `major.minor.<build>` (e.g. 1.0.57) so AltStore can recognise newer
  builds. Android is unchanged.
- The "latest" pre-release is updated in place instead of deleted and recreated, so its links
  never disappear.
- iOS bundle identifier fixed permanently to `studio.knit.app` (was a placeholder). The build
  refuses to run if it changes.
- iOS database directory pinned explicitly to `Library/Application Support/databases` (the
  previous default, so nothing moves).
- CI: build steps shared between CI and Release (`build.yml`); the redundant simulator app build
  was dropped to save macOS minutes; superseded pull-request runs are cancelled.

## 1.0.0 — 2026-11-14

First complete version.

### Added
- Pattern library with categories, favourites, drafts, archive, collections, search and sorting.
- Chart editor: draw / erase / select / move tools, pinch zoom, undo and redo, fill, copy, cut,
  paste, mirror and flip, repeat boxes, colourwork palette, multi-cell cables, resize from any
  edge, flat or round, configurable first-row side, reading direction and row numbering.
- Stitch library with 37 built-in stitches and user-defined stitches with their own symbols.
- Written row editor with live parsing and stitch arithmetic, star and bracket repeats,
  open-ended instructions, row repeats, notes, multi-size counts and a quick-insert bar.
- Chart → rows and rows → chart conversion with explicit notes for anything unsupported.
- Validation engine (errors, warnings, suggestions) with navigation to the row or cell.
- Knitting mode: row tracking per section or chart, highlighted chart row, repeat tracking,
  counters (free, cyclic, target, linked), dated notes, resume.
- Tools: gauge, cast-on, yarn amount (two methods), needle sizes, yarn and needle stash.
- Templates sized from gauge: ribbed scarf, beanie, diagonal cloth; charted lace, cable and
  colourwork starters.
- PDF export with vector charts; A4 / US Letter, portrait / landscape, selectable sections.
- `.knit` pattern files and `.knitbackup` full backups with validation and repair on import.
- Metric / imperial units, US / UK terminology, light / dark / system appearance.
- Android app (APK); iOS app sources and Xcode project.
- 155 automated tests.
