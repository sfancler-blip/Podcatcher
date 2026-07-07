# Podcatcher — Customization Plan

A personal fork of [Automattic/pocket-casts-android](https://github.com/Automattic/pocket-casts-android)
(MPL-2.0) with four custom features added. This document is the master plan; per-feature
detail lives in [`docs/prd/`](prd/) and verification steps in [`docs/TESTING-PLAN.md`](TESTING-PLAN.md).

> Produced from a deep-research pass (verified against the live source) and an architecture
> planning session. Paths below were confirmed against the imported code.

## The four features

| # | Feature | Nature | Build effort |
|---|---------|--------|--------------|
| 2 | **Hyperlinked chapter bookmarks** | Extension of existing bookmarks + chapters | Quick win (~2–4 sessions) |
| 3 | **Claude episode summaries** | New — transcript → Claude → stored summary | ~4–6 sessions |
| 4 | **Interactive Claude chat** | Re-point the *existing* `chat` module at your Claude key | ~3–5 sessions |
| 1 | **Skip ads** | New + riskiest — Claude finds ad spans → player skips them | ~6–10 sessions |

### Key finding from research
Pocket Casts **already ships** an interactive episode-chat feature (`modules/features/chat`) that
reads the transcript and calls Automattic's AI backend — but it is **locked behind their paid
subscription paywall**. Bookmarks already carry `ai_title`/`ai_summary` columns. So Feature 4 is
largely "re-point + un-gate", not "build", and Features 1/3 reuse the same transcript foundation.

## Shared foundation (built once)
Everything except Feature 2 depends on **episode transcripts** (already available via
`TranscriptManager` / `TranscriptDao`) plus a new **Anthropic Claude client**:

- **`modules/services/servers/…/anthropic/`** — a Retrofit service for the Anthropic Messages API,
  registered in `modules/services/servers/…/di/NetworkModule.kt`, key injected via an OkHttp
  interceptor (`x-api-key` + `anthropic-version` headers).
- **`modules/services/repositories/…/ai/ClaudeManager`** — consumed by the features (mirrors the
  existing `PodcastCacheServiceManager` / `ChatManagerImpl` split; keeps features services-only).
- Cache every result in Room so we never pay for the same episode twice.
- Chunk long transcripts via the existing `TranscriptWindowExtractor` (map-reduce for summaries).

> **API params:** confirm the exact Anthropic Messages API request/response shape, `anthropic-version`
> header, streaming semantics, and model IDs via the `claude-api` skill / official docs at
> implementation time — do not hardcode from memory.

## Decisions locked in
- **Model:** **`claude-sonnet-5`** for all AI features (chat, summaries, ad-detection). Simple, capable, cost-effective.
- **API key storage:** **in-app only** — entered in app settings, stored in DataStore
  (`modules/services/preferences`), **never** written to `local.properties`/BuildConfig and never
  committed. This keeps the key out of both the repo *and* the compiled APK.
- **Privacy:** an explicit first-run consent screen + settings toggle gates any transcript being
  sent to Anthropic. Off until the user opts in.
- **Chat paywall:** bypassed for this personal build, as an isolated/revertable change.
- **Branding / Firebase:** unchanged. Repo stays private; rebrand only needed before any public
  distribution. Checked-in debug `google-services.json` means no Firebase setup for debug builds.

## Feature designs (summary — see `docs/prd/` for detail)

### Feature 2 — Hyperlinked chapter bookmarks (quick win, no API)
Make bookmarks navigable and tie each to its containing chapter; tapping seeks to that point.
All pieces already exist — this is glue:
- Bookmarks: `modules/services/model/…/entity/Bookmark.kt` (has `time`), `BookmarkDao.kt`.
- Chapters: `modules/services/model/…/to/Chapter.kt` (`startTime`/`endTime`), `ChapterManager`.
- Seek: `PlaybackManager.seekToTimeMs(...)` / `skipToChapter(...)`.
- UI surfaces: `modules/features/player/…/view/bookmark/` and podcast bookmark adapters.
- **Recommended: derive** the containing chapter from `bookmark.time` at display time (no schema
  change). Add persisted `chapterTitle`/`chapterIndex` columns only if labels must be stored/synced.

### Feature 3 — Claude episode summaries
Per-episode summary generated from the transcript, shown in/near the episode screen.
- `SummaryManager` (repositories/ai) → transcript → `ClaudeManager` → persist.
- New Room `EpisodeSummary` entity + DAO + migration (135→136) + migration test.
- On-demand button first; background auto-summarize (WorkManager) as a follow-up.

### Feature 4 — Interactive Claude chat over an episode
- Reuse the existing `chat` UI, `ChatViewModel`, and `EpisodeChat`/`EpisodeChatMessage` entities.
- Add a `ChatManager` implementation that calls `ClaudeManager` with the transcript as context,
  selected by Hilt/flag so the upstream backend path stays intact for clean merges.
- Un-gate the paywall / feature flag for personal use.

### Feature 1 — Skip ads (last; riskiest)
- Detection: transcript + Claude → `[{startMs, endMs, confidence}]`, pre-computed and cached.
- New Room `AdSegment` entity + DAO + migration (136→137) + test.
- Skip: a position observer on `PlaybackManager.playbackStateFlow` calls `seekToTimeMs(endMs)`
  when entering a detected ad range (same mechanism as chapter-skip).
- UX: settings toggle, per-podcast opt-out, "skipped ad — undo" affordance, confidence threshold.

## Build order & milestones
Recommended: **Feature 2 (quick win) → foundation → Feature 3 → Feature 4 → Feature 1.**

| Milestone | Content | Gate |
|-----------|---------|------|
| M0 ✅ | Import upstream (full history) onto `claude/podcasting-app-customize-bosduh` | Done |
| M1 ✅ | Fork attribution + planning docs + CI/SessionStart setup | Done |
| M2 ✅ | Baseline verified: `spotlessCheck`, model unit tests, `assembleDebugProd` (CI) | Green APK |
| M2.5 ✅ | **Feature 2** — chapter bookmarks | Done |
| M4 ✅ | **Foundation** — Anthropic service + `ClaudeManager` + in-app key + privacy opt-in | MockWebServer + manager unit tests |
| M5 ✅ | **Feature 3** — summaries (+ auto migration 135→136) | On-demand generate button in the Summary tab |
| M6 ✅ | **Feature 4** — chat re-point + un-gate | `DelegatingChatManager` picks Claude when a key is set |
| M7 ✅ | **Feature 1** — ad-skip (same 135→136 migration) | Position observer in `PlaybackManager` + opt-outs |
| M8 🟢 | CI green (tests + APK artifact); behavioral test on device remains (the remote rejects tag pushes, so milestones are tracked here instead) | Sideload the CI APK |

### Implementation notes (what shipped, where it deviates from the sketch above)
- **One schema bump, not two.** All new tables (`episode_summaries`, `episode_ad_segments`,
  `episode_ad_analysis`) and the `podcasts.ad_skip_opt_out` column land in a single additive
  `AutoMigration(135 → 136)` — Room derives it, so there is no hand-written SQL to drift.
  The generated `136.json` schema is committed by CI (web sessions have no Android SDK to
  produce its `identityHash`).
- **Summary UI was already upstream.** Pocket Casts ships a Plus-gated Summary tab on the
  episode screen backed by `TranscriptManager.loadSummaryText`. Feature 3 plugs in underneath:
  `EpisodeSummaryManager` serves a cached Claude summary first, upstream meta-JSON second, and
  the tab gains a "Generate summary" button when Claude is configured and nothing is cached.
- **Chat backend selection is per-call.** `ChatManager` is bound to `DelegatingChatManager`,
  which routes to `ClaudeChatManager` whenever the key + opt-in are set and falls back to the
  upstream backend otherwise. Both persist to the same Room tables. Claude requires
  user-first/alternating messages, so history is normalized (leading assistant welcome dropped,
  consecutive same-role messages merged).
- **Paywall bypass is one line** — `EpisodeFragmentViewModel` pins `isPlusUser = true` (marked
  revertable). `Feature.EPISODE_CHAT` / `Feature.AI_SUMMARIES` default on in debug builds; use
  the beta-features dev toggles if a Firebase remote value ever turns them off.
- **Ad-skip "undo" simplification:** each detected segment auto-skips at most once per playback
  session, so seeking back into an ad plays it instead of fighting the user. Skips show a toast
  and add to the time-saved stat. Detection runs once per episode and is cached (including the
  "no ads found" result) in `episode_ad_analysis`.
- **Settings UI:** Settings → Claude AI holds the encrypted API key field (private prefs,
  `PBEWithMD5AndDES` like `cachedMembership`), the transcript opt-in, and the ad-skip toggle.
  The per-podcast opt-out row lives in each podcast's settings.
- **No new analytics events** — the EventHorizon schema is generated outside this repo.

## Risks & constraints
1. **Privacy** — transcript content is sent to Anthropic for features 1/3/4; explicit opt-in required.
2. **API key** — in-app/DataStore only; never committed, never baked into the APK.
3. **Cost** — cache results, cap `max_tokens`, chunk long transcripts.
4. **Room migrations** — schema is at **v135** with `exportSchema=true`; every schema change needs a
   correct migration (`AppDatabase.kt`) + a test in `app/src/androidTest/…/AppDatabaseTest.kt`, or the
   app crashes on upgrade.
5. **Keep upstream paths compilable** — prefer additive changes / parallel Hilt impls over rewriting
   upstream files, so `git merge upstream/main` stays low-conflict.
6. **Build/verify reality** — ~37 modules, ~8 GB Gradle heap, no Android SDK preinstalled in web
   sessions, no realistic in-session emulator. Verification = static gates + `assembleDebugProd`
   (in CI) + **sideloading the debug APK to a real device** for behavioral testing.
7. **Trademark / MPL** — private use is fine; rebrand (`applicationId`, name, icon) before any public
   distribution; keep `LICENSE.md` and file headers intact.

## Upstream sync
`upstream` remote = `Automattic/pocket-casts-android`. Merge periodically:
`git fetch upstream main && git merge upstream/main`. New modules/services minimize conflicts.
