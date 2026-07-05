package au.com.shiftyjelly.pocketcasts.player.viewmodel

import au.com.shiftyjelly.pocketcasts.models.entity.Bookmark
import au.com.shiftyjelly.pocketcasts.models.to.Chapter
import au.com.shiftyjelly.pocketcasts.models.to.Chapters
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookmarkChapterResolverTest {
    private val chapters = Chapters(
        listOf(
            Chapter(title = "Intro", startTime = 0.seconds, endTime = 60.seconds, index = 0, uiIndex = 0),
            Chapter(title = "Main Segment", startTime = 60.seconds, endTime = 120.seconds, index = 1, uiIndex = 1),
        ),
    )

    @Test
    fun `resolves the chapter that contains the bookmark time`() {
        assertEquals("Main Segment", chapters.chapterTitleForBookmark(bookmarkAt(75)))
        assertEquals("Intro", chapters.chapterTitleForBookmark(bookmarkAt(0)))
    }

    @Test
    fun `returns null when the episode has no chapters`() {
        assertNull(Chapters().chapterTitleForBookmark(bookmarkAt(75)))
        assertNull(null.chapterTitleForBookmark(bookmarkAt(75)))
    }

    @Test
    fun `returns null when the bookmark falls outside every chapter`() {
        assertNull(chapters.chapterTitleForBookmark(bookmarkAt(200)))
    }

    private fun bookmarkAt(timeSecs: Int) = Bookmark(uuid = "uuid", timeSecs = timeSecs)
}
