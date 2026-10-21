package com.lastwave.app.data.repository

import com.lastwave.app.data.local.db.ListeningEventDao
import com.lastwave.app.data.local.db.ListeningEventEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

enum class InsightsTimeRange {
    TODAY,
    LAST_7_DAYS,
    LAST_30_DAYS,
}

data class TimeWindow(
    val startMillis: Long,
    val endMillis: Long,
)

data class ActivityChartBucket(
    val key: String,
    val label: String,
    val durationMs: Long,
    val isToday: Boolean = false,
)

data class TrackInsightItem(
    val trackKey: String,
    val title: String,
    val artist: String,
    val videoId: String?,
    val artworkUrl: String?,
    val durationMs: Long,
    val playCount: Int,
    val completedCount: Int,
)

data class ArtistInsightItem(
    val artist: String,
    val durationMs: Long,
    val playCount: Int,
)

data class RecentHistoryItem(
    val id: Long,
    val title: String,
    val artist: String,
    val videoId: String?,
    val artworkUrl: String?,
    val durationMs: Long,
    val timestampMillis: Long,
    val completed: Boolean,
)

data class ListeningInsightsData(
    val timeRange: InsightsTimeRange,
    val totalDurationMs: Long = 0L,
    val totalPlays: Int = 0,
    val uniqueTracksCount: Int = 0,
    val uniqueArtistsCount: Int = 0,
    val chartBuckets: List<ActivityChartBucket> = emptyList(),
    val topTracks: List<TrackInsightItem> = emptyList(),
    val topArtists: List<ArtistInsightItem> = emptyList(),
    val recentHistory: List<RecentHistoryItem> = emptyList(),
    val hasAnyHistory: Boolean = false,
)

