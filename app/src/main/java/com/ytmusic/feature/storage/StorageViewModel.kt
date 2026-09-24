package com.ytmusic.feature.storage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytmusic.core.backup.PlaylistBackupManager
import com.ytmusic.core.database.dao.TrackDao
import com.ytmusic.core.database.entity.TrackEntity
import com.ytmusic.core.storage.StorageManager
import com.ytmusic.core.storage.StorageStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StorageViewModel @Inject constructor(
    private val storageManager: StorageManager,
    private val backupManager: PlaylistBackupManager,
    private val trackDao: TrackDao
) : ViewModel() {

    private val _storageStats = MutableStateFlow<StorageStats?>(null)
    val storageStats: StateFlow<StorageStats?> = _storageStats.asStateFlow()

    val downloadedTracks: StateFlow<List<TrackEntity>> = trackDao
        .observeDownloadedTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        refreshStats()
    }

    fun refreshStats() {
        viewModelScope.launch {
            _storageStats.value = storageManager.getStorageStats()
        }
    }

    fun setCacheLimitBytes(bytes: Long) {
        storageManager.setMaxCacheBytes(bytes)
        refreshStats()
    }

    fun clearCache() {
        viewModelScope.launch {
            val cleared = storageManager.clearCache()
            if (cleared) {
                _statusMessage.value = "Streaming cache cleared successfully"
            }
            refreshStats()
        }
    }

    fun deleteDownloadedTrack(trackId: String) {
        viewModelScope.launch {
            storageManager.deleteDownloadedTrack(trackId)
            _statusMessage.value = "Track removed from offline storage"
            refreshStats()
        }
    }

    fun exportBackup(onExportReady: (String) -> Unit) {
        viewModelScope.launch {
            val json = backupManager.exportBackupJson()
            _statusMessage.value = "Playlists exported successfully"
            onExportReady(json)
        }
    }

    fun restoreBackup(jsonContent: String) {
        viewModelScope.launch {
            val result = backupManager.restoreBackupJson(jsonContent)
            if (result.success) {
                _statusMessage.value = "Restored ${result.playlistsRestored} playlists and ${result.tracksRestored} tracks"
            } else {
                _statusMessage.value = "Backup restore failed: ${result.errorMessage}"
            }
            refreshStats()
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}
