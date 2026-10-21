package com.lastwave.app.data.repository

import com.lastwave.app.data.local.db.DownloadedTrackDao
import com.lastwave.app.data.local.db.ListeningEventDao
import com.lastwave.app.data.playlist.PlaylistRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local Stats Mode for the Stats tab (HomeScreen).
 *
 * When Last.fm is NOT connected, the Stats page seamlessly switches here:
 * plays, top artists/tracks, and listening time are aggregated directly from
 * the local playback statistics and listening events in Room.
 *
 * Downloads and playlists are used solely as a metadata dictionary (e.g. mapping
 * played track title/artist to album name and artwork), never as a proxy for play counts.
 *
 * This is the local-first counterpart to [HomeRepository.fetchStats]: same
 * [HomeStats] shape so the UI renders identically, with a banner pointing to
 * Settings → Integrations to sync global scrobbles.
 */
@Singleton
class LocalStatsRepository @Inject constructor(
    private val songPlayStatsRepository: SongPlayStatsRepository,
    private val listeningEventDao: ListeningEventDao,
    private val playlistRepository: PlaylistRepository,
    private val downloadedTrackDao: DownloadedTrackDao,
) {
    data class LocalStats(
        val stats: HomeStats,
        val topTracks: List<HomeTrack>,
        val topArtists: List<HomeArtistItem>,
        val topAlbums: List<HomeAlbum>,
        val recentTracks: List<HomeTrack> = emptyList(),
    )

    suspend fun load(): LocalStats {
        val playedStats = runCatching { songPlayStatsRepository.getAllPlayed() }.getOrDefault(emptyList())
            .filter { it.title.isNotBlank() && it.artist.isNotBlank() && !com.lastwave.app.util.ArtistHelper.isPlayCountOrStat(it.artist) }
        val recentEvents = runCatching { listeningEventDao.getRecentEvents(100) }.getOrDefault(emptyList())
            .filter { it.title.isNotBlank() && it.artist.isNotBlank() && !com.lastwave.app.util.ArtistHelper.isPlayCountOrStat(it.artist) }
        val eventCount = runCatching { listeningEventDao.getPlayCountSince(0L) }.getOrDefault(0)

        // Real play counts: sum of completed or meaningful plays from stats and event history.
        val statsPlaySum = playedStats.sumOf { maxOf(it.playCount.toLong(), if (it.totalPlayTimeMs > 0L) 1L else 0L) }
        val totalPlays = maxOf(eventCount.toLong(), statsPlaySum)

        // Distinct tracks actually played
        val playedKeys = (playedStats.map { it.trackKey } + recentEvents.map { it.trackKey }).filter { it.isNotBlank() }.toSet()
        val distinctTracks = playedKeys.size.toLong()

        // Distinct artists actually listened to
        val distinctArtists = (playedStats.map { it.artist } + recentEvents.map { it.artist })
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() && !com.lastwave.app.util.ArtistHelper.isPlayCountOrStat(it) }
            .distinct()
            .size.toLong()

        // Downloads and saved playlists are used strictly as a metadata dictionary
        // to look up album names and album artwork for tracks that were actually played.
        val playlists = runCatching { playlistRepository.getAll() }.getOrDefault(emptyList())
        val liked = runCatching { playlistRepository.getLikedSongs()?.tracks.orEmpty() }.getOrDefault(emptyList())
        val downloaded = runCatching { downloadedTrackDao.getAllList() }.getOrDefault(emptyList())

        val trackToAlbum = mutableMapOf<String, String>()
        val albumToArtwork = mutableMapOf<String, String>()

        (playlists.flatMap { it.tracks } + liked).forEach { t ->
            val album = t.album?.trim()?.takeIf(String::isNotBlank)
            if (album != null) {
                val key = "${t.name.trim()}|${t.artist.trim()}".lowercase()
                trackToAlbum.putIfAbsent(key, album)
                if (!t.artworkUrl.isNullOrBlank()) {
                    albumToArtwork.putIfAbsent("${album.lowercase()}|${t.artist.trim().lowercase()}", t.artworkUrl)
                }
            }
        }
        downloaded.forEach { d ->
            val album = d.album.trim().takeIf(String::isNotBlank)
            if (album != null) {
                val key = "${d.title.trim()}|${d.artist.trim()}".lowercase()
                trackToAlbum.putIfAbsent(key, album)
                if (!d.artworkUrl.isNullOrBlank()) {
                    albumToArtwork.putIfAbsent("${album.lowercase()}|${d.artist.trim().lowercase()}", d.artworkUrl)
                }
            }
        }

        // Top albums: map played tracks to their resolved albums. Unplayed tracks never contribute.
        data class PlayedAlbumSeed(val title: String, val artist: String, val artworkUrl: String?, val playCount: Long)
        val playedAlbumSeeds = mutableListOf<PlayedAlbumSeed>()

        for (stat in playedStats) {
            val key = stat.trackKey.lowercase()
            val albumName = trackToAlbum[key] ?: continue
            val plays = maxOf(stat.playCount.toLong(), if (stat.totalPlayTimeMs > 0L) 1L else 0L)
            val art = stat.artworkUrl ?: albumToArtwork["${albumName.lowercase()}|${stat.artist.trim().lowercase()}"]
            playedAlbumSeeds.add(PlayedAlbumSeed(albumName, stat.artist.trim(), art, plays))
        }

        val distinctAlbums = playedAlbumSeeds.map { "${it.title.lowercase()}|${it.artist.lowercase()}" }.distinct().size.toLong()

        val topAlbums = playedAlbumSeeds.groupBy { "${it.title.lowercase()}|${it.artist.lowercase()}" }
            .entries
            .map { (_, seeds) ->
                val first = seeds.first()
                HomeAlbum(
                    name = first.title,
                    artist = first.artist,
                    artworkUrl = seeds.firstOrNull { !it.artworkUrl.isNullOrBlank() }?.artworkUrl,
                    playCount = seeds.sumOf { it.playCount },
                )
            }
            .sortedByDescending { it.playCount }
            .take(10)

        // Top tracks sorted by play count descending
        val topTracks = playedStats.map { stat ->
            val plays = maxOf(stat.playCount, if (stat.totalPlayTimeMs > 0L) 1 else 0)
            HomeTrack(
                name = stat.title,
                artist = stat.artist,
                artworkUrl = stat.artworkUrl,
                timestampMillis = stat.lastPlayedAtMillis.takeIf { it > 0L },
                playCount = plays,
                videoId = stat.videoId,
            )
        }.sortedWith(
            compareByDescending<HomeTrack> { it.playCount }
                .thenByDescending { it.timestampMillis ?: 0L }
        ).take(50)

        val topCountByKey = topTracks.associate { it.key to it.playCount }

        // Recent tracks with real playback timestamps for the Recent tab
        val recentTracks = if (recentEvents.isNotEmpty()) {
            recentEvents.map { ev ->
                val safeArtist = ev.artist.takeIf { !com.lastwave.app.util.ArtistHelper.isPlayCountOrStat(it) } ?: ""
                HomeTrack(
                    name = ev.title,
                    artist = safeArtist,
                    artworkUrl = ev.artworkUrl,
                    timestampMillis = ev.timestampMillis,
                    playCount = topCountByKey["${ev.title.lowercase()}|${safeArtist.lowercase()}"] ?: 1,
                    videoId = ev.videoId,
                )
            }
        } else {
            runCatching { songPlayStatsRepository.getAllRecent() }.getOrDefault(emptyList()).map { stat ->
                val plays = maxOf(stat.playCount, if (stat.totalPlayTimeMs > 0L) 1 else 0)
                HomeTrack(
                    name = stat.title,
                    artist = stat.artist,
                    artworkUrl = stat.artworkUrl,
                    timestampMillis = stat.lastPlayedAtMillis.takeIf { it > 0L },
                    playCount = plays,
                    videoId = stat.videoId,
                )
            }
        }

        // Top artists from actual playback
        val artistGroups = playedStats
            .filter { it.artist.isNotBlank() && !com.lastwave.app.util.ArtistHelper.isPlayCountOrStat(it.artist) }
            .groupBy { it.artist.trim().lowercase() }

        val topArtists = artistGroups.entries
            .map { (_, statsList) ->
                val first = statsList.first()
                val totalArtistPlays = statsList.sumOf { maxOf(it.playCount.toLong(), if (it.totalPlayTimeMs > 0L) 1L else 0L) }
                val artwork = statsList.firstOrNull { !it.artworkUrl.isNullOrBlank() }?.artworkUrl
                HomeArtistItem(
                    name = first.artist.trim(),
                    playCount = totalArtistPlays,
                    artworkUrl = artwork,
                )
            }
            .sortedByDescending { it.playCount }
            .take(30)

        return LocalStats(
            stats = HomeStats(
                scrobbles = totalPlays,
                trackCount = distinctTracks,
                artistCount = distinctArtists,
                albumCount = distinctAlbums,
                avatarUrl = null,
            ),
            topTracks = topTracks,
            topArtists = topArtists,
            topAlbums = topAlbums,
            recentTracks = recentTracks,
        )
    }
}
