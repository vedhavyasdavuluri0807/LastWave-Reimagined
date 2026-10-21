package com.lastwave.app.ui.insights

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lastwave.app.data.repository.ActivityChartBucket
import com.lastwave.app.data.repository.ArtistInsightItem
import com.lastwave.app.data.repository.InsightsTimeRange
import com.lastwave.app.data.repository.ListeningInsightsData
import com.lastwave.app.data.repository.RecentHistoryItem
import com.lastwave.app.data.repository.TrackInsightItem
import com.lastwave.app.ui.common.ArtworkImage
import com.lastwave.app.ui.common.ExpressiveHeader
import com.lastwave.app.ui.common.ExpressiveLoadingIndicator
import com.lastwave.app.ui.common.adaptiveContentWidth
import com.lastwave.app.ui.player.LocalMusicPlayer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ListeningInsightsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenArtist: ((String) -> Unit)? = null,
    viewModel: ListeningInsightsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val musicPlayer = LocalMusicPlayer.current

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ExpressiveHeader(
                    title = "Listening Insights",
                    subtitle = "Your listening patterns & habits",
                    onBack = onBack,
                    modifier = Modifier.adaptiveContentWidth(maxWidth = 860.dp),
                )
            }
        },
        modifier = modifier.fillMaxSize(),
    ) { scaffoldPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            when {
                uiState.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ExpressiveLoadingIndicator(message = "Analyzing listening data…")
                    }
                }
                uiState.errorMessage != null -> {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = uiState.errorMessage ?: "Unknown error",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                uiState.insights != null -> {
                    InsightsContent(
                        data = uiState.insights!!,
                        selectedRange = uiState.selectedRange,
                        onRangeSelected = viewModel::setTimeRange,
                        onPlayTrack = { track -> viewModel.playTrack(musicPlayer, track) },
                        onPlayRecent = { recent -> viewModel.playRecent(musicPlayer, recent) },
                        onOpenArtist = onOpenArtist,
                        modifier = Modifier
                            .fillMaxSize()
                            .adaptiveContentWidth(maxWidth = 860.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun InsightsContent(
    data: ListeningInsightsData,
    selectedRange: InsightsTimeRange,
    onRangeSelected: (InsightsTimeRange) -> Unit,
    onPlayTrack: (TrackInsightItem) -> Unit,
    onPlayRecent: (RecentHistoryItem) -> Unit,
    onOpenArtist: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 1. Time Range Filter Chips
        item(key = "filter_chips") {
            RangeFilterRow(
                selectedRange = selectedRange,
                onRangeSelected = onRangeSelected,
            )
        }

        // 2. Summary Metric Cards
        item(key = "metric_cards") {
            SummaryMetricsGrid(
                totalDurationMs = data.totalDurationMs,
                totalPlays = data.totalPlays,
                uniqueTracks = data.uniqueTracksCount,
                uniqueArtists = data.uniqueArtistsCount,
            )
        }

        // 3. Listening Activity Bar Chart
        item(key = "activity_chart") {
            ActivityChartCard(
                buckets = data.chartBuckets,
                range = selectedRange,
            )
        }

        // 4. Top Tracks Section
        item(key = "top_tracks_header") {
            SectionHeader(
                title = "Top Tracks",
                subtitle = "Most listened during this period",
                icon = Icons.Filled.MusicNote,
            )
        }

        if (data.topTracks.isEmpty()) {
            item(key = "top_tracks_empty") {
                EmptyStateCard(
                    message = "No tracks played in this period.",
                )
            }
        } else {
            items(data.topTracks, key = { "track_${it.trackKey}" }) { track ->
                TrackInsightRow(
                    track = track,
                    onPlay = { onPlayTrack(track) },
                )
            }
        }

        // 5. Top Artists Section
        item(key = "top_artists_header") {
            SectionHeader(
                title = "Top Artists",
                subtitle = "Artists you spent the most time with",
                icon = Icons.Filled.Person,
            )
        }

        if (data.topArtists.isEmpty()) {
            item(key = "top_artists_empty") {
                EmptyStateCard(
                    message = "No artist listening recorded yet.",
                )
            }
        } else {
            items(data.topArtists, key = { "artist_${it.artist}" }) { artist ->
                ArtistInsightRow(
                    artist = artist,
                    onClick = { onOpenArtist?.invoke(artist.artist) },
                )
            }
        }

        // 6. Recent History Section
        item(key = "recent_history_header") {
            SectionHeader(
                title = "Recent Listening History",
                subtitle = "Playback events recorded on this device",
                icon = Icons.Filled.Schedule,
            )
        }

        if (data.recentHistory.isEmpty()) {
            item(key = "recent_history_empty") {
                EmptyStateCard(
                    message = "Your recent listening history will appear here once tracks play.",
                )
            }
        } else {
            items(data.recentHistory, key = { "history_${it.id}" }) { recent ->
                RecentHistoryRow(
                    item = recent,
                    onPlay = { onPlayRecent(recent) },
                )
            }
        }

        item {
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun RangeFilterRow(
    selectedRange: InsightsTimeRange,
    onRangeSelected: (InsightsTimeRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        InsightsTimeRange.entries.forEach { range ->
            val label = when (range) {
                InsightsTimeRange.TODAY -> "Today"
                InsightsTimeRange.LAST_7_DAYS -> "Last 7 Days"
                InsightsTimeRange.LAST_30_DAYS -> "Last 30 Days"
            }
            val selected = selectedRange == range
            FilterChip(
                selected = selected,
                onClick = { onRangeSelected(range) },
                label = {
                    Text(
                        text = label,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                shape = RoundedCornerShape(12.dp),
            )
        }
    }
}

@Composable
private fun SummaryMetricsGrid(
    totalDurationMs: Long,
    totalPlays: Int,
    uniqueTracks: Int,
    uniqueArtists: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard(
                title = "Listening Time",
                value = formatDurationHuman(totalDurationMs),
                icon = Icons.Filled.Headphones,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                title = "Tracks Played",
                value = "$totalPlays",
                icon = Icons.Filled.PlayArrow,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f),
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard(
                title = "Unique Tracks",
                value = "$uniqueTracks",
                icon = Icons.Filled.GraphicEq,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                title = "Unique Artists",
                value = "$uniqueArtists",
                icon = Icons.Filled.Person,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        tonalElevation = 1.dp,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Medium,
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor,
            )
        }
    }
}

@Composable
private fun ActivityChartCard(
    buckets: List<ActivityChartBucket>,
    range: InsightsTimeRange,
    modifier: Modifier = Modifier,
) {
    val title = when (range) {
        InsightsTimeRange.TODAY -> "Today's Listening Pattern"
        InsightsTimeRange.LAST_7_DAYS -> "Daily Listening Duration"
        InsightsTimeRange.LAST_30_DAYS -> "30-Day Activity Breakdown"
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val totalMs = buckets.sumOf { it.durationMs }
                Text(
                    text = formatDurationHuman(totalMs),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(18.dp))

            if (buckets.isEmpty() || buckets.all { it.durationMs == 0L }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No listening activity recorded in this window.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                CanvasBarChart(
                    buckets = buckets,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                )
            }
        }
    }
}

@Composable
private fun CanvasBarChart(
    buckets: List<ActivityChartBucket>,
    modifier: Modifier = Modifier,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val activeGradient = Brush.verticalGradient(
        colors = listOf(primaryColor, primaryColor.copy(alpha = 0.65f)),
    )
    val todayHighlightBrush = Brush.verticalGradient(
        colors = listOf(MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.primary),
    )
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    val maxDurationMs = remember(buckets) {
        val max = buckets.maxOfOrNull { it.durationMs } ?: 1L
        if (max > 0L) max else 1L
    }

    val animProgress = remember { Animatable(0f) }
    LaunchedEffect(buckets) {
        animProgress.snapTo(0f)
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        )
    }

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .semantics {
                    contentDescription = "Listening activity bar chart"
                },
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val bottomY = canvasHeight - 4f
            val count = buckets.size
            if (count == 0) return@Canvas

            // Baseline line
            drawLine(
                color = gridColor,
                start = Offset(0f, bottomY),
                end = Offset(canvasWidth, bottomY),
                strokeWidth = 2f,
            )

            val spacing = (canvasWidth * 0.15f) / count.coerceAtLeast(1)
            val availableBarWidth = (canvasWidth - spacing * (count + 1)) / count
            val barWidth = availableBarWidth.coerceIn(4f, 32f)
            val step = canvasWidth / count

            buckets.forEachIndexed { index, bucket ->
                val centerX = step * index + (step / 2f)
                val left = centerX - (barWidth / 2f)
                val durationRatio = (bucket.durationMs.toFloat() / maxDurationMs.toFloat()).coerceIn(0f, 1f)
                val targetBarHeight = durationRatio * (canvasHeight - 16f)
                val currentBarHeight = targetBarHeight * animProgress.value

                if (currentBarHeight > 0f) {
                    val brush = if (bucket.isToday) todayHighlightBrush else activeGradient
                    drawRoundRect(
                        brush = brush,
                        topLeft = Offset(left, bottomY - currentBarHeight),
                        size = Size(barWidth, currentBarHeight),
                        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // X-Axis Labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            buckets.forEach { bucket ->
                Text(
                    text = bucket.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = if (bucket.isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (bucket.isToday) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TrackInsightRow(
    track: TrackInsightItem,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ArtworkImage(
                name = track.title,
                artist = track.artist,
                embeddedUrl = track.artworkUrl,
                fallbackIcon = Icons.Filled.MusicNote,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatDurationHuman(track.durationMs),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "${track.playCount} plays",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ArtistInsightRow(
    artist: ArtistInsightItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    val initial = artist.artist.take(1).uppercase()
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = artist.artist,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${artist.playCount} plays recorded",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = formatDurationHuman(artist.durationMs),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun RecentHistoryRow(
    item: RecentHistoryItem,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ArtworkImage(
                name = item.title,
                artist = item.artist,
                embeddedUrl = item.artworkUrl,
                fallbackIcon = Icons.Filled.MusicNote,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatRelativeTimestamp(item.timestampMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (item.completed) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Completed",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                    Text(
                        text = formatDurationHuman(item.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyStateCard(
    message: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun formatDurationHuman(ms: Long): String {
    if (ms <= 0L) return "0m"
    val totalSeconds = ms / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L

    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "${seconds}s"
    }
}

private fun formatRelativeTimestamp(epochMillis: Long): String {
    val now = System.currentTimeMillis()
    val diffMs = (now - epochMillis).coerceAtLeast(0L)
    val diffSec = diffMs / 1000L
    val diffMin = diffSec / 60L
    val diffHours = diffMin / 60L
    val diffDays = diffHours / 24L

    return when {
        diffSec < 60L -> "Just now"
        diffMin < 60L -> "${diffMin}m ago"
        diffHours < 24L -> "${diffHours}h ago"
        diffDays == 1L -> "Yesterday"
        diffDays < 7L -> "${diffDays}d ago"
        else -> {
            val formatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
            Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(formatter)
        }
    }
}
