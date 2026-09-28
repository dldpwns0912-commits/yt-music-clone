package com.ytmusic.core.downloader

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ytmusic.core.database.dao.PlaylistDao
import com.ytmusic.core.database.dao.TrackDao
import com.ytmusic.core.extractor.model.TrackMetadata
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

@HiltWorker
class DailyPlaylistSyncWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val params: WorkerParameters,
    private val playlistDao: PlaylistDao,
    private val trackDao: TrackDao,
    private val downloadManager: AudioDownloadManager
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "DailyPlaylistSync"
        const val WORK_NAME = "daily_playlist_sync_work"
        const val PREFS_NAME = "daily_sync_prefs"
        const val KEY_TARGET_PLAYLIST = "target_playlist_id"
        const val KEY_AUTO_SYNC_ENABLED = "auto_sync_enabled"
        const val DEFAULT_PLAYLIST_ID = "favorites"

        fun scheduleDailySync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val dailyWork = PeriodicWorkRequestBuilder<DailyPlaylistSyncWorker>(
                24, TimeUnit.HOURS,
                2, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .addTag(WORK_NAME)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                dailyWork
            )
            Log.d(TAG, "Scheduled 24-hour periodic playlist sync work")
        }

        fun triggerImmediateSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val oneTimeWork = OneTimeWorkRequestBuilder<DailyPlaylistSyncWorker>()
                .setConstraints(constraints)
                .addTag("manual_playlist_sync")
                .build()

            WorkManager.getInstance(context).enqueue(oneTimeWork)
            Log.d(TAG, "Triggered immediate playlist sync work")
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean(KEY_AUTO_SYNC_ENABLED, true)
        if (!isEnabled) {
            Log.d(TAG, "Auto sync is disabled by user setting")
            return@withContext Result.success()
        }

        val targetPlaylistId = prefs.getString(KEY_TARGET_PLAYLIST, DEFAULT_PLAYLIST_ID) ?: DEFAULT_PLAYLIST_ID

        Log.d(TAG, "Executing daily sync for playlist: $targetPlaylistId")

        try {
            // Find target playlist, or fallback to favorites
            val playlistWithTracks = playlistDao.getPlaylistWithTracks(targetPlaylistId)
                ?: playlistDao.getPlaylistWithTracks(DEFAULT_PLAYLIST_ID)

            val tracks = playlistWithTracks?.tracks ?: emptyList()
            var enqueuedCount = 0

            for (track in tracks) {
                val isAlreadyDownloaded = track.isDownloaded &&
                        !track.localFilePath.isNullOrEmpty() &&
                        File(track.localFilePath).let { it.exists() && it.length() > 1024L }

                if (!isAlreadyDownloaded) {
                    val metadata = TrackMetadata(
                        id = track.id,
                        title = track.title,
                        artist = track.artist,
                        album = track.album,
                        thumbnailUrl = track.thumbnailUrl,
                        durationMs = track.durationMs
                    )
                    val enqueued = downloadManager.enqueueDownload(metadata)
                    if (enqueued) {
                        enqueuedCount++
                        Log.d(TAG, "Enqueued for auto-download: ${track.title}")
                    }
                }
            }

            Log.d(TAG, "Daily playlist sync completed. Enqueued $enqueuedCount tracks.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Daily playlist sync failed: ${e.message}", e)
            Result.failure()
        }
    }
}
