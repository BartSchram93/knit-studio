# Knit Studio

A personal knitting pattern studio for Android and iPhone: design a pattern, check that it adds
up, export it as a PDF, and knit from it — offline, with no account.

One Kotlin Multiplatform codebase builds both apps.

| Home | Chart editor | Written rows | Knitting |
|---|---|---|---|
| ![Home](docs/screenshots/home.png) | ![Chart editor](docs/screenshots/chart-editor.png) | ![Written rows](docs/screenshots/written-rows.png) | ![Knitting](docs/screenshots/knitting.png) |

*(Screenshots are rendered from the shared UI by the automated tests.)*

## What it does

- **Chart editor** built for touch: draw, erase, select, fill, copy/paste, mirror, undo/redo,
  pinch to zoom, repeat boxes, colourwork, cables that behave as one stitch, flat or in the round,
  right- or wrong-side first row, either reading direction.
- **Written rows** that are parsed as you type — `K2, *p2, k2; rep from * to end`,
  `(k2tog, yo) 3 times`, `knit to last 3 sts`, sized counts like `k10 (12, 14)` — with the stitch
  count shown beside every row.
- **Chart ⇄ rows**: write the rows from a chart, or draw the chart from rows. Anything that cannot
  be converted faithfully is reported, never guessed.
- **Check**: a validation engine that finds stitch-count mismatches, repeats that do not fit,
  broken cables, size lists of the wrong length and rows that disagree with their chart — and
  takes you to the exact row or cell.
- **Knitting mode**: one big button per row, the chart row highlighted, repeat tracking, extra
  counters, notes, and your place remembered.
- **Tools**: gauge from a swatch and comparison with the pattern, cast-on for a stitch multiple,
  yarn estimates, needle size table, a stitch library you can extend, a yarn and needle stash.
- **PDF export** with vector charts, stitch key, abbreviations and materials, on A4 or US Letter.
- **`.knit` files and full backups** that move between Android and iOS without loss.

## Status

| | |
|---|---|
| Android | Debug and release APKs build and are installable. App start-up and database verified under Robolectric. |
| iOS | CI builds an unsigned IPA for AltStore and checks its structure; shared tests (including on-device storage) pass on the iOS simulator. **Not yet installed on an iPhone** — see [Install on iPhone](#install-on-iphone). |
| Tests | 155 automated tests: knitting engine, persistence and migration, file format, PDF, UI workflows, Android runtime. |

Details, known issues and the roadmap live in [docs/PROJECT.md](docs/PROJECT.md).

## Install on iPhone

Knit Studio installs on your own iPhone with **AltStore Classic** and a **free Apple ID**, using a
Windows or Mac computer. No paid developer account and no App Store are needed.

1. Set up AltServer on the PC and AltStore on the iPhone (one time).
2. Download the newest build: **[KnitStudio.ipa](https://github.com/BartSchram93/knit-studio/releases/download/latest/KnitStudio.ipa)**
   ([release page](https://github.com/BartSchram93/knit-studio/releases/tag/latest)). It is
   rebuilt on every push to `main`.
3. AltStore › *My Apps* › **+** › choose the file. To update later, do the same with a newer
   file. It replaces the app and keeps your patterns.
4. AltStore re-signs the app automatically before the 7-day limit, whenever AltServer is reachable.

Full step-by-step guide, data-safety rules and troubleshooting: **[IOS_ALTSTORE_SETUP.md](IOS_ALTSTORE_SETUP.md)**.
Installation through AltStore has not yet been tested on a real iPhone.

## Install on Android

Download **[KnitStudio.apk](https://github.com/BartSchram93/knit-studio/releases/download/latest/KnitStudio.apk)**
and open it on the phone — see [docs/INSTALL_ANDROID.md](docs/INSTALL_ANDROID.md).

## Build

Requirements: JDK 17 or newer (21 recommended). Android SDK with platform 37 for the Android app;
macOS with Xcode 16+ for the iOS app. Gradle is provided by the wrapper.

```bash
git clone https://github.com/BartSchram93/knit-studio.git
```

```bash
cd knit-studio
```

Run all tests:

```bash
./gradlew :shared:jvmTest :composeApp:jvmTest :androidApp:testDebugUnitTest
```

Build the Android APKs (output in `androidApp/build/outputs/apk/`):

```bash
./gradlew :androidApp:assembleDebug :androidApp:assembleRelease
```

- Android installation: [docs/INSTALL_ANDROID.md](docs/INSTALL_ANDROID.md)
- iPhone with AltStore: [IOS_ALTSTORE_SETUP.md](IOS_ALTSTORE_SETUP.md) · building from Xcode: [docs/INSTALL_IOS.md](docs/INSTALL_IOS.md)

## Repository layout

```
shared/        Knitting engine, data model, database, file format, PDF writer (no UI code)
composeApp/    Shared Compose Multiplatform interface + platform bridges
androidApp/    Android application shell (manifest, activity, icon)
iosApp/        Xcode project and Swift entry point
docs/          Research, product requirements, architecture, install guides, project log
design/        Design system notes
.github/       CI: tests, APKs, unsigned iOS IPA, "latest" pre-release, manual Release workflow
scripts/       build-ios-ipa.sh — builds and checks the IPA for AltStore
```

## Documentation

- [Research](docs/RESEARCH.md) · [Product requirements](docs/PRODUCT.md) · [Architecture](docs/ARCHITECTURE.md)
- [File format](docs/FILE_FORMAT.md) · [Design system](design/DESIGN.md)
- [Project log, test results, known issues](docs/PROJECT.md) · [Changelog](CHANGELOG.md) · [Contributing](CONTRIBUTING.md)

Knit Studio is a private, personal project. It contains no network code, analytics or secrets.
