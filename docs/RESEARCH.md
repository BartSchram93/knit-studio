# Knit Studio — Research

Phase-one research for a personal knitting pattern studio. Sources are listed at the end.
Where a statement comes from general knitting practice rather than a page I could cite, it is
marked *(domain knowledge)*. Where web research came back thin, that is said plainly rather
than papered over.

## 1. Competitor analysis

| App | What it is | Strengths worth adopting | Weaknesses / complaints |
|---|---|---|---|
| **knitCompanion** | PDF pattern *follower* (iOS/Android). Row tracking and highlighting over an imported PDF; paid tiers add "magic markers" (colour-coding particular stitches), counters, joining chart parts. | Current-row highlight over the chart; linked counters; works fully offline. | Powerful "once you get the hang of it" — a steep learning curve is the recurring theme; Android rating notably lower than iOS (3.6 vs 4.8); you must prepare ("set up") each PDF before knitting; it does not create patterns. |
| **My Row Counter / Row Counter / Knit Row Counter / Stitch Keeper** | Counters first, pattern viewing second. | Big tap targets; secondary counters that follow a repeat and reset each cycle; "repeat N of M" display; notes; timers. | Ads and subscriptions are the top complaints across the category, followed by bugs after updates and privacy worries. Stitch Keeper's praise is telling: "simple, functional, no ads, no data collection". |
| **Knitting Genius / Pocket Knitting / Knit&Note** | Learning content + counters + project notebook. | Project notebook (yarn, needles, notes, photos) attached to the project. | Sync behind subscription; no real pattern authoring. *(Search turned up little verifiable detail on Pocket Knitting and Knitify; treated as the same category.)* |
| **Stitch Fiddle** | Browser chart editor (knit/crochet/cross-stitch). | Chart → written instructions with a choice of RS / WS / in-the-round; repeat boxes drawn directly on the chart; image → colour chart; progress tracking on a chart row. | Web app, awkward on phones; resizing image-derived charts loses detail; premium gates. |
| **Chart Minder** | Free browser chart editor. | Live preview of the knitted fabric next to the chart. | Private charts and basic tools (eyedropper, find/replace) are paid. |
| **Stitchmastery** | Desktop publishing-grade chart editor (~$95). | The model to follow for correctness: each stitch knows how many stitches it consumes and produces, so the tool compares what one row produces with what the next consumes; chart ⇄ text; typed instructions are parsed as you type with errors reported; glossary/key generated automatically; vector export. | Desktop only, dated UI, not a companion. Its own docs caution that warnings do not *certify* a pattern. |
| **Knitting Chart / misc. mobile chart apps** | Grid painters. | Quick colour painting. | Behave like spreadsheets or pixel editors: no stitch semantics, no validation, no written output. |

### What the market is missing

No mobile app combines **authoring** (Stitchmastery/Stitch Fiddle class) with **following**
(knitCompanion class). Authoring tools are desktop/web; mobile tools are counters. A knitter who
designs their own pattern today draws it in one tool, exports a PDF, imports the PDF into a second
tool and then manually "prepares" it for tracking — even though the first tool already knew every
row. Knit Studio keeps one structured pattern from design to last row, so the companion needs no
setup at all.

### Repeatedly requested / praised

1. No ads, no account, no subscription, works offline.
2. Counters that understand repeats ("repeat 3 of 6, row 2 of 4").
3. Highlighting the current chart row and dimming the rest.
4. Written instructions and charts for the same pattern (many knitters only read one of them).
5. Stitch counts at the end of rows, and being told when they are wrong.
6. Remembering exactly where you stopped.

## 2. Knitting standards and conventions

### Charts *(Craft Yarn Council conventions + domain knowledge)*

- A chart shows the fabric **as seen from the right side (RS)**. Row 1 is the bottom row.
- **Flat knitting:** RS rows are read right → left, WS rows left → right. A symbol on a WS row is
  worked as its counterpart (blank = knit on RS, purl on WS; `k2tog` on RS = `p2tog` on WS).
  Usually row 1 is RS, but patterns that start on a WS row exist, so the first-row side must be
  configurable.
- **In the round:** every round is an RS row, read right → left.
- Left-handed (mirror) knitters read the opposite way, so the RS reading direction must be
  configurable too.
