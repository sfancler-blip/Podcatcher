package au.com.shiftyjelly.pocketcasts.models.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "episode_summaries")
data class EpisodeSummary(
    @PrimaryKey @ColumnInfo(name = "episode_uuid") val episodeUuid: String,
    @ColumnInfo(name = "podcast_uuid") val podcastUuid: String? = null,
    @ColumnInfo(name = "summary") val summary: String,
    @ColumnInfo(name = "model") val model: String,
    @ColumnInfo(name = "created_at") val createdAt: Date = Date(),
)
