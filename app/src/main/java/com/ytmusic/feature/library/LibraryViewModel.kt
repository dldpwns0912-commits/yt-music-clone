package com.ytmusic.feature.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytmusic.core.database.dao.TrackDao
import com.ytmusic.core.database.entity.TrackEntity
import com.ytmusic.core.downloader.DailyPlaylistSyncWorker
import com.ytmusic.core.extractor.model.TrackMetadata
import com.ytmusic.core.storage.StorageManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trackDao: TrackDao,
    private val storageManager: StorageManager
) : ViewModel() {

    val downloadedTracks: StateFlow<List<TrackEntity>> = trackDao
        .observeDownloadedTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalStorageBytes: StateFlow<Long> = trackDao
        .observeDownloadedTracks()
        .map { tracks -> tracks.sumOf { it.fileSize } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    fun deleteTrack(videoId: String) {
        viewModelScope.launch {
            storageManager.deleteDownloadedTrack(videoId)
        }
    }

    fun triggerDailySync() {
        try {
            DailyPlaylistSyncWorker.triggerImmediateSync(context)
            _syncMessage.value = "보관함 동기화를 시작했습니다"
        } catch (e: Exception) {
            _syncMessage.value = "동기화 실패: ${e.message}"
        }
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
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