- Stitch numbers run along the bottom, counted from the edge where row 1 starts.
- **Repeats** are shown as a bordered box; stitches outside it are edge stitches worked once.
- **"No stitch"** cells (grey) keep columns aligned when increases/decreases change the count.
- A chart's cell count is the stitch count *after* the row is worked.
- There is **no single authoritative symbol standard**; every pattern must carry its own key.
  Knit Studio therefore always generates the legend from the symbols actually used.

### Stitch semantics

Every stitch is described by how many stitches it **consumes** from the left needle and how many it
**produces** on the right needle:

| Stitch | Consumes → produces | | Stitch | Consumes → produces |
|---|---|---|---|---|
| k, p, ktbl, ptbl, sl | 1 → 1 | | yo, m1, m1l, m1r, cast on | 0 → 1 |
| k2tog, ssk, p2tog, ssp | 2 → 1 | | kfb, pfb | 1 → 2 |
| k3tog, sk2p, s2kp (cdd) | 3 → 1 | | bind off | 1 → 0 |
| 2/2 cable (c4f/c4b) | 4 → 4 | | no stitch | 0 → 0 |

A row is consistent when the stitches it consumes equal the stitches the previous row produced.
This single rule catches the large majority of real pattern errors and is the core of the
validation engine.

### Written instructions

- Rows are `Row 1 (RS): …`; rounds are `Rnd 1: …`.
- Repeats: `*k2, p2; rep from * to end`, `… to last 3 sts`, `(k2tog, yo) 3 times`,
  `[k1, p1] twice`.
- Open-ended instructions: `knit to end`, `p to last 2 sts`.
- Multi-size counts: `k10 (12, 14, 16)` — first value is the smallest size.
- Stitch multiples: "multiple of 4 sts + 2" describes repeat width plus edge stitches.
- Row repeats: `Rep rows 1–4 five more times`.

### US vs UK terminology

| US | UK |
|---|---|
| gauge | tension |
| bind off (BO) | cast off |
| stockinette stitch | stocking stitch |
| seed stitch | moss stitch (UK "moss" = US "seed"; US "moss" is the 4-row version) |
| yarn over (yo) | yarn forward / yarn over needle / yarn round needle (yfwd / yon / yrn) |
| skip | miss |
| worsted / bulky / fingering | aran / chunky / 4-ply |

Needle sizes: metric (mm) is primary in Europe; US numbers and old UK/Canadian numbers run in
opposite directions (4 mm = US 6 = UK 8).

### Gauge, sizing, yarn

- Gauge is stitches × rows per 10 cm (4 in). Cast-on = width × stitch gauge, rounded to the
  pattern multiple.
- Converting a pattern to a different gauge scales stitch counts by `myGauge / patternGauge`.
- Yarn use is estimated best from a weighed swatch (grams per cm²). Without a swatch, loop length
  can be approximated from stitch density with Munden's relation `l = sqrt(k / S)` (k ≈ 21.6 for
  relaxed plain knitting, S = stitches per cm²). Both methods are offered; both are labelled as
  estimates and include a safety margin.

## 3. Evaluation of additional opportunities

| Idea | Value | Feasibility | Decision |
|---|---|---|---|
| Chart → written instructions | High | High — deterministic with consume/produce semantics | **v1** |
| Written → chart | High | Medium — needs known stitch counts; alignment is ambiguous when counts change | **v1**, with explicit "unsupported / approximate" reporting |
| Automatic stitch counts | High | High | **v1** |
| Inconsistency detection | High | High | **v1** |
| Independently editable repeats | High | High | **v1** (repeat boxes on charts; repeat groups and row repeats in text) |
| Interactive row-by-row guidance | High | High | **v1** |
| Yarn quantity estimation | Medium | Medium | **v1** (two methods, clearly estimates) |
| Templates from measurements | Medium | Medium | **v1** for simple shapes (scarf, hat, cloth, swatch) |
| Custom stitch library | High | High | **v1** |
| Multi-size counts | High | Medium | **v1** (`k10 (12, 14)` syntax, per-size evaluation) |
| Visual fabric simulation | Medium | Low–medium | Later — colourwork preview is already what the chart shows |
| Automatic grading of garments | Medium | Low — real grading is a design discipline, not a formula | Later |
| PDF import & interpretation | Medium | Low offline — needs OCR/layout analysis | Later; documented |
| AI pattern generation | Low–medium | Needs network + key; counts untrustworthy | Later, optional; the validator is already the safety net it would need |
| Pattern recommendations / variations | Low | — | Not planned |

