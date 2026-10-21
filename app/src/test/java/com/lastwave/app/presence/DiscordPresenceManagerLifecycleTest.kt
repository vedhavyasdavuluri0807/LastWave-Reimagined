package com.lastwave.app.presence

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.lastwave.app.data.artwork.ArtworkRepository
import com.lastwave.app.playback.MusicPlayer
import com.lastwave.app.playback.MusicPlayerState
import com.lastwave.app.playback.PlayableTrack
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiscordPresenceManagerLifecycleTest {

    private val testDispatcher = StandardTestDispatcher()

    private val mockContext = mockk<Context>(relaxed = true)
    private val mockMusicPlayer = mockk<MusicPlayer>(relaxed = true)
    private val mockPreferences = mockk<DiscordPresencePreferences>(relaxed = true)
    private val mockArtworkRepo = mockk<ArtworkRepository>(relaxed = true)

    private val playerStateFlow = MutableStateFlow(MusicPlayerState())
    private val preferencesFlow = MutableStateFlow(true)
    private val resolvedArtFlow = MutableStateFlow<Map<String, String>>(emptyMap())

    private lateinit var fakeTransport: FakeDiscordTransport
    private var currentTimeMs = 100_000L

    private fun sampleTrack(title: String = "Test Song", artist: String = "Test Artist") =
        PlayableTrack(
            title = title,
            artist = artist,
            album = "Test Album",
            artworkUrl = "https://example.com/cover.jpg",
            videoId = "test_vid",
            durationMs = 200_000L,
        )

    private fun createManager(scope: CoroutineScope): DiscordPresenceManager {
        return DiscordPresenceManager(
            musicPlayer = mockMusicPlayer,
            preferences = mockPreferences,
            artworkRepository = mockArtworkRepo,
            applicationScope = scope,
            context = mockContext,
        ).apply {
            transportFactory = { fakeTransport }
            dispatcher = testDispatcher
            clock = { currentTimeMs }
        }
    }

    @Before
    fun setUp() {
        fakeTransport = FakeDiscordTransport()
        currentTimeMs = 100_000L

        every { mockMusicPlayer.state } returns playerStateFlow
        every { mockPreferences.enabled } returns preferencesFlow
        every { mockArtworkRepo.resolved } returns resolvedArtFlow
    }

    @Test
    fun whenPlaying_pushesActivityWithTimestamps() = runTest(testDispatcher) {
        val manager = createManager(backgroundScope)
        manager.start()
        runCurrent()

        playerStateFlow.value = MusicPlayerState(
            current = sampleTrack(),
            isPlaying = true,
            positionMs = 10_000L,
            durationMs = 200_000L,
        )
        runCurrent()

        assertThat(fakeTransport.setActivityCalls).hasSize(1)
        val activity = fakeTransport.setActivityCalls.last()
        assertThat(activity.containsKey("timestamps")).isTrue()
    }

    @Test
    fun whenPaused_pushesActivityWithoutTimestamps_andDoesNotAdvanceProgressBar() = runTest(testDispatcher) {
        val manager = createManager(backgroundScope)
        manager.start()
        runCurrent()

        // 1. Play first
        playerStateFlow.value = MusicPlayerState(
            current = sampleTrack(),
            isPlaying = true,
            positionMs = 10_000L,
            durationMs = 200_000L,
        )
        runCurrent()
        assertThat(fakeTransport.setActivityCalls).hasSize(1)

        // 2. Pause
        currentTimeMs += 1_000L
        playerStateFlow.value = MusicPlayerState(
            current = sampleTrack(),
            isPlaying = false,
            isBuffering = false,
            positionMs = 10_000L,
            durationMs = 200_000L,
        )
        runCurrent()

        assertThat(fakeTransport.setActivityCalls).hasSize(2)
        val pausedActivity = fakeTransport.setActivityCalls.last()
        // Timestamps should be omitted from buildActivity so wire protocol serializes "timestamps": null
        assertThat(pausedActivity.containsKey("timestamps")).isFalse()
    }

    @Test
    fun whenClearStoppedIsTrue_clearsActivity_andPreventsStaleArtworkRepublishing() = runTest(testDispatcher) {
        val manager = createManager(backgroundScope)
        manager.start()
        runCurrent()

        val track = sampleTrack()
        playerStateFlow.value = MusicPlayerState(
            current = track,
            isPlaying = true,
            durationMs = 200_000L,
        )
        runCurrent()
        assertThat(fakeTransport.setActivityCalls).hasSize(1)

        // Stop playback (e.g. app swiped away, or natural completion)
        playerStateFlow.value = MusicPlayerState(
            current = track,
            isPlaying = false,
            isBuffering = false,
        )
        manager.clear(stopped = true)
        runCurrent()

        assertThat(fakeTransport.clearActivityCallCount).isAtLeast(1)
        val previousCallCount = fakeTransport.setActivityCalls.size

        // Delayed artwork finishes resolving for the old track after stop/clear
        resolvedArtFlow.value = mapOf("test song|test artist" to "https://example.com/late_resolved.jpg")
        runCurrent()

        // Must NOT resurrect or push any activity for the stopped track
        assertThat(fakeTransport.setActivityCalls).hasSize(previousCallCount)
    }

    @Test
    fun whenPlaybackResumesAfterStop_resetsStoppedStateAndPublishes() = runTest(testDispatcher) {
        val manager = createManager(backgroundScope)
        manager.start()
        runCurrent()

        val track = sampleTrack()
        playerStateFlow.value = MusicPlayerState(
            current = track,
            isPlaying = true,
            durationMs = 200_000L,
        )
        runCurrent()
        assertThat(fakeTransport.setActivityCalls).hasSize(1)

        manager.clear(stopped = true)
        runCurrent()
        assertThat(fakeTransport.clearActivityCallCount).isAtLeast(1)

        // Resume / new track starts playing
        val newTrack = sampleTrack(title = "Brand New Song")
        playerStateFlow.value = MusicPlayerState(
            current = newTrack,
            isPlaying = true,
            durationMs = 180_000L,
        )
        runCurrent()

        assertThat(fakeTransport.setActivityCalls).hasSize(2)
        val newActivity = fakeTransport.setActivityCalls.last()
        assertThat(newActivity["details"]?.toString()).contains("Brand New Song")
    }

    @Test
    fun whenPaused_heartbeatDoesNotRepushOrResurrectTimestamps() = runTest(testDispatcher) {
        val manager = createManager(backgroundScope)
        manager.start()
        runCurrent()

        // Play then pause
        playerStateFlow.value = MusicPlayerState(
            current = sampleTrack(),
            isPlaying = true,
            positionMs = 10_000L,
            durationMs = 200_000L,
        )
        runCurrent()
        assertThat(fakeTransport.setActivityCalls).hasSize(1)

        playerStateFlow.value = MusicPlayerState(
            current = sampleTrack(),
            isPlaying = false,
            isBuffering = false,
            positionMs = 10_000L,
            durationMs = 200_000L,
        )
        runCurrent()
        assertThat(fakeTransport.setActivityCalls).hasSize(2)

        // Advance past heartbeat interval
        currentTimeMs += DiscordPresence.PUSH_INTERVAL_MS + 1000L
        advanceTimeBy(DiscordPresence.PUSH_INTERVAL_MS + 1000L)
        runCurrent()

        // Heartbeat should NOT repush while paused
        assertThat(fakeTransport.setActivityCalls).hasSize(2)
    }

    private class FakeDiscordTransport : DiscordTransport {
        override var isOpen: Boolean = true
        val setActivityCalls = mutableListOf<JsonObject>()
        var clearActivityCallCount = 0
        var closed = false

        override fun setActivity(activity: JsonObject): Boolean {
            setActivityCalls.add(activity)
            return true
        }

        override fun clearActivity(): Boolean {
            clearActivityCallCount++
            return true
        }

        override fun close() {
            closed = true
            isOpen = false
        }
    }
}
