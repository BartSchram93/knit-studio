# Design system

The reference implementation is `composeApp/src/commonMain/kotlin/studio/knit/ui/theme/Theme.kt`
and `…/components/`.

## Principles

- **Quiet.** Grouped grey backgrounds, white cards, hairline separators, one accent colour.
- **Content first.** Large titles that collapse into the bar; few buttons per screen; secondary
  actions live in "…" menus and touch-and-hold menus.
- **One product.** The same palette, type scale, icons and layouts on both platforms; the system
  font of each platform (San Francisco / Roboto); system sheets, pickers and share dialogs.

## Colour

| Token | Light | Dark | Use |
|---|---|---|---|
| background | `#F2F2F7` | `#000000` | page |
| surface | `#FFFFFF` | `#1C1C1E` | cards, groups |
| label / secondary / tertiary | `#1C1C1E` / `#6E6E73` / `#AEAEB2` | `#F5F5F7` / `#98989F` / `#636366` | text |
| accent (madder red) | `#B4533C` | `#E2775F` | actions, selection, repeat boxes |
| error / warning / success / info | `#D70015` / `#B25000` / `#248A3D` / `#3A6EA5` | brighter variants | validation |
| chart paper / grid / ink | `#FFFFFF` / `#C7C7CC` / `#1C1C1E` | `#1C1C1E` / `#48484A` / `#F2F2F7` | charts |

Text colours meet WCAG AA contrast on their backgrounds.

## Type

iOS-style scale: Large title 34 bold · Title 28/22/20 · Headline 17 semibold · Body 17 ·
Callout 16 · Subhead 15 · Footnote 13 · Caption 12. Instructions are set at 19 for reading at
arm's length; the row counter at 64. Sizes are in `sp`, so they follow the system text size.

## Layout

16 dp page margin · 14 dp card radius · 10 dp field radius · 44 dp minimum touch target ·
section headers in small caps above groups, explanatory footers below.

## Motion and feedback

Push/pop slides between screens (300 ms); pressed controls dim instead of rippling; the row
number slides when advancing; a light haptic tick when a cell is painted or a counter changes
(can be switched off).

## Icons

About 60 line icons on a 24-unit grid with 1.8-unit round strokes, defined as vector paths in
`Icons.kt`. Chart symbols are separate (`shared/…/stitch/Glyphs.kt`) and shared with the PDF.

## Accessibility

Content descriptions on icon buttons and charts, headings marked for screen readers, live regions
for the current row and transient messages, colour never the only signal (issues carry icons and
text), full light and dark themes.
