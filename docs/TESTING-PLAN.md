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

> **CI note:** the CI workflow runs `spotlessCheck` + `:modules:services:model:testDebugUnitTest`
> + `:modules:services:servers:testDebugUnitTest` (the servers module does not depend on the
> local crashlogging module, so it is CI-safe). Feature-module and repositories unit tests
> transitively rebuild `modules/services/crashlogging`, whose KSP/Dagger-generated Java compile
> is flaky in headless CI (an upstream quirk, unrelated to this fork) — run those from a full
> local/Android Studio build: `:modules:services:repositories:testDebugUnitTest` covers
> `ClaudeManagerImpl`, `EpisodeSummaryManagerImpl`, `AdSkipManagerImpl`, and `ClaudeChatManager`;
> `:modules:features:podcasts:testDebugUnitTest` covers the episode page summary state.
> CI also commits freshly generated Room schema JSONs (e.g. `136.json`) back to the branch,
> because their `identityHash` can only be produced by the Room compiler.

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
- Unit: `EpisodeSummaryManagerImpl` with a mocked `ClaudeManager` (no live key in tests); cache-first
  behavior; transcript-absent path throws. `AnthropicServiceTest` exercises the wire format against
  MockWebServer (CI-covered).
- Migration: `AppDatabaseTest.migrate135To136CreatesClaudeAiTablesAndAdSkipOptOut` covers the
  auto migration (all new tables + the podcast column, device-only).
- Device: enter key + opt in (Settings → Claude AI) → open an episode with a transcript →
  Summary tab → Generate summary → summary renders and is cached.

### Feature 4 — Claude chat
- Unit: `ClaudeChatManagerTest` — transcript becomes system context, history normalization
  (drop leading assistant welcome, merge consecutive same-role), no persistence on failure.
- Device: with a key set, the chat banner answers via Claude (`DelegatingChatManager`); with no
  key it falls back to the upstream backend.

### Feature 1 — Ad-skip
- Unit: `AdSkipManagerImplTest` — JSON span parsing (with prose/code fences), invalid-span
  filtering, cache-first, "analyzed but no ads" persistence, unconfigured/no-transcript paths.
- Migration: covered by the same 135→136 test above.
- Device: enable ad skipping → play an episode with sponsor reads → spans skip with a toast →
  seeking back into the ad plays it (each segment skips once per session) → per-podcast opt-out
  respected.

## Definition of done (per feature)
`spotlessCheck` ✅ · feature + model unit tests ✅ · migration test (if schema changed) ✅ ·
`assembleDebugProd` ✅ (CI) · behavioral check on device ✅ (user).
