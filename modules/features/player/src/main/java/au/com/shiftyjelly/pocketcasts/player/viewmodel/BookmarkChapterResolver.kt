package au.com.shiftyjelly.pocketcasts.player.viewmodel

import au.com.shiftyjelly.pocketcasts.models.entity.Bookmark
import au.com.shiftyjelly.pocketcasts.models.to.Chapters
import kotlin.time.Duration.Companion.seconds

/**
 * Resolves the title of the chapter that contains a bookmark's timestamp.
 *
 * Returns null when the episode has no chapters (null receiver or empty [Chapters]) or when the
 * bookmark falls outside every chapter, so callers can simply hide the label in those cases.
 */
internal fun Chapters?.chapterTitleForBookmark(bookmark: Bookmark): String? = this?.getChapter(bookmark.timeSecs.seconds)?.title
