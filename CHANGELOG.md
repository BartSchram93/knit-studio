# Changelog

## Unreleased

### Added
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
