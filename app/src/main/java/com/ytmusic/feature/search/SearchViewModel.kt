package com.ytmusic.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytmusic.core.database.dao.SearchHistoryDao
import com.ytmusic.core.database.dao.TrackDao
import com.ytmusic.core.database.entity.SearchHistoryEntity
import com.ytmusic.core.extractor.YoutubeStreamExtractor
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
    private val extractor: YoutubeStreamExtractor
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

    private var searchDebounceJob: Job? = null

    fun onQueryChanged(newQuery: String) {
        _query.value = newQuery
        searchDebounceJob?.cancel()

        if (newQuery.isBlank()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }

        // Debounce auto-search by 350ms
        searchDebounceJob = viewModelScope.launch {
            delay(350L)
            executeSearch(newQuery, recordHistory = false)
        }
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
                val resultsList = mutableListOf<TrackMetadata>()

                // 1. Direct YouTube URL or Video ID Resolution
                val directVideoId = extractVideoId(trimmed)
                if (directVideoId != null) {
                    try {
                        val extraction = extractor.extractStream(directVideoId)
                        resultsList.add(extraction.metadata)
                    } catch (_: Exception) {}
                }

                // 2. Fetch live YouTube search results
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

                // 3. Local database matches
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
