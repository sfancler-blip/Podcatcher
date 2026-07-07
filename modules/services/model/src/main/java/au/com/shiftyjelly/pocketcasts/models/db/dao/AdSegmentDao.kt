package au.com.shiftyjelly.pocketcasts.models.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import au.com.shiftyjelly.pocketcasts.models.entity.EpisodeAdAnalysis
import au.com.shiftyjelly.pocketcasts.models.entity.EpisodeAdSegment

@Dao
abstract class AdSegmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertSegments(segments: List<EpisodeAdSegment>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertAnalysis(analysis: EpisodeAdAnalysis)

    @Query("DELETE FROM episode_ad_segments WHERE episode_uuid = :episodeUuid")
    protected abstract suspend fun deleteSegments(episodeUuid: String)

    @Query("SELECT * FROM episode_ad_segments WHERE episode_uuid = :episodeUuid ORDER BY start_ms ASC")
    abstract suspend fun segmentsForEpisode(episodeUuid: String): List<EpisodeAdSegment>

    @Query("SELECT * FROM episode_ad_analysis WHERE episode_uuid = :episodeUuid")
    abstract suspend fun analysisForEpisode(episodeUuid: String): EpisodeAdAnalysis?

    @Transaction
    open suspend fun replaceAnalysis(analysis: EpisodeAdAnalysis, segments: List<EpisodeAdSegment>) {
        deleteSegments(analysis.episodeUuid)
        insertSegments(segments)
        insertAnalysis(analysis)
    }
}
