# Contributing

Knit Studio is a personal project; these notes keep it easy to pick up again.

## Workflow

- `main` always builds and passes tests. Work on `feature/<name>` branches and merge with
  `--no-ff` when a feature is complete.
- Commit in logical steps with messages that say what changed and why.
- Tag working releases `vMAJOR.MINOR.PATCH` and note them in `CHANGELOG.md`.
- Never commit keystores, certificates, provisioning profiles, `.env` or `local.properties`
  (all are git-ignored).

## Before committing

```bash
./gradlew :shared:jvmTest :composeApp:jvmTest :androidApp:testDebugUnitTest
```

```bash
./gradlew :androidApp:assembleDebug :composeApp:compileKotlinIosArm64
```

The second command also compile-checks the iOS sources on any operating system.

## Where things go

- Knitting rules, arithmetic and anything that could be wrong in a pattern: `shared`, with a
  unit test built on a realistic knitting example. No Compose imports in `shared`.
- Screens and components: `composeApp/commonMain`. Platform APIs only behind the small
  `expect`/`actual` surface in `ui/platform`.
- A new stitch: add it to `BuiltInStitches` with its consume/produce counts, wrong-side
  counterpart and mirror, add a glyph if needed, and it works everywhere (charts, rows, PDF).
- A change to the stored model: add fields with defaults (old files keep loading). For a breaking
  change, bump `KnitFiles.VERSION` and add a migration step plus a test.
- A change to the SQL schema: add `N.sqm` next to `Knit.sq` and extend the migration test.

## Style

Kotlin official style. Comments explain intent and knitting reasoning, not syntax. User-facing
text is plain and specific: say what is wrong and where, in knitters' words.

## UI tests and screenshots

`composeApp/src/jvmTest` drives the real UI headlessly. Screenshots are written to
`composeApp/build/screenshots/`; look at them after changing a screen.
