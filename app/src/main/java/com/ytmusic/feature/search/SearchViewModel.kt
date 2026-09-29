package com.ytmusic.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytmusic.core.database.dao.SearchHistoryDao
import com.ytmusic.core.database.dao.TrackDao
import com.ytmusic.core.database.entity.SearchHistoryEntity
import com.ytmusic.core.downloader.AudioDownloadManager
import com.ytmusic.core.downloader.DownloadType
import com.ytmusic.core.extractor.YoutubeStreamExtractor
import com.ytmusic.core.extractor.model.PlaylistInfo
import com.ytmusic.core.extractor.model.TrackMetadata
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchHistoryDao: SearchHistoryDao,
    private val trackDao: TrackDao,
    private val extractor: YoutubeStreamExtractor,
    private val downloadManager: AudioDownloadManager
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val searchHistory: StateFlow<List<SearchHistoryEntity>> = searchHistoryDao
        .observeRecentSearches(limit = 20)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchResults = MutableStateFlow<List<TrackMetadata>>(emptyList())
    val searchResults: StateFlow<List<TrackMetadata>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _activePlaylist = MutableStateFlow<PlaylistInfo?>(null)
    val activePlaylist: StateFlow<PlaylistInfo?> = _activePlaylist.asStateFlow()

    private val _selectedTrackForDownload = MutableStateFlow<TrackMetadata?>(null)
    val selectedTrackForDownload: StateFlow<TrackMetadata?> = _selectedTrackForDownload.asStateFlow()

    private val _downloadToast = MutableStateFlow<String?>(null)
    val downloadToast: StateFlow<String?> = _downloadToast.asStateFlow()

    private var searchDebounceJob: Job? = null

    fun onQueryChanged(newQuery: String) {
        _query.value = newQuery
        searchDebounceJob?.cancel()

        if (newQuery.isBlank()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            _activePlaylist.value = null
            return
        }

        // Debounce auto-search by 350ms
        searchDebounceJob = viewModelScope.launch {
            delay(350L)
            executeSearch(newQuery, recordHistory = false)
        }
    }

    fun selectTrackForDownload(track: TrackMetadata?) {
        _selectedTrackForDownload.value = track
    }

    fun clearActivePlaylist() {
        _activePlaylist.value = null
    }

    fun clearDownloadToast() {
        _downloadToast.value = null
    }

    fun downloadTrack(track: TrackMetadata, isVideo: Boolean) {
        val type = if (isVideo) DownloadType.VIDEO else DownloadType.AUDIO
        val label = if (isVideo) "영상" else "음원"
        val success = downloadManager.enqueueDownload(track, "HIGH", type)
        if (success) {
            _downloadToast.value = "'${track.title}' $label 다운로드를 시작합니다"
        } else {
            _downloadToast.value = "다운로드 요청 실패"
        }
    }

    fun downloadPlaylist(playlist: PlaylistInfo, isVideo: Boolean) {
        val type = if (isVideo) DownloadType.VIDEO else DownloadType.AUDIO
        val label = if (isVideo) "영상" else "음원"
        val count = downloadManager.enqueueBatchDownload(playlist.tracks, "HIGH", type)
        _downloadToast.value = "'${playlist.title}' ${count}곡 $label 일괄 다운로드를 시작합니다"
    }

    fun executeSearch(searchQuery: String, recordHistory: Boolean = true) {
        val trimmed = searchQuery.trim()
        if (trimmed.isEmpty()) return

        _query.value = trimmed
        _isSearching.value = true

        viewModelScope.launch(Dispatchers.IO) {
            if (recordHistory) {
                searchHistoryDao.upsertSearch(
                    SearchHistoryEntity(
                        query = trimmed,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }

            try {
                // 1. Check if input is a playlist URL or ID
                val playlistId = extractPlaylistId(trimmed)
                if (playlistId != null) {
                    val pl = extractor.fetchPlaylist(playlistId)
                    if (pl != null && pl.tracks.isNotEmpty()) {
                        _activePlaylist.value = pl
                        _searchResults.value = pl.tracks
                        return@launch
                    }
                } else {
                    _activePlaylist.value = null
                }

                val resultsList = mutableListOf<TrackMetadata>()

                // 2. Direct YouTube Video URL or Video ID
                val directVideoId = extractVideoId(trimmed)
                if (directVideoId != null) {
                    try {
                        val extraction = extractor.extractStream(directVideoId)
                        resultsList.add(extraction.metadata)
                        _selectedTrackForDownload.value = extraction.metadata
                    } catch (_: Exception) {}
                }

                // 3. Live search
                val onlineResults = try {
                    extractor.searchVideos(trimmed)
                } catch (_: Exception) {
                    emptyList()
                }
                for (track in onlineResults) {
                    if (resultsList.none { it.id == track.id }) {
                        resultsList.add(track)
                    }
                }

                // 4. Local DB search
                try {
                    val localMatches = trackDao.searchTracks(trimmed).first().map { entity ->
                        TrackMetadata(
                            id = entity.id,
                            title = entity.title,
                            artist = entity.artist,
                            artistId = entity.artistId,
                            album = entity.album,
                            albumId = entity.albumId,
                            durationMs = entity.durationMs,
                            thumbnailUrl = entity.thumbnailUrl
                        )
                    }
                    for (track in localMatches) {
                        if (resultsList.none { it.id == track.id }) {
                            resultsList.add(track)
                        }
                    }
                } catch (_: Exception) {}

                _searchResults.value = resultsList
            } catch (_: Exception) {
                _searchResults.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }

    private fun extractPlaylistId(input: String): String? {
        val trimmed = input.trim()
        val patterns = listOf(
            Regex("[?&]list=([a-zA-Z0-9_-]+)"),
            Regex("^(?:PL|UU|LL|RD|OLAK5uy_)[a-zA-Z0-9_-]+$")
        )
        for (pattern in patterns) {
            val match = pattern.find(trimmed)
            if (match != null) {
                return if (match.groupValues.size > 1) match.groupValues[1] else match.groupValues[0]
            }
        }
        return null
    }

    private fun extractVideoId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.matches(Regex("^[a-zA-Z0-9_-]{11}$"))) {
            return trimmed
        }
        val patterns = listOf(
            Regex("(?:v=|/v/|youtu\\.be/|/embed/|/shorts/)([a-zA-Z0-9_-]{11})"),
            Regex("youtube\\.com/watch\\?.*v=([a-zA-Z0-9_-]{11})")
        )
        for (pattern in patterns) {
            val match = pattern.find(trimmed)
            if (match != null) {
                return match.groupValues[1]
            }
        }
        return null
    }

    fun deleteHistoryItem(query: String) {
        viewModelScope.launch(Dispatchers.IO) {
            searchHistoryDao.deleteSearch(query)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            searchHistoryDao.clearAllSearches()
        }
    }
}
