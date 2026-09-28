package com.ytmusic.feature.library

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytmusic.core.database.dao.TrackDao
import com.ytmusic.core.database.entity.TrackEntity
import com.ytmusic.core.downloader.DailyPlaylistSyncWorker
import com.ytmusic.core.extractor.model.TrackMetadata
import com.ytmusic.core.storage.StorageManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
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

    /**
     * Imports a user-selected local video/audio file into the Library.
     */
    fun importLocalFile(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val contentResolver = context.contentResolver
                var displayName = "영상_${System.currentTimeMillis() % 10000}"
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex != -1) {
                        cursor.getString(nameIndex)?.let { displayName = it }
                    }
                }

                val mimeType = contentResolver.getType(uri) ?: "video/mp4"
                val ext = when {
                    mimeType.contains("mp4") -> "mp4"
                    mimeType.contains("mkv") -> "mkv"
                    mimeType.contains("webm") -> "webm"
                    mimeType.contains("m4a") -> "m4a"
                    mimeType.contains("mp3") -> "mp3"
                    mimeType.contains("opus") -> "opus"
                    displayName.contains(".") -> displayName.substringAfterLast(".")
                    else -> "mp4"
                }

                val trackId = "local_${System.currentTimeMillis()}"
                val targetFile = File(storageManager.downloadsDir, "$trackId.$ext")

                contentResolver.openInputStream(uri)?.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: throw IllegalStateException("선택한 파일을 열 수 없습니다")

                var title = displayName.substringBeforeLast(".")
                var artist = "내 기기 영상"
                var durationMs = 0L
                var thumbUrl = ""

                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(targetFile.absolutePath)
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.let {
                        if (it.isNotBlank()) title = it
                    }
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.let {
                        if (it.isNotBlank()) artist = it
                    }
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let {
                        durationMs = it
                    }

                    // Extract frame thumbnail
                    val frame = retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    if (frame != null) {
                        val thumbFile = File(storageManager.downloadsDir, "${trackId}_thumb.jpg")
                        thumbFile.outputStream().use { out ->
                            frame.compress(Bitmap.CompressFormat.JPEG, 85, out)
                        }
                        thumbUrl = thumbFile.absolutePath
                    }
                } catch (e: Exception) {
                    Log.w("LibraryViewModel", "Local media metadata parsing: ${e.message}")
                } finally {
                    try { retriever.release() } catch (_: Exception) {}
                }

                val entity = TrackEntity(
                    id = trackId,
                    title = title,
                    artist = artist,
                    album = "내 기기 보관함",
                    durationMs = durationMs,
                    thumbnailUrl = thumbUrl,
                    localFilePath = targetFile.absolutePath,
                    isDownloaded = true,
                    downloadedAt = System.currentTimeMillis(),
                    fileSize = targetFile.length(),
                    mimeType = mimeType
                )
                trackDao.upsertTrack(entity)

                _syncMessage.value = "'$title' 보관함에 추가되었습니다"
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "Failed to import local file: ${e.message}", e)
                _syncMessage.value = "파일 추가 실패: ${e.message}"
            }
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
