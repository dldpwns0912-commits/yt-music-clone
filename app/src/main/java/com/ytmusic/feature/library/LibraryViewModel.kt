package com.ytmusic.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytmusic.core.database.dao.TrackDao
import com.ytmusic.core.database.entity.TrackEntity
import com.ytmusic.core.extractor.model.TrackMetadata
import com.ytmusic.core.storage.StorageManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val trackDao: TrackDao,
    private val storageManager: StorageManager
) : ViewModel() {

    val downloadedTracks: StateFlow<List<TrackEntity>> = trackDao
        .observeDownloadedTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalStorageBytes: StateFlow<Long> = storageManager
        .totalDownloadedSize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    fun deleteTrack(videoId: String) {
        viewModelScope.launch {
            storageManager.deleteDownloadedTrack(videoId)
        }
    }

    fun toTrackMetadata(entity: TrackEntity): TrackMetadata {
        return TrackMetadata(
            id = entity.id,
            title = entity.title,
            artist = entity.artist,
            thumbnailUrl = entity.thumbnailUrl,
            durationMs = entity.durationMs
        )
    }
}
