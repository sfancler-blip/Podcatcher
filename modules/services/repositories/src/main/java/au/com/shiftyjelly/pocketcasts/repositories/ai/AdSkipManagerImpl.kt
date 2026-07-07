package au.com.shiftyjelly.pocketcasts.repositories.ai

import au.com.shiftyjelly.pocketcasts.models.db.dao.AdSegmentDao
import au.com.shiftyjelly.pocketcasts.models.entity.EpisodeAdAnalysis
import au.com.shiftyjelly.pocketcasts.models.entity.EpisodeAdSegment
import au.com.shiftyjelly.pocketcasts.models.to.Transcript
import au.com.shiftyjelly.pocketcasts.models.to.TranscriptEntry
import au.com.shiftyjelly.pocketcasts.preferences.Settings
import au.com.shiftyjelly.pocketcasts.repositories.transcript.TranscriptManager
import au.com.shiftyjelly.pocketcasts.servers.anthropic.AnthropicMessage
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class AdSkipManagerImpl @Inject constructor(
    private val adSegmentDao: AdSegmentDao,
    private val transcriptManager: TranscriptManager,
    private val claudeManager: ClaudeManager,
    private val settings: Settings,
    moshi: Moshi,
) : AdSkipManager {
    private val spansAdapter = moshi.adapter<List<AdSpan>>(
        Types.newParameterizedType(List::class.java, AdSpan::class.java),
    )

    override fun isAdSkippingEnabled(): Boolean = settings.adSkipEnabled.value

    override suspend fun adSegmentsFor(episodeUuid: String): List<EpisodeAdSegment> {
        if (adSegmentDao.analysisForEpisode(episodeUuid) != null) {
            return adSegmentDao.segmentsForEpisode(episodeUuid)
        }
        if (!claudeManager.isConfigured()) return emptyList()

        val transcript = transcriptManager.loadTranscript(episodeUuid) as? Transcript.Text ?: return emptyList()
        val timestamped = transcript.entries
            .filterIsInstance<TranscriptEntry.Text>()
            .filter { it.startTimeMs >= 0 && it.endTimeMs >= it.startTimeMs }
            .joinToString(separator = "\n") { "[${it.startTimeMs}-${it.endTimeMs}] ${it.value}" }
        if (timestamped.isBlank()) return emptyList()

        val reply = try {
            claudeManager.complete(
                system = SYSTEM_PROMPT,
                messages = listOf(
                    AnthropicMessage(
                        role = AnthropicMessage.ROLE_USER,
                        content = timestamped.truncateForClaude(),
                    ),
                ),
                maxTokens = MAX_TOKENS,
            )
        } catch (e: Exception) {
            Timber.e(e, "Ad detection failed for episode $episodeUuid")
            return emptyList()
        }

        // The request was already paid for, so an unparseable reply is cached as "analyzed, no
        // ads" rather than re-billed on every playback.
        val segments = parseSpans(reply)
            .filter { it.endMs > it.startMs && it.startMs >= 0 && it.confidence in 0.0..1.0 }
            .map { span ->
                EpisodeAdSegment(
                    episodeUuid = episodeUuid,
                    startMs = span.startMs,
                    endMs = span.endMs,
                    confidence = span.confidence,
                )
            }
        adSegmentDao.replaceAnalysis(
            analysis = EpisodeAdAnalysis(episodeUuid = episodeUuid, model = ClaudeManager.MODEL),
            segments = segments,
        )
        return adSegmentDao.segmentsForEpisode(episodeUuid)
    }

    private fun parseSpans(reply: String): List<AdSpan> {
        val start = reply.indexOf('[')
        val end = reply.lastIndexOf(']')
        if (start == -1 || end <= start) return emptyList()
        return try {
            spansAdapter.fromJson(reply.substring(start, end + 1)).orEmpty()
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse ad spans from Claude reply")
            emptyList()
        }
    }

    @JsonClass(generateAdapter = true)
    data class AdSpan(
        @Json(name = "startMs") val startMs: Long,
        @Json(name = "endMs") val endMs: Long,
        @Json(name = "confidence") val confidence: Double,
    )

    companion object {
        const val MAX_TOKENS = 2048

        val SYSTEM_PROMPT = """
            You detect advertisements in podcast episodes. The user sends a transcript where each
            line is prefixed with its time range in milliseconds, formatted as [startMs-endMs].
            Identify spans that are advertisements, sponsor reads, or promotional segments
            (including host-read ads and cross-promotions for other shows).

            Respond with ONLY a JSON array, no other text, in this exact shape:
            [{"startMs": 123000, "endMs": 183000, "confidence": 0.9}]

            Use the line timestamps to set startMs and endMs, merge adjacent ad lines into one
            span, and set confidence between 0 and 1. Respond with [] if there are no ads.
        """.trimIndent()
    }
}
