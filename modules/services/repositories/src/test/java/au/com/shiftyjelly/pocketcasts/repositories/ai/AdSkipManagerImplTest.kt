package au.com.shiftyjelly.pocketcasts.repositories.ai

import au.com.shiftyjelly.pocketcasts.models.db.dao.AdSegmentDao
import au.com.shiftyjelly.pocketcasts.models.entity.EpisodeAdAnalysis
import au.com.shiftyjelly.pocketcasts.models.entity.EpisodeAdSegment
import au.com.shiftyjelly.pocketcasts.models.to.Transcript
import au.com.shiftyjelly.pocketcasts.models.to.TranscriptEntry
import au.com.shiftyjelly.pocketcasts.models.to.TranscriptType
import au.com.shiftyjelly.pocketcasts.preferences.Settings
import au.com.shiftyjelly.pocketcasts.preferences.UserSetting
import au.com.shiftyjelly.pocketcasts.repositories.transcript.TranscriptManager
import au.com.shiftyjelly.pocketcasts.servers.anthropic.AnthropicMessage
import com.squareup.moshi.Moshi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AdSkipManagerImplTest {
    private val dao = TestAdSegmentDao()
    private val transcriptManager = mock<TranscriptManager>()
    private val claudeManager = mock<ClaudeManager> {
        on { isConfigured() } doReturn true
    }
    private val settings = mock<Settings> {
        on { adSkipEnabled } doReturn UserSetting.Mock(true, mock())
    }
    private val manager = AdSkipManagerImpl(dao, transcriptManager, claudeManager, settings, Moshi.Builder().build())

    @Test
    fun `returns cached segments without re-analyzing`() = runTest {
        dao.analysis = EpisodeAdAnalysis(episodeUuid = EPISODE_UUID, model = "claude-sonnet-5")
        dao.segments += EpisodeAdSegment(episodeUuid = EPISODE_UUID, startMs = 0, endMs = 30_000, confidence = 0.9)

        val segments = manager.adSegmentsFor(EPISODE_UUID)

        assertEquals(1, segments.size)
        verify(claudeManager, never()).complete(anyOrNull(), any(), any())
    }

    @Test
    fun `detects ads and persists analysis`() = runTest {
        whenever(transcriptManager.loadTranscript(EPISODE_UUID)).thenReturn(transcript())
        whenever(claudeManager.complete(anyOrNull(), any(), any())).thenReturn(
            """[{"startMs": 30000, "endMs": 90000, "confidence": 0.85}]""",
        )

        val segments = manager.adSegmentsFor(EPISODE_UUID)

        assertEquals(1, segments.size)
        assertEquals(30_000L, segments.single().startMs)
        assertEquals(90_000L, segments.single().endMs)
        assertEquals(0.85, segments.single().confidence, 0.0001)
        assertEquals(EPISODE_UUID, dao.analysis?.episodeUuid)

        val messagesCaptor = argumentCaptor<List<AnthropicMessage>>()
        verify(claudeManager).complete(anyOrNull(), messagesCaptor.capture(), any())
        assertTrue(messagesCaptor.firstValue.single().content.contains("[0-29000] Welcome to the show."))
    }

    @Test
    fun `tolerates prose around the json array`() = runTest {
        whenever(transcriptManager.loadTranscript(EPISODE_UUID)).thenReturn(transcript())
        whenever(claudeManager.complete(anyOrNull(), any(), any())).thenReturn(
            "Here are the ads:\n```json\n[{\"startMs\": 1000, \"endMs\": 5000, \"confidence\": 0.7}]\n```",
        )

        val segments = manager.adSegmentsFor(EPISODE_UUID)

        assertEquals(1, segments.size)
        assertEquals(1000L, segments.single().startMs)
    }

    @Test
    fun `marks episode analyzed when reply is unparseable`() = runTest {
        whenever(transcriptManager.loadTranscript(EPISODE_UUID)).thenReturn(transcript())
        whenever(claudeManager.complete(anyOrNull(), any(), any())).thenReturn("I could not find any ads.")

        val segments = manager.adSegmentsFor(EPISODE_UUID)

        assertEquals(0, segments.size)
        assertEquals(EPISODE_UUID, dao.analysis?.episodeUuid)
    }

    @Test
    fun `returns empty when claude is not configured`() = runTest {
        whenever(claudeManager.isConfigured()).thenReturn(false)

        assertEquals(0, manager.adSegmentsFor(EPISODE_UUID).size)
        assertEquals(null, dao.analysis)
    }

    @Test
    fun `returns empty and does not persist when the request fails`() = runTest {
        whenever(transcriptManager.loadTranscript(EPISODE_UUID)).thenReturn(transcript())
        whenever(claudeManager.complete(anyOrNull(), any(), any())).thenAnswer { throw IllegalStateException("boom") }

        assertEquals(0, manager.adSegmentsFor(EPISODE_UUID).size)
        assertEquals(null, dao.analysis)
    }

    @Test
    fun `drops invalid spans`() = runTest {
        whenever(transcriptManager.loadTranscript(EPISODE_UUID)).thenReturn(transcript())
        whenever(claudeManager.complete(anyOrNull(), any(), any())).thenReturn(
            """[
                {"startMs": 5000, "endMs": 1000, "confidence": 0.9},
                {"startMs": -100, "endMs": 1000, "confidence": 0.9},
                {"startMs": 1000, "endMs": 2000, "confidence": 1.5},
                {"startMs": 1000, "endMs": 2000, "confidence": 0.8}
            ]""",
        )

        val segments = manager.adSegmentsFor(EPISODE_UUID)

        assertEquals(1, segments.size)
        assertEquals(0.8, segments.single().confidence, 0.0001)
    }

    private class TestAdSegmentDao : AdSegmentDao() {
        var analysis: EpisodeAdAnalysis? = null
        val segments = mutableListOf<EpisodeAdSegment>()

        public override suspend fun insertSegments(segments: List<EpisodeAdSegment>) {
            this.segments += segments
        }

        public override suspend fun insertAnalysis(analysis: EpisodeAdAnalysis) {
            this.analysis = analysis
        }

        public override suspend fun deleteSegments(episodeUuid: String) {
            segments.removeAll { it.episodeUuid == episodeUuid }
        }

        override suspend fun segmentsForEpisode(episodeUuid: String): List<EpisodeAdSegment> {
            return segments.filter { it.episodeUuid == episodeUuid }.sortedBy { it.startMs }
        }

        override suspend fun analysisForEpisode(episodeUuid: String): EpisodeAdAnalysis? {
            return analysis?.takeIf { it.episodeUuid == episodeUuid }
        }
    }

    private companion object {
        const val EPISODE_UUID = "episode-uuid"

        fun transcript() = Transcript.Text(
            entries = listOf(
                TranscriptEntry.Text("Welcome to the show.", startTimeMs = 0, endTimeMs = 29_000),
                TranscriptEntry.Text("This episode is sponsored by Acme.", startTimeMs = 30_000, endTimeMs = 90_000),
                TranscriptEntry.Speaker("Host"),
            ),
            type = TranscriptType.Vtt,
            url = "https://example.com/transcript.vtt",
            isGenerated = true,
            episodeUuid = EPISODE_UUID,
            podcastUuid = "podcast-uuid",
        )
    }
}
