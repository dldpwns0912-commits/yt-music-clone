package com.ytmusic.core.downloader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ytmusic.core.database.dao.PlaylistDao
import com.ytmusic.core.database.dao.TrackDao
import com.ytmusic.core.database.entity.PlaylistTrackCrossRef
import com.ytmusic.core.database.entity.TrackEntity
import com.ytmusic.core.extractor.YoutubeStreamExtractor
import com.ytmusic.core.extractor.model.AudioCodec
import com.ytmusic.core.extractor.model.AudioQuality
import com.ytmusic.core.storage.StorageManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

@HiltWorker
class AudioDownloadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val params: WorkerParameters,
    private val streamExtractor: YoutubeStreamExtractor,
    private val trackDao: TrackDao,
    private val playlistDao: PlaylistDao,
    private val okHttpClient: OkHttpClient,
    private val storageManager: StorageManager
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_TRACK_ID = "track_id"
        const val KEY_TITLE = "title"
        const val KEY_ARTIST = "artist"
        const val KEY_ALBUM = "album"
        const val KEY_DURATION = "duration"
        const val KEY_THUMBNAIL = "thumbnail"
        const val KEY_QUALITY = "quality"
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"

        const val CHANNEL_ID = "yt_music_downloads"
        private const val NOTIFICATION_ID_BASE = 10000
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val trackId = inputData.getString(KEY_TRACK_ID) ?: return@withContext Result.failure()
        val title = inputData.getString(KEY_TITLE) ?: "Track"
        val artist = inputData.getString(KEY_ARTIST) ?: "Artist"
        val album = inputData.getString(KEY_ALBUM)
        val durationMs = inputData.getLong(KEY_DURATION, 0L)
        val thumbnailUrl = inputData.getString(KEY_THUMBNAIL) ?: ""
        val qualityPref = inputData.getString(KEY_QUALITY) ?: "HIGH"

        createNotificationChannel()
        val notificationId = NOTIFICATION_ID_BASE + (trackId.hashCode() and 0x7FFFFFFF % 10000)

        setForeground(createForegroundInfo(notificationId, title, artist, 0, false))

        try {
            // 1. Resolve AudioQuality
            val audioQuality = when (qualityPref.uppercase()) {
                "320K", "HIGH" -> AudioQuality.HIGH
                "128K", "LOW" -> AudioQuality.LOW
                else -> AudioQuality.MEDIUM
            }

            // 2. Extract playable stream
            val extractionResult = streamExtractor.extractStream(trackId)
            val stream = extractionResult.selectBestStream(audioQuality)
                ?: return@withContext Result.failure(workDataOf(KEY_ERROR to "No audio stream available"))

            val extension = if (stream.codec == AudioCodec.OPUS) "opus" else "m4a"
            val targetFile = File(storageManager.downloadsDir, "$trackId.$extension")
            val tempFile = File(storageManager.downloadsDir, "$trackId.$extension.part")

            // 3. Resume support
            val existingBytes = if (tempFile.exists()) tempFile.length() else 0L

            val requestBuilder = Request.Builder().url(stream.url)
            if (existingBytes > 0L) {
                requestBuilder.header("Range", "bytes=$existingBytes-")
            }

            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful && response.code != 416) {
                if (runAttemptCount < 3) return@withContext Result.retry()
                return@withContext Result.failure(workDataOf(KEY_ERROR to "HTTP ${response.code}"))
            }

            val body = response.body
                ?: return@withContext Result.failure(workDataOf(KEY_ERROR to "Null response body"))

            val contentLength = body.contentLength()
            val totalBytes = if (contentLength > 0) existingBytes + contentLength else stream.contentLength

            // Stream to disk
            body.byteStream().use { input ->
                val randomAccessFile = RandomAccessFile(tempFile, "rw")
                randomAccessFile.seek(existingBytes)

                val buffer = ByteArray(16384)
                var bytesRead: Int
                var currentBytes = existingBytes
                var lastProgressUpdate = 0L

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    if (isStopped) {
                        randomAccessFile.close()
                        return@withContext Result.failure()
                    }

                    randomAccessFile.write(buffer, 0, bytesRead)
                    currentBytes += bytesRead

                    val now = System.currentTimeMillis()
                    if (now - lastProgressUpdate > 300L || currentBytes == totalBytes) {
                        val progressPercent = if (totalBytes > 0) {
                            ((currentBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                        } else 0

                        setProgress(workDataOf(KEY_PROGRESS to progressPercent))
                        notificationManager.notify(
                            notificationId,
                            buildNotification(title, artist, progressPercent, false)
                        )
                        lastProgressUpdate = now
                    }
                }
                randomAccessFile.close()
            }

            // Move part file to final file
            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            // 4. Update Database
            val trackEntity = TrackEntity(
                id = trackId,
                title = title,
                artist = artist,
                album = album,
                durationMs = durationMs,
                thumbnailUrl = thumbnailUrl,
                localFilePath = targetFile.absolutePath,
                isDownloaded = true,
                downloadedAt = System.currentTimeMillis(),
                audioQuality = qualityPref,
                fileSize = targetFile.length(),
                mimeType = stream.mimeType
            )
            trackDao.upsertTrack(trackEntity)

            playlistDao.insertCrossRef(
                PlaylistTrackCrossRef(
                    playlistId = "downloads",
                    trackId = trackId,
                    position = 0
                )
            )

            // 5. Complete notification
            notificationManager.notify(
                notificationId,
                buildNotification(title, artist, 100, true)
            )

            Result.success(workDataOf(KEY_TRACK_ID to trackId))
        } catch (e: Exception) {
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Unknown download error")))
            }
        }
    }

    private fun createForegroundInfo(
        id: Int,
        title: String,
        artist: String,
        progress: Int,
        completed: Boolean
    ): ForegroundInfo {
        val notification = buildNotification(title, artist, progress, completed)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(id, notification)
        }
    }

    private fun buildNotification(
        title: String,
        artist: String,
        progress: Int,
        completed: Boolean
    ) = NotificationCompat.Builder(context, CHANNEL_ID)
        .setContentTitle(if (completed) "Download Complete" else "Downloading: $title")
        .setContentText(artist)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setProgress(100, progress, progress == 0 && !completed)
        .setOngoing(!completed)
        .setAutoCancel(completed)
        .setOnlyAlertOnce(true)
        .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of offline song downloads"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}
