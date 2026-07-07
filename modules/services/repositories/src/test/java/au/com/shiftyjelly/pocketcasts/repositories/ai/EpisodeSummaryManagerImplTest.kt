package au.com.shiftyjelly.pocketcasts.repositories.ai

import au.com.shiftyjelly.pocketcasts.models.db.dao.EpisodeSummaryDao
import au.com.shiftyjelly.pocketcasts.models.entity.EpisodeSummary
import au.com.shiftyjelly.pocketcasts.models.to.Transcript
import au.com.shiftyjelly.pocketcasts.models.to.TranscriptEntry
import au.com.shiftyjelly.pocketcasts.models.to.TranscriptType
import au.com.shiftyjelly.pocketcasts.repositories.transcript.TranscriptManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class EpisodeSummaryManagerImplTest {
    private val transcriptManager = mock<TranscriptManager>()
    private val claudeManager = mock<ClaudeManager>()
    private val dao = TestEpisodeSummaryDao()
    private val manager = EpisodeSummaryManagerImpl(dao, transcriptManager, claudeManager)

    @Test
    fun `returns cached summary without calling claude`() = runTest {
        dao.insert(EpisodeSummary(episodeUuid = EPISODE_UUID, summary = "Cached", model = "claude-sonnet-5"))

        assertEquals("Cached", manager.summaryFor(EPISODE_UUID))
        verify(claudeManager, never()).complete(anyOrNull(), any(), any())
    }

    @Test
    fun `generates and caches summary from transcript`() = runTest {
        whenever(transcriptManager.loadTranscript(EPISODE_UUID)).thenReturn(transcript())
        whenever(claudeManager.complete(anyOrNull(), any(), any())).thenReturn("Fresh summary")

        assertEquals("Fresh summary", manager.summaryFor(EPISODE_UUID))

        val cached = dao.findByEpisodeUuid(EPISODE_UUID)
        assertEquals("Fresh summary", cached?.summary)
        assertEquals(PODCAST_UUID, cached?.podcastUuid)
        assertEquals(ClaudeManager.MODEL, cached?.model)
    }

    @Test
    fun `fails when transcript is missing`() = runTest {
        whenever(transcriptManager.loadTranscript(EPISODE_UUID)).thenReturn(null)

        val exception = runCatching { manager.summaryFor(EPISODE_UUID) }.exceptionOrNull()

        assertTrue(exception is IllegalStateException)
        verify(claudeManager, never()).complete(anyOrNull(), any(), any())
    }

    private class TestEpisodeSummaryDao : EpisodeSummaryDao() {
        private val summaries = mutableMapOf<String, EpisodeSummary>()

        override suspend fun insert(summary: EpisodeSummary) {
            summaries[summary.episodeUuid] = summary
        }

        override suspend fun findByEpisodeUuid(episodeUuid: String): EpisodeSummary? = summaries[episodeUuid]

        override suspend fun deleteByEpisodeUuid(episodeUuid: String) {
            summaries.remove(episodeUuid)
        }
    }

    private companion object {
        const val EPISODE_UUID = "episode-uuid"
        const val PODCAST_UUID = "podcast-uuid"

        fun transcript() = Transcript.Text(
            entries = listOf(
                TranscriptEntry.Speaker("Host"),
                TranscriptEntry.Text("Welcome to the show."),
            ),
            type = TranscriptType.Vtt,
            url = "https://example.com/transcript.vtt",
            isGenerated = false,
            episodeUuid = EPISODE_UUID,
            podcastUuid = PODCAST_UUID,
        )
    }
}
