package au.com.shiftyjelly.pocketcasts.repositories.ai

import au.com.shiftyjelly.pocketcasts.models.entity.EpisodeAdSegment

interface AdSkipManager {
    fun isAdSkippingEnabled(): Boolean

    // Returns the detected ad segments for an episode. Uses the cached analysis when present;
    // otherwise runs Claude detection over the episode's timestamped transcript and caches the
    // result. Returns an empty list when detection isn't possible (no key, no transcript, no
    // timings) — never throws, because it runs inside the playback pipeline.
    suspend fun adSegmentsFor(episodeUuid: String): List<EpisodeAdSegment>

    companion object {
        // Segments below this confidence are stored but not auto-skipped.
        const val CONFIDENCE_THRESHOLD = 0.7
    }
}
