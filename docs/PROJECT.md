# Knit Studio — project log

The living record of the project: what is done, how it was verified, what is not done.

## Checklist

### Stage 1 — Research and foundation
- [x] Competitor and conventions research (`RESEARCH.md`), product requirements (`PRODUCT.md`)
- [x] Architecture (`ARCHITECTURE.md`), repository structure, Gradle build on current stable versions
- [x] Design system, navigation, data model, persistence, home screen, create/save patterns
- [x] GitHub repository (private) created and pushed: https://github.com/BartSchram93/knit-studio

### Stage 2 — Core knitting functionality
- [x] Stitch library (37 built-in stitches, custom stitches)
- [x] Chart editor and renderer
- [x] Written pattern editor, rows and rounds, row repeats
- [x] Chart ⇄ written conversion
- [x] Validation engine

### Stage 3 — Knitting tools
- [x] Gauge, cast-on and yarn calculators, needle table
- [x] Yarn and needle records, stash
- [x] Measurements and multi-size patterns
- [x] Knitting companion

### Stage 4 — Export and interoperability
- [x] PDF export with vector charts
- [x] `.knit` export/import, `.knitbackup` backup/restore
- [x] Cross-device round-trip tests

### Stage 5 — iOS
- [x] All iOS Kotlin sources compile (`iosArm64`, `iosSimulatorArm64`), including UIKit / PhotosUI interop
- [x] Xcode project, Swift entry point, app icon, CI job
- [ ] **Xcode build, on-device test** — needs macOS; not done

### Stage 6 — Refinement
- [x] Light/dark themes, haptics, screen-transition and row-change animation
- [x] Accessibility labels, headings, live regions
- [x] Error handling for import, export, backup, damaged data
- [ ] On-device performance profiling and screen-reader pass — not possible in the build environment

## Test results (2026-11-14)

| Suite | Tests | Result |
|---|---|---|
| `:shared:jvmTest` — engine, conversion, validator, calculators, files, templates, companion, repository, migration, PDF | 139 | pass |
| `:composeApp:jvmTest` — UI workflows and screenshot tour (headless) | 14 | pass |
| `:androidApp:testDebugUnitTest` — Android Application/Activity start, Android SQLite driver (Robolectric) | 2 | pass |
| iOS simulator tests (`:shared:iosSimulatorArm64Test`) | — | configured in CI, **never run** |

Other verification performed:
- Debug and release APKs built; release APK inspected (`aapt2`: package `studio.knit.app`,
  version 1.0.0, no permissions requested) and signature verified (`apksigner`).
- Generated PDFs opened with an independent reader (`pypdf`) and rendered to images
  (`pdftoppm`) to check layout by eye.
- Screenshots of every main screen in light and dark reviewed by eye.

## What has not been verified

Be aware of these before relying on the app:

1. **No run on a physical Android device or emulator.** The build machine has no hardware
   virtualisation. Start-up and storage were exercised under Robolectric and the whole UI under
   the desktop renderer, but real-device behaviour — touch feel in the chart editor, the system
   share sheet, the file and photo pickers, keyboard insets, performance on large charts — is
   untested.
2. **The iOS app has never been linked or launched.** The Kotlin compiles; the Xcode project,
   `Info.plist` and Swift files were written by hand without Xcode. Expect to spend a little time
   on the first build (see `INSTALL_IOS.md`).
3. **The CI workflows had not completed a run when this log was written.** Check the Actions tab; the iOS job in particular is the first real build of the Xcode project.
4. Cross-platform file compatibility is tested between two databases on the JVM and on the
   Android driver. Both platforms run the same shared encoder, so the result should hold on iOS,
   but an actual phone-to-phone transfer has not been done.

## Known issues and limitations

- PDF text uses the built-in Helvetica (WinAnsi): Western European characters print correctly;
  other scripts (Cyrillic, Greek, CJK) are replaced by `?` in PDFs. The app itself displays them.
- PDFs embed JPEG photos only (what the photo pickers produce).
- Rows → chart draws shaping with "no stitch" padding at the left edge; the placement is a
  starting point and is reported as such.
- Chart → rows expresses one repeat box per row; further boxes are written out once (reported).
- Short rows (`turn`, `w&t`) are not counted; they are flagged rather than guessed.
- Charts show every row; "right-side rows only" charts are not supported.
- Wrong-side equivalents are not defined for cables and some increases; such rows keep their
  right-side wording and are flagged.
- The written editor has no undo of its own beyond the keyboard's.
- Renaming a chart in Chart settings adds an undo step per keystroke.
- A chart is limited to 200 × 200 cells and 12 colours.
- The interface is English only; knitting terms switch between US and UK.
- Android release builds are signed with the debug key and are not minified.
- No PDF import, fabric simulation, garment grading or AI assistance (see roadmap).

## Environment limits encountered

| Limit | Effect | What is needed |
|---|---|---|
| The build session may push branches but not tags or releases | `v1.0.0` exists only as a local tag; there is no GitHub release yet | Run the **Release** workflow from the Actions tab (creates tag `v1.0.0` and a release with both APKs) |
| Linux build machine, no macOS/Xcode | iOS framework not linked, app not built or signed, no IPA | Build on a Mac, or let the CI macOS job do it after the first push |
| No hardware virtualisation | No Android emulator | Install the APK on a phone |

## Roadmap

1. First real-device pass on both phones; fix what touch testing reveals.
2. Chart editor: move a selection by dragging, eyedropper tool, row/column headers that select.
3. Undo in the written editor; drag to reorder rows.
4. "Right-side rows only" charts; short rows.
5. Fabric preview for colourwork and texture.
6. Open `.knit` files directly from the Files app (document types / intent filters).
7. Embedded fonts in PDFs for non-Latin text.
8. Optional: PDF import, garment grading, AI-assisted drafting behind the existing validator.

## Releases

| Version | Date | Notes |
|---|---|---|
| 1.0.0 | 2026-11-14 | First complete version. Publish with the Release workflow. |