@Singleton
class ListeningInsightsRepository @Inject constructor(
    private val dao: ListeningEventDao,
) {
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default

    /**
     * Calculates the local-time calendar window for the specified filter.
     * Uses the system's local timezone so day boundaries (midnight to midnight)
     * strictly match the user's real calendar days.
     */
    fun getTimeWindow(
        range: InsightsTimeRange,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): TimeWindow {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        val startLocalDate = when (range) {
            InsightsTimeRange.TODAY -> today
            InsightsTimeRange.LAST_7_DAYS -> today.minusDays(6)
            InsightsTimeRange.LAST_30_DAYS -> today.minusDays(29)
        }
        val startMillis = startLocalDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endMillis = today.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return TimeWindow(startMillis = startMillis, endMillis = endMillis)
    }

    /**
     * Observes real-time listening insights for the specified time range.
     */
    fun observeInsights(
        range: InsightsTimeRange,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Flow<ListeningInsightsData> {
        val window = getTimeWindow(range = range, zoneId = zoneId)
        return dao.observeEventsSince(window.startMillis)
            .map { allEventsSince ->
                // Filter events that fall strictly within [startMillis, endMillis)
                val windowEvents = allEventsSince.filter {
                    it.timestampMillis >= window.startMillis && it.timestampMillis < window.endMillis
                }
                aggregateInsights(
                    range = range,
                    window = window,
                    events = windowEvents,
                    allRecentEvents = allEventsSince.take(50),
                    zoneId = zoneId,
                )
            }
            .flowOn(defaultDispatcher)
    }

    /**
     * Aggregates raw listening events into UI-ready metrics and chart data.
     */
    internal fun aggregateInsights(
        range: InsightsTimeRange,
        window: TimeWindow,
        events: List<ListeningEventEntity>,
        allRecentEvents: List<ListeningEventEntity>,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): ListeningInsightsData {
        val totalDurationMs = events.sumOf { it.durationMs }
        val totalPlays = events.size
        val uniqueTracksCount = events.map { it.trackKey }.distinct().size
        val uniqueArtistsCount = events.map { it.artist.trim().lowercase() }
            .filter { it.isNotBlank() }
            .distinct()
            .size

        val chartBuckets = computeChartBuckets(range, window, events, zoneId)
        val topTracks = computeTopTracks(events)
        val topArtists = computeTopArtists(events)
        val recentHistory = allRecentEvents.map { entity ->
            RecentHistoryItem(
                id = entity.id,
                title = entity.title,
                artist = entity.artist,
                videoId = entity.videoId,
                artworkUrl = entity.artworkUrl,
                durationMs = entity.durationMs,
                timestampMillis = entity.timestampMillis,
                completed = entity.completed,
            )
        }

        return ListeningInsightsData(
            timeRange = range,
            totalDurationMs = totalDurationMs,
            totalPlays = totalPlays,
            uniqueTracksCount = uniqueTracksCount,
            uniqueArtistsCount = uniqueArtistsCount,
            chartBuckets = chartBuckets,
            topTracks = topTracks,
            topArtists = topArtists,
            recentHistory = recentHistory,
            hasAnyHistory = allRecentEvents.isNotEmpty(),
        )
    }

    private fun computeChartBuckets(
        range: InsightsTimeRange,
        window: TimeWindow,
        events: List<ListeningEventEntity>,
        zoneId: ZoneId,
    ): List<ActivityChartBucket> {
        val today = LocalDate.now(zoneId)

        return when (range) {
            InsightsTimeRange.TODAY -> {
                // 8 three-hour buckets across the 24 hours of Today:
                // 12am, 3am, 6am, 9am, 12pm, 3pm, 6pm, 9pm
                val hoursList = listOf(0, 3, 6, 9, 12, 15, 18, 21)
                val labels = listOf("12a", "3a", "6a", "9a", "12p", "3p", "6p", "9p")
                val durations = LongArray(8)

                for (event in events) {
                    val zdt = Instant.ofEpochMilli(event.timestampMillis).atZone(zoneId)
                    val hour = zdt.hour
                    val bucketIndex = (hour / 3).coerceIn(0, 7)
                    durations[bucketIndex] += event.durationMs
                }

                hoursList.indices.map { i ->
                    ActivityChartBucket(
                        key = "today_${hoursList[i]}",
                        label = labels[i],
                        durationMs = durations[i],
                        isToday = true,
                    )
                }
            }
            InsightsTimeRange.LAST_7_DAYS -> {
                // 7 calendar days leading to today
                val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
                val durationsByDate = mutableMapOf<LocalDate, Long>()
                for (event in events) {
                    val eventDate = Instant.ofEpochMilli(event.timestampMillis).atZone(zoneId).toLocalDate()
                    durationsByDate[eventDate] = (durationsByDate[eventDate] ?: 0L) + event.durationMs
                }

                days.map { date ->
                    val isToday = date == today
                    val dayLabel = if (isToday) "Today" else date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                    ActivityChartBucket(
                        key = date.toString(),
                        label = dayLabel,
                        durationMs = durationsByDate[date] ?: 0L,
                        isToday = isToday,
                    )
                }
            }
            InsightsTimeRange.LAST_30_DAYS -> {
                // 30 calendar days
                val days = (29 downTo 0).map { today.minusDays(it.toLong()) }
                val durationsByDate = mutableMapOf<LocalDate, Long>()
                val dateFormatter = DateTimeFormatter.ofPattern("M/d")

                for (event in events) {
                    val eventDate = Instant.ofEpochMilli(event.timestampMillis).atZone(zoneId).toLocalDate()
                    durationsByDate[eventDate] = (durationsByDate[eventDate] ?: 0L) + event.durationMs
                }

                days.mapIndexed { index, date ->
                    val isToday = date == today
                    // Show date label on every 5th item or today to prevent crowding
                    val showLabel = index % 5 == 0 || isToday || index == 0
                    val label = if (showLabel) {
                        if (isToday) "Today" else date.format(dateFormatter)
                    } else ""
                    ActivityChartBucket(
                        key = date.toString(),
                        label = label,
                        durationMs = durationsByDate[date] ?: 0L,
                        isToday = isToday,
                    )
                }
            }
        }
    }

    private fun computeTopTracks(events: List<ListeningEventEntity>): List<TrackInsightItem> {
        return events.groupBy { it.trackKey }
            .values
            .map { trackEvents ->
                val first = trackEvents.first()
                TrackInsightItem(
                    trackKey = first.trackKey,
                    title = first.title,
                    artist = first.artist,
                    videoId = trackEvents.firstNotNullOfOrNull { it.videoId },
                    artworkUrl = trackEvents.firstNotNullOfOrNull { it.artworkUrl },
                    durationMs = trackEvents.sumOf { it.durationMs },
                    playCount = trackEvents.size,
                    completedCount = trackEvents.count { it.completed },
                )
            }
            .sortedByDescending { it.durationMs }
            .take(15)
    }

    private fun computeTopArtists(events: List<ListeningEventEntity>): List<ArtistInsightItem> {
        return events.groupBy { it.artist.trim().lowercase() }
            .filterKeys { it.isNotBlank() }
            .values
            .map { artistEvents ->
                val displayArtist = artistEvents.first().artist.trim()
                ArtistInsightItem(
                    artist = displayArtist,
                    durationMs = artistEvents.sumOf { it.durationMs },
                    playCount = artistEvents.size,
                )
            }
            .sortedByDescending { it.durationMs }
            .take(15)
    }
}
