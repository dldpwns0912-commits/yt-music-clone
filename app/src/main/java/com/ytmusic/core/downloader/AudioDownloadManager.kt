package com.ytmusic.core.downloader

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.ytmusic.core.database.dao.TrackDao
import com.ytmusic.core.extractor.model.TrackMetadata
import com.ytmusic.core.storage.StorageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

sealed interface DownloadState {
    data object Idle : DownloadState
    data object Enqueued : DownloadState
    data class Downloading(val progress: Int) : DownloadState
    data object Completed : DownloadState
    data class Failed(val error: String) : DownloadState
}

@Singleton
class AudioDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trackDao: TrackDao,
    private val storageManager: StorageManager
) {
    private val workManager = WorkManager.getInstance(context)

    fun enqueueDownload(track: TrackMetadata, quality: String = "HIGH") {
        val inputData = Data.Builder()
            .putString(AudioDownloadWorker.KEY_TRACK_ID, track.id)
            .putString(AudioDownloadWorker.KEY_TITLE, track.title)
            .putString(AudioDownloadWorker.KEY_ARTIST, track.artist)
            .putString(AudioDownloadWorker.KEY_ALBUM, track.album)
            .putLong(AudioDownloadWorker.KEY_DURATION, track.durationMs)
            .putString(AudioDownloadWorker.KEY_THUMBNAIL, track.thumbnailUrl)
            .putString(AudioDownloadWorker.KEY_QUALITY, quality)
            .build()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresStorageNotLow(true)
            .build()

        val downloadWork = OneTimeWorkRequestBuilder<AudioDownloadWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .addTag("download_${track.id}")
            .build()

        workManager.enqueueUniqueWork(
            "download_${track.id}",
            ExistingWorkPolicy.KEEP,
            downloadWork
        )
    }

    fun cancelDownload(trackId: String) {
        workManager.cancelUniqueWork("download_$trackId")
    }

    suspend fun deleteDownload(trackId: String): Boolean {
        cancelDownload(trackId)
        return storageManager.deleteDownloadedTrack(trackId)
    }

    fun observeDownloadState(trackId: String): Flow<DownloadState> {
        return workManager.getWorkInfosForUniqueWorkFlow("download_$trackId")
            .map { workInfos ->
                val info = workInfos.firstOrNull() ?: return@map DownloadState.Idle
                when (info.state) {
                    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> DownloadState.Enqueued
                    WorkInfo.State.RUNNING -> {
                        val progress = info.progress.getInt(AudioDownloadWorker.KEY_PROGRESS, 0)
                        DownloadState.Downloading(progress)
                    }
                    WorkInfo.State.SUCCEEDED -> DownloadState.Completed
                    WorkInfo.State.FAILED -> {
                        val error = info.outputData.getString(AudioDownloadWorker.KEY_ERROR) ?: "Download failed"
                        DownloadState.Failed(error)
                    }
                    WorkInfo.State.CANCELLED -> DownloadState.Idle
                }
            }
    }

    fun observeIsDownloaded(trackId: String): Flow<Boolean> {
        return trackDao.observeTrackById(trackId).map { it?.isDownloaded == true }
    }
}
