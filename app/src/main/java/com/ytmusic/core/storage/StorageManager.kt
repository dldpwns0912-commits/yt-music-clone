package com.ytmusic.core.storage

import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.ytmusic.core.database.dao.PlaylistDao
import com.ytmusic.core.database.dao.TrackDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class StorageStats(
    val cacheBytes: Long,
    val downloadBytes: Long,
    val maxCacheBytes: Long,
    val freeSpaceBytes: Long,
    val totalSpaceBytes: Long
)

@Singleton
class StorageManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trackDao: TrackDao,
    private val playlistDao: PlaylistDao
) {
    private val prefs = context.getSharedPreferences("yt_storage_prefs", Context.MODE_PRIVATE)

    val mediaCacheDir: File = File(context.cacheDir, "media_cache").apply { if (!exists()) mkdirs() }
    val downloadsDir: File = File(context.filesDir, "downloads").apply { if (!exists()) mkdirs() }

    companion object {
        private const val KEY_MAX_CACHE_BYTES = "max_cache_bytes"
        const val DEFAULT_MAX_CACHE_BYTES = 2L * 1024L * 1024L * 1024L // 2 GB
    }

    fun getMaxCacheBytes(): Long {
        return prefs.getLong(KEY_MAX_CACHE_BYTES, DEFAULT_MAX_CACHE_BYTES)
    }

    fun setMaxCacheBytes(bytes: Long) {
        prefs.edit().putLong(KEY_MAX_CACHE_BYTES, bytes).apply()
    }

    suspend fun getStorageStats(): StorageStats = withContext(Dispatchers.IO) {
        val cacheBytes = calculateDirectorySize(mediaCacheDir)
        val downloadBytes = calculateDirectorySize(downloadsDir)
        val stat = StatFs(context.filesDir.absolutePath)
        val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
        val totalBytes = stat.blockCountLong * stat.blockSizeLong

        StorageStats(
            cacheBytes = cacheBytes,
            downloadBytes = downloadBytes,
            maxCacheBytes = getMaxCacheBytes(),
            freeSpaceBytes = freeBytes,
            totalSpaceBytes = totalBytes
        )
    }

    /**
     * Evicts least recently accessed stream cache files until the total cache size
     * is within the configured limit, leaving room for [requiredBytes].
     * Never touches permanent downloads.
     */
    suspend fun autoCleanupCache(requiredBytes: Long = 0L): Long = withContext(Dispatchers.IO) {
        val maxLimit = getMaxCacheBytes()
        val files = mediaCacheDir.listFiles() ?: return@withContext 0L
        var currentCacheSize = files.sumOf { it.length() }

        if (currentCacheSize + requiredBytes <= maxLimit) {
            return@withContext 0L
        }

        // Sort by last modified ascending (oldest first)
        val sortedFiles = files.sortedBy { it.lastModified() }
        var bytesCleaned = 0L

        for (file in sortedFiles) {
            val length = file.length()
            if (file.delete()) {
                bytesCleaned += length
                currentCacheSize -= length
                if (currentCacheSize + requiredBytes <= maxLimit) {
                    break
                }
            }
        }

        bytesCleaned
    }

    suspend fun clearCache(): Boolean = withContext(Dispatchers.IO) {
        val files = mediaCacheDir.listFiles() ?: return@withContext true
        var allSuccess = true
        for (file in files) {
            if (!file.delete()) {
                allSuccess = false
            }
        }
        allSuccess
    }

    suspend fun deleteDownloadedTrack(trackId: String): Boolean = withContext(Dispatchers.IO) {
        val track = trackDao.getTrackById(trackId)
        val file = track?.localFilePath?.let { File(it) }
            ?: File(downloadsDir, "$trackId.m4a").takeIf { it.exists() }
            ?: File(downloadsDir, "$trackId.opus").takeIf { it.exists() }

        val deleted = file?.delete() ?: true

        trackDao.updateDownloadStatus(
            id = trackId,
            isDownloaded = false,
            localPath = null,
            downloadedAt = null,
            fileSize = 0L,
            quality = null
        )
        playlistDao.removeTrackFromPlaylist("downloads", trackId)

        deleted
    }

    private fun calculateDirectorySize(directory: File): Long {
        if (!directory.exists() || !directory.isDirectory) return 0L
        val files = directory.listFiles() ?: return 0L
        var total = 0L
        for (file in files) {
            total += if (file.isDirectory) calculateDirectorySize(file) else file.length()
        }
        return total
    }
}
