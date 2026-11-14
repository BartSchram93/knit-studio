# Knit Studio — Product requirements

## Vision

One structured pattern from first idea to last row: **create, visualise, edit, validate, save,
export and knit from it** — offline, without accounts, on Android and iPhone.

## Principles

1. **Correct before pretty.** A wrong stitch count is worse than a rough edge. Every generated
   instruction comes from stitch semantics, and anything the engine cannot resolve is reported.
2. **Never change the pattern behind the user's back.** Validation reports; the user decides.
3. **One pattern, two views.** Charts and written rows are linked views, with generated content
   clearly labelled and manual edits protected.
4. **Quiet interface.** One accent colour, grouped lists, large titles, few buttons per screen.
5. **Local first.** Everything lives in a local database; files and backups are explicit exports.

## Navigation

| Tab | Contents |
|---|---|
| **Home** | Continue knitting (active projects), recent patterns, favourites, drafts, **New** |
| **Patterns** | Full library: search, category / state filters, collections, sorting |
| **Tools** | Gauge, yarn, cast-on & needle calculators, stitch library, yarn & needle stash |
| **Settings** | Units, terminology (US/UK), appearance, PDF defaults, backup & restore, data |

"Create" is a sheet reachable from Home and Patterns (blank pattern, chart first, written first,
from a template, import a file) rather than a tab of its own — it is an action, not a place.

A pattern opens into a hub with five tabs: **Overview · Charts · Rows · Materials · Check**.

## User workflows

1. **Design a chart** → New → Chart → paint stitches → mark a repeat → *Write rows from chart* →
   Check → Export PDF.
2. **Write a pattern** → New → Written → type `K2, *p2, k2; rep from * to end` → live counts →
   *Draw chart from rows*.
3. **Start from measurements** → New → Template → hat → enter head circumference and gauge →
   an editable, validated pattern.
4. **Knit** → open a pattern → *Knit* → tap to advance rows; the chart row is highlighted, repeats
   and extra counters are tracked, and the position is restored next time.
5. **Share / move phones** → Export `.knit` on one phone → import on the other, or use a full backup.

## Feature list and status

Legend: ✅ implemented · ◐ partially · ⏭ planned

### Essential
- ✅ Pattern metadata, categories, difficulty, sizes, gauge, measurements, notes, reference images
- ✅ Create / edit / duplicate / delete / archive / favourite / search / sort / filter / collections
- ✅ Chart editor: paint, erase, select, fill, copy/cut/paste, flip, undo/redo, zoom/pan, resize,
  multiple charts, repeat boxes, colourwork palette, row & stitch numbers, flat / round, first-row
  side, reading direction
- ✅ Stitch library (built-in + custom stitches with their own symbols)
- ✅ Written editor: sections, rows, rounds, row repeats, notes, `*…; rep from *`, `( ) n times`,
  `[ ] n times`, open-ended instructions, multi-size counts, stated counts
- ✅ Chart → rows and rows → chart, with unsupported cases reported
- ✅ Validation engine with errors / warnings / suggestions and precise locations
- ✅ Companion mode: row tracking per section, repeat tracking, extra counters, notes, resume
- ✅ Yarn & needle records, yarn estimate, gauge calculator, cast-on calculator, needle converter
- ✅ PDF export (A4 / US Letter, portrait / landscape, selectable sections, vector charts)
- ✅ `.knit` project files and full backups with validation on import

### Later
- ⏭ Fabric simulation preview, PDF import, garment grading, optional AI assistance,
  UI localisation, cloud sync

See `docs/PROJECT.md` for live progress, test results and known issues.
