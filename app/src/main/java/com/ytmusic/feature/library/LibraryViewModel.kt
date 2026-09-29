package com.ytmusic.feature.library

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
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
    /**
     * Imports a single user-selected local media file.
     */
    fun importLocalFile(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val title = importSingleUri(uri)
            if (title != null) {
                _syncMessage.value = "'$title' 보관함에 추가되었습니다"
            } else {
                _syncMessage.value = "파일 추가에 실패했습니다"
            }
        }
    }

    /**
     * Imports multiple user-selected local media files in batch.
     */
    fun importLocalFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            _syncMessage.value = "${uris.size}개 파일 일괄 추가 중..."
            var successCount = 0
            for (uri in uris) {
                try {
                    if (importSingleUri(uri) != null) {
                        successCount++
                    }
                } catch (e: Exception) {
                    Log.e("LibraryViewModel", "Import failed for $uri: ${e.message}")
                }
            }
            _syncMessage.value = "총 ${successCount}개 파일이 보관함에 추가되었습니다"
        }
    }

    /**
     * Imports all media files from a user-selected folder tree.
     */
    fun importFolder(treeUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _syncMessage.value = "폴더 내 미디어 파일 검색 중..."
            val docUris = mutableListOf<Uri>()
            try {
                val contentResolver = context.contentResolver
                val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)
                contentResolver.query(
                    childrenUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE
                    ),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    while (cursor.moveToNext()) {
                        val docId = cursor.getString(idCol) ?: continue
                        val name = cursor.getString(nameCol) ?: ""
                        val mime = cursor.getString(mimeCol) ?: ""
                        if (mime.startsWith("audio/") || mime.startsWith("video/") ||
                            name.endsWith(".mp3", true) || name.endsWith(".m4a", true) ||
                            name.endsWith(".mp4", true) || name.endsWith(".webm", true) ||
                            name.endsWith(".flac", true) || name.endsWith(".opus", true)) {
                            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                            docUris.add(docUri)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("LibraryViewModel", "Error reading folder: ${e.message}")
            }

            if (docUris.isEmpty()) {
                _syncMessage.value = "폴더에 추가할 수 있는 음원/영상 파일이 없습니다"
                return@launch
            }

            _syncMessage.value = "${docUris.size}개 파일 일괄 추가 중..."
            var count = 0
            for (uri in docUris) {
                try {
                    if (importSingleUri(uri) != null) count++
                } catch (_: Exception) {}
            }
            _syncMessage.value = "폴더에서 총 ${count}개 파일이 보관함에 추가되었습니다"
        }
    }

    /**
     * Fast 1-click scan of device public Downloads folder (/storage/emulated/0/Download).
     */
    fun scanDeviceDownloads() {
        viewModelScope.launch(Dispatchers.IO) {
            _syncMessage.value = "기기 다운로드 폴더 스캔 중..."
            val dir = File("/storage/emulated/0/Download")
            if (!dir.exists() || !dir.canRead()) {
                _syncMessage.value = "기기 다운로드 폴더에 접근할 수 없습니다"
                return@launch
            }

            val mediaFiles = dir.listFiles { file ->
                file.isFile && (
                    file.name.endsWith(".mp3", true) ||
                    file.name.endsWith(".m4a", true) ||
                    file.name.endsWith(".mp4", true) ||
                    file.name.endsWith(".webm", true) ||
                    file.name.endsWith(".flac", true) ||
                    file.name.endsWith(".opus", true)
                )
            } ?: emptyArray()

            if (mediaFiles.isEmpty()) {
                _syncMessage.value = "다운로드 폴더에 음원/영상 파일이 없습니다"
                return@launch
            }

            var count = 0
            for (file in mediaFiles) {
                try {
                    val trackId = "local_dl_${file.name.hashCode()}"
                    var title = file.nameWithoutExtension
                    var artist = "기기 다운로드"
                    var durationMs = 0L

                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(file.absolutePath)
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.let {
                            if (it.isNotBlank()) title = it
                        }
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.let {
                            if (it.isNotBlank()) artist = it
                        }
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let {
                            durationMs = it
                        }
                    } catch (_: Exception) {
                    } finally {
                        try { retriever.release() } catch (_: Exception) {}
                    }

                    val mime = when {
                        file.name.endsWith(".mp4", true) -> "video/mp4"
                        file.name.endsWith(".webm", true) -> "video/webm"
                        file.name.endsWith(".mp3", true) -> "audio/mpeg"
                        file.name.endsWith(".m4a", true) -> "audio/mp4"
                        file.name.endsWith(".opus", true) -> "audio/opus"
                        file.name.endsWith(".flac", true) -> "audio/flac"
                        else -> "audio/mp4"
                    }

                    val entity = TrackEntity(
                        id = trackId,
                        title = title,
                        artist = artist,
                        album = "기기 다운로드 폴더",
                        durationMs = durationMs,
                        thumbnailUrl = "",
                        localFilePath = file.absolutePath,
                        isDownloaded = true,
                        downloadedAt = file.lastModified(),
                        fileSize = file.length(),
                        mimeType = mime
                    )
                    trackDao.upsertTrack(entity)
                    count++
                } catch (_: Exception) {}
            }
            _syncMessage.value = "다운로드 폴더에서 ${count}개 파일 등록 완료"
        }
    }

    private fun importSingleUri(uri: Uri): String? {
        val contentResolver = context.contentResolver
        var displayName = "미디어_${System.currentTimeMillis() % 10000}"
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
            mimeType.contains("flac") -> "flac"
            displayName.contains(".") -> displayName.substringAfterLast(".")
            else -> "mp4"
        }

        val trackId = "local_${System.currentTimeMillis()}_${(displayName.hashCode() and 0x7FFFFFFF) % 10000}"
        val targetFile = File(storageManager.downloadsDir, "$trackId.$ext")

        contentResolver.openInputStream(uri)?.use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: return null

        var title = displayName.substringBeforeLast(".")
        var artist = "내 기기 미디어"
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

            // Extract frame thumbnail if video
            if (mimeType.startsWith("video") || ext in listOf("mp4", "mkv", "webm")) {
                val frame = retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (frame != null) {
                    val thumbFile = File(storageManager.downloadsDir, "${trackId}_thumb.jpg")
                    thumbFile.outputStream().use { out ->
                        frame.compress(Bitmap.CompressFormat.JPEG, 85, out)
                    }
                    thumbUrl = thumbFile.absolutePath
                }
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
        return title
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
