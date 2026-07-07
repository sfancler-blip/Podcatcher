package au.com.shiftyjelly.pocketcasts.models.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import au.com.shiftyjelly.pocketcasts.models.entity.EpisodeSummary

@Dao
abstract class EpisodeSummaryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(summary: EpisodeSummary)

    @Query("SELECT * FROM episode_summaries WHERE episode_uuid = :episodeUuid")
    abstract suspend fun findByEpisodeUuid(episodeUuid: String): EpisodeSummary?

    @Query("DELETE FROM episode_summaries WHERE episode_uuid = :episodeUuid")
    abstract suspend fun deleteByEpisodeUuid(episodeUuid: String)
}
