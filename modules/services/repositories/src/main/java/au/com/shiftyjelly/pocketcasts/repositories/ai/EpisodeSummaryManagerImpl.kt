package au.com.shiftyjelly.pocketcasts.repositories.ai

import au.com.shiftyjelly.pocketcasts.models.db.dao.EpisodeSummaryDao
import au.com.shiftyjelly.pocketcasts.models.entity.EpisodeSummary
import au.com.shiftyjelly.pocketcasts.models.to.Transcript
import au.com.shiftyjelly.pocketcasts.repositories.transcript.TranscriptManager
import au.com.shiftyjelly.pocketcasts.servers.anthropic.AnthropicMessage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EpisodeSummaryManagerImpl @Inject constructor(
    private val episodeSummaryDao: EpisodeSummaryDao,
    private val transcriptManager: TranscriptManager,
    private val claudeManager: ClaudeManager,
) : EpisodeSummaryManager {
    override fun canGenerate(): Boolean = claudeManager.isConfigured()

    override suspend fun cachedSummary(episodeUuid: String): String? {
        return episodeSummaryDao.findByEpisodeUuid(episodeUuid)?.summary
    }

    override suspend fun summaryFor(episodeUuid: String): String {
        cachedSummary(episodeUuid)?.let { return it }

        val transcript = checkNotNull(transcriptManager.loadTranscript(episodeUuid) as? Transcript.Text) {
            "A transcript is required to generate an episode summary"
        }
        val transcriptText = checkNotNull(transcript.buildString().takeIf(String::isNotBlank)) {
            "A transcript is required to generate an episode summary"
        }

        val summary = claudeManager.complete(
            system = SYSTEM_PROMPT,
            messages = listOf(
                AnthropicMessage(
                    role = AnthropicMessage.ROLE_USER,
                    content = transcriptText.truncateForClaude(),
                ),
            ),
            maxTokens = MAX_TOKENS,
        )
        episodeSummaryDao.insert(
            EpisodeSummary(
                episodeUuid = episodeUuid,
                podcastUuid = transcript.podcastUuid,
                summary = summary,
                model = ClaudeManager.MODEL,
            ),
        )
        return summary
    }

    companion object {
        const val MAX_TOKENS = 2048

        val SYSTEM_PROMPT = """
            You summarize podcast episodes from their transcripts. Write a concise summary in
            Markdown: one or two short paragraphs describing what the episode covers, followed by
            a bulleted list of the key takeaways. Use only information from the transcript. Do not
            mention the transcript itself or that you are an AI.
        """.trimIndent()
    }
}
