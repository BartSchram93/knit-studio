# Knit Studio file formats

UTF-8 JSON. Two kinds of file:

| Extension | `format` | Contents |
|---|---|---|
| `.knit` | `knit-studio.pattern` | One pattern with its custom stitches, reference images and knitting progress |
| `.knitbackup` | `knit-studio.backup` | All patterns, images, custom stitches, collections, stash and settings |

```json
{
  "format": "knit-studio.pattern",
  "version": 1,
  "app": "Knit Studio",
  "exportedAt": 1794643200000,
  "pattern": { "id": "…", "name": "Feather & fan lace", "charts": [ … ], "sections": [ … ] },
  "images": { "<image id>": "<base64 JPEG>" }
}
```

## Versioning rules

- `version` is an integer. A reader refuses files with a higher version than it knows
  ("made with a newer version") instead of reading them partially.
- Older versions are upgraded one step at a time through `KnitFiles.MIGRATIONS` before decoding.
- Unknown fields are ignored, so adding an optional field does not require a new version.

## Charts

```json
{ "id": "…", "name": "Cable panel", "cols": 8, "rows": 6,
  "cells": ["p","p","k","k","k","k","p","p", "…"],
  "construction": "FLAT", "firstRowSide": "RS", "rsDirection": "RIGHT_TO_LEFT",
  "repeats": [{ "id": "…", "colStart": 0, "colEnd": 7, "rowStart": 0, "rowEnd": 5 }],
  "palette": [{ "name": "A", "argb": 4290925372 }] }
```

`cells` has `cols × rows` entries, **bottom row first, each row left to right as drawn**.
A cell is `stitch`, optionally `#colour-index` and `+part`: `k`, `k#0` (knit in palette colour 0),
`c4f+2` (third cell of a 2/2 left cross).

## Written sections

Rows keep the text exactly as typed: `{ "kind": "INSTRUCTION", "text": "K2, *p2, k2; rep from * to end" }`.
Row repeats are `{ "kind": "REPEAT_ROWS", "repeatFrom": 1, "repeatTo": 2, "repeatTimes": { "values": [9] } }`.
Sized numbers are lists, smallest size first.

## Import

Importing validates the format tag and version, decodes, and then repairs **structural** damage
only (a chart whose cell count does not match its size, repeat boxes outside the chart, colour
indices without a palette entry, duplicate ids). Each repair is reported to the user. An imported
pattern never overwrites an existing one; it gets a new id and "(imported)" in its name.
