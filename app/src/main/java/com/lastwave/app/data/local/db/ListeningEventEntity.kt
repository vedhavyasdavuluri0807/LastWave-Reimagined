package com.lastwave.app.data.local.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Represents an individual, local playback event recorded when a track is listened to.
 *
 * All data is kept 100% on-device in Room without network transmission.
 * Keyed by trackKey ("$title|$artist", lowercased) for consistency across the app.
 */
@Entity(
    tableName = "listening_events",
    indices = [
        Index(value = ["timestampMillis"]),
        Index(value = ["trackKey"]),
    ],
)
data class ListeningEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val trackKey: String,
    val title: String,
    val artist: String,
    val videoId: String? = null,
    val artworkUrl: String? = null,
    val durationMs: Long = 0L,
    val completed: Boolean = false,
    val timestampMillis: Long = System.currentTimeMillis(),
)

data class TrackListeningStat(
    val trackKey: String,
    val title: String,
    val artist: String,
    val videoId: String?,
    val artworkUrl: String?,
    val totalDurationMs: Long,
    val playCount: Int,
)

data class ArtistListeningStat(
    val artist: String,
    val totalDurationMs: Long,
    val playCount: Int,
)

@Dao
interface ListeningEventDao {
    @Insert
    suspend fun insert(event: ListeningEventEntity): Long

    @Query("SELECT * FROM listening_events WHERE timestampMillis >= :sinceMillis ORDER BY timestampMillis DESC")
    fun observeEventsSince(sinceMillis: Long): Flow<List<ListeningEventEntity>>

    @Query("SELECT * FROM listening_events WHERE timestampMillis >= :sinceMillis ORDER BY timestampMillis DESC")
    suspend fun getEventsSince(sinceMillis: Long): List<ListeningEventEntity>

    @Query("SELECT * FROM listening_events WHERE timestampMillis >= :startMillis AND timestampMillis < :endMillis ORDER BY timestampMillis ASC")
    suspend fun getEventsBetween(startMillis: Long, endMillis: Long): List<ListeningEventEntity>

    @Query("SELECT * FROM listening_events ORDER BY timestampMillis DESC LIMIT :limit")
    fun observeRecentEvents(limit: Int): Flow<List<ListeningEventEntity>>

    @Query("SELECT * FROM listening_events ORDER BY timestampMillis DESC LIMIT :limit")
    suspend fun getRecentEvents(limit: Int): List<ListeningEventEntity>

    @Query("SELECT COUNT(*) FROM listening_events WHERE timestampMillis >= :sinceMillis")
    suspend fun getPlayCountSince(sinceMillis: Long): Int

    @Query("SELECT COALESCE(SUM(durationMs), 0) FROM listening_events WHERE timestampMillis >= :sinceMillis")
    suspend fun getTotalDurationMsSince(sinceMillis: Long): Long

    @Query("SELECT COUNT(DISTINCT trackKey) FROM listening_events WHERE timestampMillis >= :sinceMillis")
    suspend fun getUniqueTracksCountSince(sinceMillis: Long): Int

    @Query("SELECT COUNT(DISTINCT LOWER(TRIM(artist))) FROM listening_events WHERE timestampMillis >= :sinceMillis")
    suspend fun getUniqueArtistsCountSince(sinceMillis: Long): Int

    @Query("""
        SELECT trackKey, title, artist, videoId, artworkUrl,
               SUM(durationMs) AS totalDurationMs,
               COUNT(*) AS playCount
        FROM listening_events
        WHERE timestampMillis >= :sinceMillis
        GROUP BY trackKey
        ORDER BY totalDurationMs DESC
        LIMIT :limit
    """)
    suspend fun getTopTracksSince(sinceMillis: Long, limit: Int): List<TrackListeningStat>

    @Query("""
        SELECT artist,
               SUM(durationMs) AS totalDurationMs,
               COUNT(*) AS playCount
        FROM listening_events
        WHERE timestampMillis >= :sinceMillis
        GROUP BY LOWER(TRIM(artist))
        ORDER BY totalDurationMs DESC
        LIMIT :limit
    """)
    suspend fun getTopArtistsSince(sinceMillis: Long, limit: Int): List<ArtistListeningStat>

    @Query("DELETE FROM listening_events")
    suspend fun clearAll()
}