## 4. Recommended functionality (summary)

**Essential (v1):** pattern library with metadata; touch-first chart editor with real stitch
semantics; structured written editor with a live parser; chart ⇄ text conversion; validation
engine; companion mode with counters; gauge / yarn / cast-on / needle tools; stitch library with
custom stitches; multi-size support; professional vector PDF; versioned project file with
import/export and full backup; light and dark mode.

**Optional later:** fabric simulation, PDF import, garment grading, AI assistance, localisation of
the UI beyond English (terminology already switches US/UK), iCloud/Drive sync.

## 5. Technical findings

- Stack verified against the registries on 2026-11-14: Kotlin 2.4.21, Compose Multiplatform 1.12.2,
  Android Gradle Plugin 9.5.1, Gradle 9.9.1, SQLDelight 2.4.1, kotlinx.serialization 1.13.0,
  coroutines 1.11.0, Navigation Compose (JetBrains) 2.10.0.
- AGP 9 no longer allows an Android *application* in a Kotlin Multiplatform module. The project
  therefore uses `:shared` and `:composeApp` as KMP libraries (plugin
  `com.android.kotlin.multiplatform.library`) and a thin `:androidApp`.
- Kotlin can compile iOS klibs on Linux (used here as a compile check); linking the iOS framework
  and building the app need macOS + Xcode.
- PDF: no cross-platform PDF library is needed. A small pure-Kotlin PDF 1.4 writer using the
  built-in Helvetica fonts produces vector output identical on both platforms.
- iOS installation without the App Store: build from Xcode with a free Apple ID ("Personal Team").
  Apps signed this way expire after 7 days and at most 3 can be installed; a paid developer
  membership extends this to one year. AltStore/SideStore/Sideloadly automate re-signing an IPA
  with a free Apple ID. See `docs/INSTALL_IOS.md`.

## 6. Implementation challenges

1. **Touch chart editing** — distinguishing paint from pan. Resolved with explicit tools
   (draw / erase / select / move) while two-finger pinch-and-pan always works.
2. **Chart ⇄ text fidelity** — never silently wrong: WS counterparts must exist, counts must be
   known, and anything unresolvable is reported instead of guessed.
3. **Multi-cell stitches (cables)** — one stitch spanning several cells must move, erase and
   validate as a unit.
4. **iOS without a Mac in CI** — shared code is compile-checked here; the Xcode project must be
   built on a Mac (a GitHub Actions macOS job is provided).

## 7. Sources

- knitCompanion feature and review summaries: https://www.allfreeknitting.com/Product-Reviews/knitCompanion-App · https://apps.apple.com/us/app/id1058142783 · https://www.knitfreedom.com/blog/knitcompanion-a-great-app-for-following-charts/
- Chart editors: https://omidsaffari.com/blog/chart-minder-vs-stitchmastery · https://omidsaffari.com/blog/stitch-fiddle-vs-chart-minder · https://www.stitchfiddle.com/en/help/1pen-80n7rh
- Stitchmastery written-instruction parsing: https://stitchmastery.zendesk.com/hc/en-us/articles/360008958718
- Row counter landscape: https://play.google.com/store/apps/details?id=com.row_counter · https://apps.apple.com/app/id6770376077 · https://rowcounterapp.com/
- Chart conventions: https://craftyarncouncil.com/standards/knit-chart-symbols · https://www.craftyarncouncil.com/standards/knitting-abbreviations · https://www.yarnspirations.com/blogs/how-to/understanding-knitting-symbols · https://www.brooklyntweed.com/reading-charts/
- US/UK terms: https://littleredwindow.com/uk-vs-us-knitting-terms-yarn-weights-and-needle-sizes/ · https://www.handylittleme.com/knitting-abbreviations/
- Compose Multiplatform compatibility: https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html
- iOS sideloading: https://www.xda-developers.com/how-to-sideload-apps-on-iphone/ · https://www.whistleout.com/CellPhones/Guides/how-to-sideload-apps-on-ios-with-altstore-sideloadly
