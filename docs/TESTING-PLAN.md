# Podcatcher — Testing & Verification Plan

The app is a ~37-module Android build (~8 GB Gradle heap) with **no Android SDK preinstalled** in
web sessions and **no realistic in-session emulator**. Verification therefore layers three levels.

## Level 1 — Static gates (run in-session and in CI; no device)
```bash
./gradlew spotlessCheck                                 # formatting (must pass before merge)
./gradlew :modules:services:model:testDebugUnitTest     # model/DAO/migration unit tests
./gradlew :modules:features:<feature>:testDebugUnitTest # per-feature unit tests
./gradlew aggregatedLintRelease                          # lint (optional, slower)
```
Auto-fix formatting: `./gradlew spotlessApply`.

## Level 2 — Assemble (proves it builds; CI-preferred)
```bash
./gradlew :app:assembleDebugProd     # debug APK vs production servers; no secrets needed
```
- First build needs the Android SDK bootstrapped (`platforms;android-37`, `build-tools;36.0.0`,
  `platform-tools`) and `sdk.dir` in `local.properties`.
- Most reliable signal comes from **GitHub Actions** (runners ship the Android SDK). A workflow
  running Level 1 + this assemble on push is the "does it still build?" gate, and can upload the APK
  as an artifact.

## Level 3 — On-device behavioral test (⚠ requires the user's device)
```bash
./gradlew :app:installDebugProd      # with a connected device/emulator
# or sideload the CI-produced APK artifact
```
Instrumented tests (incl. Room migration tests) run only where a device exists:
```bash
./gradlew :app:connectedDebugAndroidTest
```

## Per-feature acceptance checks

### Feature 2 — Chapter bookmarks
- Unit: bookmark→chapter resolution (time inside chapter range; chapterless episode falls back to plain seek).
- Device: create a bookmark in a chaptered episode → bookmark row shows chapter label → tap seeks to chapter start.

### Feature 3 — Claude summaries
- Unit: `SummaryManager` with a **mocked** Anthropic endpoint (no live key in tests); transcript-absent path handled.
- Migration: `AppDatabaseTest` covers 135→136 (`EpisodeSummary`).
- Device: opt in → request summary on an episode with a transcript → summary renders and is cached.

### Feature 4 — Claude chat
- Unit: transcript→messages mapping; backend selected by flag; upstream path still compiles.
- Device: paywall bypassed → ask a question about an episode → answer returns via the user's key.

### Feature 1 — Ad-skip
- Unit: ad-range detection parsing; position-observer skip logic (mock `PlaybackManager`).
- Migration: `AppDatabaseTest` covers 136→137 (`AdSegment`).
- Device: episode with known ads → ad spans skipped → "undo" restores position → per-podcast opt-out respected.

## Definition of done (per feature)
`spotlessCheck` ✅ · feature + model unit tests ✅ · migration test (if schema changed) ✅ ·
`assembleDebugProd` ✅ (CI) · behavioral check on device ✅ (user).
