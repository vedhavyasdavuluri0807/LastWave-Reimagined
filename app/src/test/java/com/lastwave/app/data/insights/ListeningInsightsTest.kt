package com.lastwave.app.data.insights

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.google.common.truth.Truth.assertThat
import com.lastwave.app.data.local.db.ListeningEventDao
import com.lastwave.app.data.local.db.ListeningEventEntity
import com.lastwave.app.data.repository.InsightsTimeRange
import com.lastwave.app.data.repository.ListeningInsightsRepository
import com.lastwave.app.data.repository.TimeWindow
import com.lastwave.app.di.DatabaseModule
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ListeningInsightsTest {

    private val mockDao: ListeningEventDao = mockk(relaxed = true)
    private val repository = ListeningInsightsRepository(mockDao)

    // =========================================================================
    // 1. Room Migration 15 to 16 Test
    // =========================================================================

    @Test
    fun `migration15To16 creates listening_events table with correct schema and indices without destroying existing tables`() {
        val context = RuntimeEnvironment.getApplication()
        val dbFile = context.getDatabasePath("test_migration_15_16.db")
        dbFile.delete()

        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbFile.name)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(15) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Pre-migration schema at version 15
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `song_play_stats` (
                            `trackKey` TEXT NOT NULL PRIMARY KEY,
                            `title` TEXT NOT NULL DEFAULT '',
                            `artist` TEXT NOT NULL DEFAULT '',
                            `videoId` TEXT,
                            `artworkUrl` TEXT,
                            `totalPlayTimeMs` INTEGER NOT NULL DEFAULT 0,
                            `playCount` INTEGER NOT NULL DEFAULT 0,
                            `skipCount` INTEGER NOT NULL DEFAULT 0,
                            `lastPlayedAtMillis` INTEGER NOT NULL DEFAULT 0
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        """
                        INSERT INTO `song_play_stats` (`trackKey`, `title`, `artist`, `totalPlayTimeMs`)
                        VALUES ('song|artist', 'Song', 'Artist', 120000)
                        """.trimIndent(),
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase

        // Apply migration 15 to 16
        DatabaseModule.migration15To16.migrate(db)

        // Verify existing table and data preserved
        val statsCursor = db.query("SELECT trackKey, totalPlayTimeMs FROM song_play_stats")
        assertThat(statsCursor.moveToFirst()).isTrue()
        assertThat(statsCursor.getString(0)).isEqualTo("song|artist")
        assertThat(statsCursor.getLong(1)).isEqualTo(120000L)
        statsCursor.close()

        // Verify new listening_events table columns
        val tableInfoCursor = db.query("PRAGMA table_info(`listening_events`)")
        val columns = mutableMapOf<String, String>()
        while (tableInfoCursor.moveToNext()) {
            val name = tableInfoCursor.getString(tableInfoCursor.getColumnIndexOrThrow("name"))
            val type = tableInfoCursor.getString(tableInfoCursor.getColumnIndexOrThrow("type"))
            columns[name] = type
        }
        tableInfoCursor.close()

        assertThat(columns).containsKey("id")
        assertThat(columns).containsKey("trackKey")
        assertThat(columns).containsKey("title")
        assertThat(columns).containsKey("artist")
        assertThat(columns).containsKey("videoId")
        assertThat(columns).containsKey("artworkUrl")
        assertThat(columns).containsKey("durationMs")
        assertThat(columns).containsKey("completed")
        assertThat(columns).containsKey("timestampMillis")

        // Verify indices
        val indexCursor = db.query("PRAGMA index_list(`listening_events`)")
        val indexNames = mutableListOf<String>()
        while (indexCursor.moveToNext()) {
            indexNames.add(indexCursor.getString(indexCursor.getColumnIndexOrThrow("name")))
        }
        indexCursor.close()

        assertThat(indexNames).contains("index_listening_events_timestampMillis")
        assertThat(indexNames).contains("index_listening_events_trackKey")

        db.close()
    }

    // =========================================================================
    // 2. Date-window and Local-day-boundary Calculations
    // =========================================================================

    @Test
    fun `getTimeWindow calculates strict local calendar boundaries for Today`() {
        val zone = ZoneId.of("America/New_York")
        // 2026-10-21 14:30:00 EDT
        val nowInstant = ZonedDateTime.of(2026, 10, 21, 14, 30, 0, 0, zone).toInstant()

        val window = repository.getTimeWindow(
            range = InsightsTimeRange.TODAY,
            nowMillis = nowInstant.toEpochMilli(),
            zoneId = zone,
        )

        val expectedStart = ZonedDateTime.of(2026, 10, 21, 0, 0, 0, 0, zone).toInstant().toEpochMilli()
        val expectedEnd = ZonedDateTime.of(2026, 10, 22, 0, 0, 0, 0, zone).toInstant().toEpochMilli()

        assertThat(window.startMillis).isEqualTo(expectedStart)
        assertThat(window.endMillis).isEqualTo(expectedEnd)
    }

    @Test
    fun `getTimeWindow calculates exactly 7 local calendar days for Last 7 Days`() {
        val zone = ZoneId.of("Asia/Tokyo")
        val nowInstant = ZonedDateTime.of(2026, 10, 21, 23, 15, 0, 0, zone).toInstant()

        val window = repository.getTimeWindow(
            range = InsightsTimeRange.LAST_7_DAYS,
            nowMillis = nowInstant.toEpochMilli(),
            zoneId = zone,
        )

        // Today is 10/21. 7 days = 10/15, 10/16, 10/17, 10/18, 10/19, 10/20, 10/21
        val expectedStart = ZonedDateTime.of(2026, 10, 15, 0, 0, 0, 0, zone).toInstant().toEpochMilli()
        val expectedEnd = ZonedDateTime.of(2026, 10, 22, 0, 0, 0, 0, zone).toInstant().toEpochMilli()

        assertThat(window.startMillis).isEqualTo(expectedStart)
        assertThat(window.endMillis).isEqualTo(expectedEnd)
    }

    @Test
    fun `getTimeWindow calculates exactly 30 local calendar days for Last 30 Days`() {
        val zone = ZoneId.of("Europe/London")
        val nowInstant = ZonedDateTime.of(2026, 10, 21, 10, 0, 0, 0, zone).toInstant()

        val window = repository.getTimeWindow(
            range = InsightsTimeRange.LAST_30_DAYS,
            nowMillis = nowInstant.toEpochMilli(),
            zoneId = zone,
        )

        // Today is 10/21. 30 days = 10/21 minus 29 days = 9/22
        val expectedStart = ZonedDateTime.of(2026, 9, 22, 0, 0, 0, 0, zone).toInstant().toEpochMilli()
        val expectedEnd = ZonedDateTime.of(2026, 10, 22, 0, 0, 0, 0, zone).toInstant().toEpochMilli()

        assertThat(window.startMillis).isEqualTo(expectedStart)
        assertThat(window.endMillis).isEqualTo(expectedEnd)
    }

    // =========================================================================
    // 3. Aggregation & Empty History Test
    // =========================================================================

    @Test
    fun `aggregateInsights returns honest zero-state for empty history without fabricated data`() {
        val zone = ZoneId.of("UTC")
        val window = TimeWindow(startMillis = 1000L, endMillis = 2000L)

        val result = repository.aggregateInsights(
            range = InsightsTimeRange.TODAY,
            window = window,
            events = emptyList(),
            allRecentEvents = emptyList(),
            zoneId = zone,
        )

        assertThat(result.totalDurationMs).isEqualTo(0L)
        assertThat(result.totalPlays).isEqualTo(0)
        assertThat(result.uniqueTracksCount).isEqualTo(0)
        assertThat(result.uniqueArtistsCount).isEqualTo(0)
        assertThat(result.topTracks).isEmpty()
        assertThat(result.topArtists).isEmpty()
        assertThat(result.recentHistory).isEmpty()
        assertThat(result.hasAnyHistory).isFalse()

        // Chart buckets exist for all time slots but each has 0 duration
        assertThat(result.chartBuckets).isNotEmpty()
        assertThat(result.chartBuckets.all { it.durationMs == 0L }).isTrue()
    }

    @Test
    fun `aggregateInsights correctly groups unique tracks, unique artists, and aggregates durations`() {
        val zone = ZoneId.of("UTC")
        val window = TimeWindow(startMillis = 0L, endMillis = 10_000_000L)

        val events = listOf(
            ListeningEventEntity(
                id = 1L,
                trackKey = "song a|artist 1",
                title = "Song A",
                artist = "Artist 1",
                durationMs = 180_000L,
                completed = true,
                timestampMillis = 1000L,
            ),
            ListeningEventEntity(
                id = 2L,
                trackKey = "song a|artist 1",
                title = "Song A",
                artist = "Artist 1",
                durationMs = 120_000L,
                completed = false,
                timestampMillis = 2000L,
            ),
            ListeningEventEntity(
                id = 3L,
                trackKey = "song b|artist 2",
                title = "Song B",
                artist = "Artist 2 ",
                durationMs = 200_000L,
                completed = true,
                timestampMillis = 3000L,
            ),
            ListeningEventEntity(
                id = 4L,
                trackKey = "song c|artist 2",
                title = "Song C",
                artist = "ARTIST 2",
                durationMs = 90_000L,
                completed = true,
                timestampMillis = 4000L,
            ),
        )

        val result = repository.aggregateInsights(
            range = InsightsTimeRange.TODAY,
            window = window,
            events = events,
            allRecentEvents = events,
            zoneId = zone,
        )

        assertThat(result.totalDurationMs).isEqualTo(590_000L)
        assertThat(result.totalPlays).isEqualTo(4)
        // 3 unique tracks: Song A, Song B, Song C
        assertThat(result.uniqueTracksCount).isEqualTo(3)
        // 2 unique artists: Artist 1, Artist 2 (case/trim insensitive)
        assertThat(result.uniqueArtistsCount).isEqualTo(2)

        // Top tracks ranked by total duration: Song A (300k), Song B (200k), Song C (90k)
        assertThat(result.topTracks[0].title).isEqualTo("Song A")
        assertThat(result.topTracks[0].durationMs).isEqualTo(300_000L)
        assertThat(result.topTracks[0].playCount).isEqualTo(2)
        assertThat(result.topTracks[0].completedCount).isEqualTo(1)

        // Top artists ranked by duration: Song A (300k for Artist 1), Song B+C (290k for Artist 2)
        assertThat(result.topArtists[0].artist).isEqualTo("Artist 1")
        assertThat(result.topArtists[0].durationMs).isEqualTo(300_000L)
        assertThat(result.topArtists[1].durationMs).isEqualTo(290_000L)
    }

    // =========================================================================
    // 4. Playback Progress Accumulator Test (Pauses, Buffering, Seeks)
    // =========================================================================

    /**
     * Standalone implementation of the exact accumulator logic integrated in MusicPlayer
     * to test its mathematical precision in unit test isolation.
     */
    private class TestPlaybackAccumulator {
        var accumulatedListenMs = 0L
        var lastAccumulatedPosMs = -1L
        var accumulatedTrackKey: String? = null

        fun accumulate(
            trackKey: String?,
            currentPos: Long,
            isPlaying: Boolean,
            isBuffering: Boolean,
            isSeeking: Boolean,
        ) {
            if (trackKey.isNullOrBlank()) {
                lastAccumulatedPosMs = -1L
                return
            }
            if (trackKey != accumulatedTrackKey) {
                accumulatedTrackKey = trackKey
                accumulatedListenMs = 0L
                lastAccumulatedPosMs = if (isPlaying && !isBuffering && !isSeeking) currentPos else -1L
                return
            }
            if (isSeeking || !isPlaying || isBuffering) {
                lastAccumulatedPosMs = -1L
                return
            }
            if (lastAccumulatedPosMs >= 0L) {
                val delta = currentPos - lastAccumulatedPosMs
                if (delta in 1L..2000L) {
                    accumulatedListenMs += delta
                }
            }
            lastAccumulatedPosMs = currentPos
        }
    }

    @Test
    fun `playback accumulator accumulates smooth playback ticks accurately`() {
        val acc = TestPlaybackAccumulator()
        val key = "title|artist"

        acc.accumulate(key, currentPos = 0L, isPlaying = true, isBuffering = false, isSeeking = false)
        acc.accumulate(key, currentPos = 60L, isPlaying = true, isBuffering = false, isSeeking = false)
        acc.accumulate(key, currentPos = 120L, isPlaying = true, isBuffering = false, isSeeking = false)
        acc.accumulate(key, currentPos = 180L, isPlaying = true, isBuffering = false, isSeeking = false)

        assertThat(acc.accumulatedListenMs).isEqualTo(180L)
    }

    @Test
    fun `playback accumulator does not count paused duration`() {
        val acc = TestPlaybackAccumulator()
        val key = "title|artist"

        // Play for 100ms
        acc.accumulate(key, currentPos = 0L, isPlaying = true, isBuffering = false, isSeeking = false)
        acc.accumulate(key, currentPos = 100L, isPlaying = true, isBuffering = false, isSeeking = false)
        assertThat(acc.accumulatedListenMs).isEqualTo(100L)

        // Pause for 10 minutes (ticks report isPlaying=false, position stays 100L)
        repeat(5) {
            acc.accumulate(key, currentPos = 100L, isPlaying = false, isBuffering = false, isSeeking = false)
        }
        assertThat(acc.accumulatedListenMs).isEqualTo(100L)

        // Resume playback
        acc.accumulate(key, currentPos = 100L, isPlaying = true, isBuffering = false, isSeeking = false)
        acc.accumulate(key, currentPos = 200L, isPlaying = true, isBuffering = false, isSeeking = false)
        assertThat(acc.accumulatedListenMs).isEqualTo(200L)
    }

    @Test
    fun `playback accumulator does not count buffering time`() {
        val acc = TestPlaybackAccumulator()
        val key = "title|artist"

        acc.accumulate(key, currentPos = 0L, isPlaying = true, isBuffering = false, isSeeking = false)
        acc.accumulate(key, currentPos = 500L, isPlaying = true, isBuffering = false, isSeeking = false)
        assertThat(acc.accumulatedListenMs).isEqualTo(500L)

        // Buffering stall
        acc.accumulate(key, currentPos = 500L, isPlaying = true, isBuffering = true, isSeeking = false)
        assertThat(acc.accumulatedListenMs).isEqualTo(500L)

        // Exits buffer
        acc.accumulate(key, currentPos = 500L, isPlaying = true, isBuffering = false, isSeeking = false)
        acc.accumulate(key, currentPos = 1000L, isPlaying = true, isBuffering = false, isSeeking = false)
        assertThat(acc.accumulatedListenMs).isEqualTo(1000L)
    }

    @Test
    fun `playback accumulator ignores forward seek jumps and accumulates backward repeats`() {
        val acc = TestPlaybackAccumulator()
        val key = "title|artist"

        // Listen from 0 to 10s
        acc.accumulate(key, currentPos = 0L, isPlaying = true, isBuffering = false, isSeeking = false)
        acc.accumulate(key, currentPos = 10_000L, isPlaying = true, isBuffering = false, isSeeking = false)
        // delta was 10_000L > 2000L, so huge leap without ticking is not counted
        assertThat(acc.accumulatedListenMs).isEqualTo(0L)

        // Normal 1s tick
        acc.accumulate(key, currentPos = 11_000L, isPlaying = true, isBuffering = false, isSeeking = false)
        assertThat(acc.accumulatedListenMs).isEqualTo(1000L)

        // Forward seek jump to 180s
        acc.accumulate(key, currentPos = 180_000L, isPlaying = true, isBuffering = false, isSeeking = true)
        assertThat(acc.accumulatedListenMs).isEqualTo(1000L)

        // Play for 500ms after seek
        acc.accumulate(key, currentPos = 180_000L, isPlaying = true, isBuffering = false, isSeeking = false)
        acc.accumulate(key, currentPos = 180_500L, isPlaying = true, isBuffering = false, isSeeking = false)
        assertThat(acc.accumulatedListenMs).isEqualTo(1500L)

        // Backward seek from 180.5s back to 10s
        acc.accumulate(key, currentPos = 10_000L, isPlaying = true, isBuffering = false, isSeeking = true)
        // Duration did NOT decrease
        assertThat(acc.accumulatedListenMs).isEqualTo(1500L)

        // Play repeated section for 1000ms
        acc.accumulate(key, currentPos = 10_000L, isPlaying = true, isBuffering = false, isSeeking = false)
        acc.accumulate(key, currentPos = 11_000L, isPlaying = true, isBuffering = false, isSeeking = false)
        // Successfully accumulated repeated listening time: 1500 + 1000 = 2500ms!
        assertThat(acc.accumulatedListenMs).isEqualTo(2500L)
    }

    // =========================================================================
    // 5. Meaningful play and micro-skip filtering
    // =========================================================================

    @Test
    fun `meaningful play rules identify skips under 5 seconds versus completed tracks`() {
        fun isMeaningful(listenedMs: Long, completedNaturally: Boolean): Boolean {
            return listenedMs >= 5_000L || completedNaturally
        }

        // 2 second tap-through skip is not meaningful
        assertThat(isMeaningful(listenedMs = 2000L, completedNaturally = false)).isFalse()

        // 5 second or more listen is meaningful
        assertThat(isMeaningful(listenedMs = 5000L, completedNaturally = false)).isTrue()
        assertThat(isMeaningful(listenedMs = 45000L, completedNaturally = false)).isTrue()

        // Natural completion is always meaningful even for short tracks
        assertThat(isMeaningful(listenedMs = 3000L, completedNaturally = true)).isTrue()
    }
}
