package com.ytmusic.core.media.service

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.ytmusic.core.database.dao.PlaybackHistoryDao
import com.ytmusic.core.database.dao.PlaylistDao
import com.ytmusic.core.database.dao.TrackDao
import com.ytmusic.core.database.entity.TrackEntity
import com.ytmusic.core.extractor.model.TrackMetadata
import com.ytmusic.core.media.player.MusicPlayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicLibrarySessionCallback @Inject constructor(
    private val playerManager: MusicPlayerManager,
    private val trackDao: TrackDao,
    private val playlistDao: PlaylistDao,
    private val historyDao: PlaybackHistoryDao
) : MediaLibrarySession.Callback {

    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        const val ROOT_ID = "ytmusic_root"
        const val CATEGORY_RECENT = "category_recent"
        const val CATEGORY_DOWNLOADS = "category_downloads"
        const val CATEGORY_PLAYLISTS = "category_playlists"
        const val CATEGORY_MOST_PLAYED = "category_most_played"

        const val ACTION_TOGGLE_NORMALIZATION = "com.ytmusic.action.TOGGLE_NORMALIZATION"
        const val ACTION_SET_SPEED = "com.ytmusic.action.SET_SPEED"
    }

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> {
        val rootExtras = Bundle().apply {
            putBoolean("android.media.browse.SEARCH_SUPPORTED", true)
        }

        val rootMediaItem = MediaItem.Builder()
            .setMediaId(ROOT_ID)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("YouTube Music")
                    .setIsPlayable(false)
                    .setIsBrowsable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .setExtras(rootExtras)
                    .build()
            )
            .build()

        return Futures.immediateFuture(LibraryResult.ofItem(rootMediaItem, params))
    }

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        return scope.future {
            val items: List<MediaItem> = when (parentId) {
                ROOT_ID -> getRootCategories()
                CATEGORY_RECENT -> getRecentlyPlayedItems()
                CATEGORY_DOWNLOADS -> getDownloadedItems()
                CATEGORY_PLAYLISTS -> getPlaylistFolders()
                CATEGORY_MOST_PLAYED -> getMostPlayedItems()
                else -> {
                    if (parentId.startsWith("playlist_")) {
                        val playlistId = parentId.removePrefix("playlist_")
                        getPlaylistTracks(playlistId)
                    } else {
                        emptyList()
                    }
                }
            }

            LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
        }
    }

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String
    ): ListenableFuture<LibraryResult<MediaItem>> {
        return scope.future {
            val trackEntity = trackDao.getTrackById(mediaId)
            if (trackEntity != null) {
                val mediaItem = trackEntity.toPlayableMediaItem()
                LibraryResult.ofItem(mediaItem, null)
            } else {
                LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
            }
        }
    }

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>
    ): ListenableFuture<List<MediaItem>> {
        return scope.future {
            val resolvedList = mutableListOf<MediaItem>()
            val tracks = mutableListOf<TrackMetadata>()

            for (item in mediaItems) {
                val track = trackDao.getTrackById(item.mediaId)?.toMetadata() ?: TrackMetadata(
                    id = item.mediaId,
                    title = item.mediaMetadata.title?.toString() ?: "Unknown Track",
                    artist = item.mediaMetadata.artist?.toString() ?: "Unknown Artist",
                    album = item.mediaMetadata.albumTitle?.toString(),
                    thumbnailUrl = item.mediaMetadata.artworkUri?.toString() ?: ""
                )
                tracks.add(track)
                val resolved = playerManager.resolveMediaItem(track)
                resolvedList.add(resolved)
            }

            // Sync with PlayerManager queue
            if (tracks.isNotEmpty()) {
                playerManager.playTrack(tracks.first(), tracks)
            }

            resolvedList
        }
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle
    ): ListenableFuture<SessionResult> {
        when (customCommand.customAction) {
            ACTION_TOGGLE_NORMALIZATION -> {
                val enabled = args.getBoolean("enabled", true)
                playerManager.setVolumeNormalization(enabled)
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            ACTION_SET_SPEED -> {
                val speed = args.getFloat("speed", 1.0f)
                playerManager.setPlaybackSpeed(speed)
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
        }
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
    }

    // Helper functions for Android Auto / MediaBrowser category trees
    private fun getRootCategories(): List<MediaItem> {
        return listOf(
            buildBrowsableCategory(CATEGORY_RECENT, "Recently Played", "Your recently played music"),
            buildBrowsableCategory(CATEGORY_DOWNLOADS, "Downloaded", "Offline listening"),
            buildBrowsableCategory(CATEGORY_PLAYLISTS, "Playlists", "Saved and custom playlists"),
            buildBrowsableCategory(CATEGORY_MOST_PLAYED, "Most Played", "Your top tracks")
        )
    }

    private suspend fun getRecentlyPlayedItems(): List<MediaItem> {
        val tracks = historyDao.observeRecentlyPlayedTracks(30).first()
        return tracks.map { it.toPlayableMediaItem() }
    }

    private suspend fun getDownloadedItems(): List<MediaItem> {
        val tracks = trackDao.getDownloadedTracks()
        return tracks.map { it.toPlayableMediaItem() }
    }

    private suspend fun getMostPlayedItems(): List<MediaItem> {
        val tracks = historyDao.observeMostPlayedTracks(30).first()
        return tracks.map { it.toPlayableMediaItem() }
    }

    private suspend fun getPlaylistFolders(): List<MediaItem> {
        val playlists = playlistDao.observeAllPlaylists().first()
        return playlists.map { playlist ->
            MediaItem.Builder()
                .setMediaId("playlist_${playlist.id}")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(playlist.name)
                        .setSubtitle(playlist.description ?: "Playlist")
                        .setIsPlayable(false)
                        .setIsBrowsable(true)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                        .build()
                )
                .build()
        }
    }

    private suspend fun getPlaylistTracks(playlistId: String): List<MediaItem> {
        val playlistWithTracks = playlistDao.observePlaylistWithTracks(playlistId).first()
        return playlistWithTracks?.tracks?.map { it.toPlayableMediaItem() } ?: emptyList()
    }

    private fun buildBrowsableCategory(id: String, title: String, subtitle: String): MediaItem {
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setIsPlayable(false)
                    .setIsBrowsable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .build()
            )
            .build()
    }

    private fun TrackEntity.toPlayableMediaItem(): MediaItem {
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .setArtworkUri(if (thumbnailUrl.isNotBlank()) Uri.parse(thumbnailUrl) else null)
                    .build()
            )
            .build()
    }

    private fun TrackEntity.toMetadata(): TrackMetadata {
        return TrackMetadata(
            id = id,
            title = title,
            artist = artist,
            artistId = artistId,
            album = album,
            albumId = albumId,
            durationMs = durationMs,
            thumbnailUrl = thumbnailUrl
        )
    }
}
