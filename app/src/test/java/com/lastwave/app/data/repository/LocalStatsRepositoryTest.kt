package com.lastwave.app.data.repository

import com.google.common.truth.Truth.assertThat
import com.lastwave.app.data.generate.GeneratedTrack
import com.lastwave.app.data.local.db.DownloadedTrackDao
import com.lastwave.app.data.local.db.DownloadedTrackEntity
import com.lastwave.app.data.local.db.ListeningEventDao
import com.lastwave.app.data.local.db.ListeningEventEntity
import com.lastwave.app.data.local.db.SongPlayStatsDao
import com.lastwave.app.data.local.db.SongPlayStatsEntity
import com.lastwave.app.data.playlist.PlaylistRepository
import com.lastwave.app.data.playlist.SavedPlaylist
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class LocalStatsRepositoryTest {

    private val mockSongPlayStatsDao = mockk<SongPlayStatsDao>(relaxed = true)
    private lateinit var songPlayStatsRepository: SongPlayStatsRepository

    private val mockListeningEventDao = mockk<ListeningEventDao>(relaxed = true)
    private val mockPlaylistRepository = mockk<PlaylistRepository>(relaxed = true)
    private val mockDownloadedTrackDao = mockk<DownloadedTrackDao>(relaxed = true)

    private lateinit var localStatsRepository: LocalStatsRepository

    @Before
    fun setUp() {
        songPlayStatsRepository = SongPlayStatsRepository(mockSongPlayStatsDao)
        localStatsRepository = LocalStatsRepository(
            songPlayStatsRepository = songPlayStatsRepository,
            listeningEventDao = mockListeningEventDao,
            playlistRepository = mockPlaylistRepository,
            downloadedTrackDao = mockDownloadedTrackDao,
        )
    }

    @Test
    fun load_whenNoTracksPlayed_returnsZeroStatsAndEmptyLists() = runTest {
        coEvery { mockSongPlayStatsDao.getAllPlayed() } returns emptyList()
        coEvery { mockListeningEventDao.getRecentEvents(any()) } returns emptyList()
        coEvery { mockListeningEventDao.getPlayCountSince(any()) } returns 0
        coEvery { mockPlaylistRepository.getAll() } returns emptyList()
        coEvery { mockPlaylistRepository.getLikedSongs() } returns null
        coEvery { mockDownloadedTrackDao.getAllList() } returns emptyList()

        val result = localStatsRepository.load()

        assertThat(result.stats.scrobbles).isEqualTo(0L)
        assertThat(result.stats.trackCount).isEqualTo(0L)
        assertThat(result.stats.artistCount).isEqualTo(0L)
        assertThat(result.stats.albumCount).isEqualTo(0L)
        assertThat(result.topTracks).isEmpty()
        assertThat(result.topArtists).isEmpty()
        assertThat(result.topAlbums).isEmpty()
        assertThat(result.recentTracks).isEmpty()
    }

    @Test
    fun load_unplayedPlaylistsAndDownloads_doNotFabricatePlays() = runTest {
        // User has downloads and saved playlists, but has never played anything
        val downloadedTrack = DownloadedTrackEntity(
            trackKey = "artist a_downloaded song",
            title = "Downloaded Song",
            artist = "Artist A",
            album = "Album A",
            filePath = "/path/1",
        )
        val playlistTrack = GeneratedTrack(
            name = "Playlist Song",
            artist = "Artist B",
            album = "Album B",
        )
        val playlist = SavedPlaylist(
            id = 1L,
            title = "My Playlist",
            subtitle = "Custom",
            mode = "custom",
            tracks = listOf(playlistTrack),
            createdAtMillis = 1000L,
        )

        coEvery { mockSongPlayStatsDao.getAllPlayed() } returns emptyList()
        coEvery { mockListeningEventDao.getRecentEvents(any()) } returns emptyList()
        coEvery { mockListeningEventDao.getPlayCountSince(any()) } returns 0
        coEvery { mockPlaylistRepository.getAll() } returns listOf(playlist)
        coEvery { mockPlaylistRepository.getLikedSongs() } returns null
        coEvery { mockDownloadedTrackDao.getAllList() } returns listOf(downloadedTrack)

        val result = localStatsRepository.load()

        // Unplayed tracks must NOT fabricate plays or appear in top tracks/artists
        assertThat(result.stats.scrobbles).isEqualTo(0L)
        assertThat(result.stats.trackCount).isEqualTo(0L)
        assertThat(result.stats.artistCount).isEqualTo(0L)
        assertThat(result.stats.albumCount).isEqualTo(0L)
        assertThat(result.topTracks).isEmpty()
        assertThat(result.topArtists).isEmpty()
        assertThat(result.topAlbums).isEmpty()
        assertThat(result.recentTracks).isEmpty()
    }

    @Test
    fun load_withRealPlayback_accuratelyAggregatesPlaysTracksArtistsAndAlbums() = runTest {
        val played1 = SongPlayStatsEntity(
            trackKey = "song 1|artist a",
            title = "Song 1",
            artist = "Artist A",
            videoId = "v1",
            artworkUrl = "https://art/1.jpg",
            totalPlayTimeMs = 180_000L,
            playCount = 5,
            lastPlayedAtMillis = 1000L,
        )
        val played2 = SongPlayStatsEntity(
            trackKey = "song 2|artist a",
            title = "Song 2",
            artist = "Artist A",
            videoId = "v2",
            artworkUrl = "https://art/2.jpg",
            totalPlayTimeMs = 210_000L,
            playCount = 3,
            lastPlayedAtMillis = 2000L,
        )
        val played3 = SongPlayStatsEntity(
            trackKey = "song 3|artist b",
            title = "Song 3",
            artist = "Artist B",
            videoId = "v3",
            artworkUrl = "https://art/3.jpg",
            totalPlayTimeMs = 120_000L,
            playCount = 2,
            lastPlayedAtMillis = 3000L,
        )

        val recentEvent = ListeningEventEntity(
            id = 1L,
            trackKey = "song 3|artist b",
            title = "Song 3",
            artist = "Artist B",
            videoId = "v3",
            artworkUrl = "https://art/3.jpg",
            durationMs = 120_000L,
            completed = true,
            timestampMillis = 3000L,
        )

        // Metadata: Song 1 and Song 2 belong to "Debut Album"
        val downloadedMetadata = listOf(
            DownloadedTrackEntity(
                trackKey = "artist a_song 1",
                title = "Song 1",
                artist = "Artist A",
                album = "Debut Album",
                filePath = "/path/1",
            ),
            DownloadedTrackEntity(
                trackKey = "artist a_song 2",
                title = "Song 2",
                artist = "Artist A",
                album = "Debut Album",
                filePath = "/path/2",
            ),
        )

        coEvery { mockSongPlayStatsDao.getAllPlayed() } returns listOf(played1, played2, played3)
        coEvery { mockListeningEventDao.getRecentEvents(100) } returns listOf(recentEvent)
        coEvery { mockListeningEventDao.getPlayCountSince(0L) } returns 10 // 5 + 3 + 2 = 10
        coEvery { mockPlaylistRepository.getAll() } returns emptyList()
        coEvery { mockPlaylistRepository.getLikedSongs() } returns null
        coEvery { mockDownloadedTrackDao.getAllList() } returns downloadedMetadata

        val result = localStatsRepository.load()

        // Scrobbles/plays = 10
        assertThat(result.stats.scrobbles).isEqualTo(10L)
        // Tracks = 3 unique
        assertThat(result.stats.trackCount).isEqualTo(3L)
        // Artists = 2 unique ("Artist A", "Artist B")
        assertThat(result.stats.artistCount).isEqualTo(2L)
        // Albums = 1 resolved ("Debut Album")
        assertThat(result.stats.albumCount).isEqualTo(1L)

        // Top tracks ordered by play count
        assertThat(result.topTracks).hasSize(3)
        assertThat(result.topTracks[0].name).isEqualTo("Song 1")
        assertThat(result.topTracks[0].playCount).isEqualTo(5)
        assertThat(result.topTracks[1].name).isEqualTo("Song 2")
        assertThat(result.topTracks[1].playCount).isEqualTo(3)
        assertThat(result.topTracks[2].name).isEqualTo("Song 3")
        assertThat(result.topTracks[2].playCount).isEqualTo(2)

        // Top artists: Artist A (5 + 3 = 8 plays), Artist B (2 plays)
        assertThat(result.topArtists).hasSize(2)
        assertThat(result.topArtists[0].name).isEqualTo("Artist A")
        assertThat(result.topArtists[0].playCount).isEqualTo(8L)
        assertThat(result.topArtists[1].name).isEqualTo("Artist B")
        assertThat(result.topArtists[1].playCount).isEqualTo(2L)

        // Top albums: "Debut Album" with 8 plays
        assertThat(result.topAlbums).hasSize(1)
        assertThat(result.topAlbums[0].name).isEqualTo("Debut Album")
        assertThat(result.topAlbums[0].artist).isEqualTo("Artist A")
        assertThat(result.topAlbums[0].playCount).isEqualTo(8L)

        // Recent tracks contains Song 3 with timestamp
        assertThat(result.recentTracks).hasSize(1)
        assertThat(result.recentTracks[0].name).isEqualTo("Song 3")
        assertThat(result.recentTracks[0].timestampMillis).isEqualTo(3000L)
    }

    @Test
    fun songPlayStatsRepository_recordListenedMs_incrementsPlayCountForMeaningfulPlay() = runTest {
        coEvery { mockSongPlayStatsDao.find("test song|test artist") } returns null

        songPlayStatsRepository.recordListenedMs(
            title = "Test Song",
            artist = "Test Artist",
            videoId = "v1",
            artworkUrl = null,
            listenedMs = 35_000L,
            completed = false, // Not completed naturally, but played for 35s
        )

        io.mockk.coVerify {
            mockSongPlayStatsDao.upsert(
                match { entity ->
                    entity.playCount == 1 &&
                        entity.totalPlayTimeMs == 35_000L &&
                        entity.title == "Test Song" &&
                        entity.artist == "Test Artist"
                },
            )
        }
    }
}
