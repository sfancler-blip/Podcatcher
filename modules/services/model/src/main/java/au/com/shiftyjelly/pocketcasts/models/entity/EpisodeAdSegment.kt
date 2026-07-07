package au.com.shiftyjelly.pocketcasts.models.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

// A single ad span detected by Claude in an episode's transcript. No foreign key so that
// segments can be stored for any playable episode regardless of its table of origin.
@Entity(
    tableName = "episode_ad_segments",
    indices = [Index(name = "ad_segment_episode_uuid_index", value = ["episode_uuid"])],
)
data class EpisodeAdSegment(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "_id") val id: Long = 0,
    @ColumnInfo(name = "episode_uuid") val episodeUuid: String,
    @ColumnInfo(name = "start_ms") val startMs: Long,
    @ColumnInfo(name = "end_ms") val endMs: Long,
    @ColumnInfo(name = "confidence") val confidence: Double,
)

// Marker that an episode's transcript has been analyzed for ads, so an episode with no
// detected ads is not re-analyzed (and re-billed) on every playback.
@Entity(tableName = "episode_ad_analysis")
data class EpisodeAdAnalysis(
    @PrimaryKey @ColumnInfo(name = "episode_uuid") val episodeUuid: String,
    @ColumnInfo(name = "model") val model: String,
    @ColumnInfo(name = "created_at") val createdAt: Date = Date(),
)
