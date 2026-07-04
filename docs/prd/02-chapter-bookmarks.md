# PRD — Feature 2: Hyperlinked Chapter Bookmarks

**Status:** planned (first feature / quick win) · **No external API** · **No secrets**

## Goal
Make bookmarks chapter-aware: each bookmark shows the chapter it falls in, and tapping a bookmark
seeks playback to that point. A lightweight navigation improvement built entirely on existing code.

## Why it's a quick win
Every ingredient already exists in the codebase — this is glue, not new infrastructure:
- **Bookmarks:** `modules/services/model/…/entity/Bookmark.kt` (has `time` in seconds), `BookmarkDao.kt`.
- **Chapters:** `modules/services/model/…/to/Chapter.kt` (`startTime`/`endTime`), `ChapterManager`/`ChapterManagerImpl`.
- **Seek:** `PlaybackManager.seekToTimeMs(...)` and `skipToChapter(...)`.
- **UI:** `modules/features/player/…/view/bookmark/` (BookmarkPage/BookmarkViewModel/BookmarkDetailPage)
  and podcast bookmark adapters in `modules/features/podcasts/…/view/podcast/adapter/`.

## Approach (recommended: derive, no schema change)
1. Add a helper (in `repositories` or a small util) that, given `bookmark.time` and an episode's
   chapter list from `ChapterManager`, returns the containing `Chapter` (range containment) or null.
2. In the bookmark ViewModel/UI state, expose an optional resolved chapter (title + start time).
3. Render the chapter label on the bookmark row/detail; make the row/tap action call
   `PlaybackManager.seekToTimeMs(chapter.startTime)` (or the bookmark's own time — decide during build).
4. Chapterless episodes: hide the label and fall back to seeking the bookmark's own timestamp.

*Optional persisted variant:* add nullable `chapterTitle`/`chapterIndex` columns to `Bookmark`
(migration 135→136) only if labels must be stored/synced. Not needed for the derive approach.

## Scope
- **In:** chapter resolution for bookmarks, chapter label in bookmark UI, seek-to-chapter on tap,
  chapterless fallback, unit tests.
- **Out:** creating bookmarks (already exists), cross-device sync of new columns, AI (that's F1/F3/F4).

## Acceptance criteria
- A bookmark in a chaptered episode displays its chapter title.
- Tapping the bookmark seeks to the chapter (or bookmark) timestamp and resumes context.
- An episode without chapters shows no chapter label and still seeks correctly.
- `spotlessCheck`, player + model unit tests, and `assembleDebugProd` all pass.

## Risks
- Low. Main edge cases: chapterless episodes, and mapping a bookmark time to the right chapter
  (simple range containment; watch inclusive/exclusive boundaries at chapter edges).
