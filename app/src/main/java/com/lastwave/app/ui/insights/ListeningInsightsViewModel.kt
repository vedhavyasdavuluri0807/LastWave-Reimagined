package com.lastwave.app.ui.insights

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lastwave.app.data.repository.InsightsTimeRange
import com.lastwave.app.data.repository.ListeningInsightsData
import com.lastwave.app.data.repository.ListeningInsightsRepository
import com.lastwave.app.data.repository.RecentHistoryItem
import com.lastwave.app.data.repository.TrackInsightItem
import com.lastwave.app.playback.MusicPlayer
import com.lastwave.app.playback.PlayableTrack
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@Immutable
data class ListeningInsightsUiState(
    val isLoading: Boolean = false,
    val selectedRange: InsightsTimeRange = InsightsTimeRange.TODAY,
    val insights: ListeningInsightsData? = null,
    val errorMessage: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ListeningInsightsViewModel @Inject constructor(
    private val repository: ListeningInsightsRepository,
) : ViewModel() {

    private val _selectedRange = MutableStateFlow(InsightsTimeRange.TODAY)
    val selectedRange: StateFlow<InsightsTimeRange> = _selectedRange

    val uiState: StateFlow<ListeningInsightsUiState> = _selectedRange
        .flatMapLatest { range ->
            repository.observeInsights(range)
                .map { data ->
                    ListeningInsightsUiState(
                        isLoading = false,
                        selectedRange = range,
                        insights = data,
                        errorMessage = null,
                    )
                }
                .catch { error ->
                    emit(
                        ListeningInsightsUiState(
                            isLoading = false,
                            selectedRange = range,
                            insights = null,
                            errorMessage = error.localizedMessage ?: "Failed to load listening insights",
                        ),
                    )
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = ListeningInsightsUiState(isLoading = true, selectedRange = InsightsTimeRange.TODAY),
        )

    fun setTimeRange(range: InsightsTimeRange) {
        _selectedRange.value = range
    }

    fun playTrack(player: MusicPlayer, track: TrackInsightItem) {
        val playable = PlayableTrack(
            title = track.title,
            artist = track.artist,
            videoId = track.videoId,
            artworkUrl = track.artworkUrl,
        )
        player.play(playable, sourceLabel = "Listening Insights")
    }

    fun playRecent(player: MusicPlayer, recent: RecentHistoryItem) {
        val playable = PlayableTrack(
            title = recent.title,
            artist = recent.artist,
            videoId = recent.videoId,
            artworkUrl = recent.artworkUrl,
        )
        player.play(playable, sourceLabel = "Recent History")
    }
}
