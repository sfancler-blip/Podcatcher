package au.com.shiftyjelly.pocketcasts.repositories.ai

interface EpisodeSummaryManager {
    fun canGenerate(): Boolean

    suspend fun cachedSummary(episodeUuid: String): String?

    // Returns the cached summary if one exists, otherwise generates one from the episode's
    // transcript via Claude and caches it. Throws when no transcript is available or Claude
    // is not configured.
    suspend fun summaryFor(episodeUuid: String): String
}
