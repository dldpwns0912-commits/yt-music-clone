package com.ytmusic.core.extractor.updater

import android.content.Context
import com.ytmusic.core.extractor.model.ExtractorVersionInfo
import com.ytmusic.core.extractor.model.UpdateStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExtractorUpdateManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val releaseChecker: GitHubReleaseChecker
) : ExtractorUpdateManager {

    private val prefs = context.getSharedPreferences("yt_extractor_prefs", Context.MODE_PRIVATE)
    private val binDir = File(context.filesDir, "bin").apply { if (!exists()) mkdirs() }
    private val binaryFile = File(binDir, "yt-dlp")

    override fun getCurrentVersion(): String {
        return prefs.getString("current_version", "none") ?: "none"
    }

    override fun getBinaryFile(): File = binaryFile

    override fun isBinaryAvailable(): Boolean {
        return binaryFile.exists() && binaryFile.length() > 0 && binaryFile.canExecute()
    }

    override fun checkForUpdates(): Flow<UpdateStatus> = flow {
        val current = getCurrentVersion()
        try {
            val latest = releaseChecker.fetchLatestRelease()
            if (current == "none" || isNewerVersion(latest.version, current)) {
                emit(UpdateStatus.UpdateAvailable(currentVersion = current, newVersion = latest))
            } else {
                emit(UpdateStatus.UpToDate(currentVersion = current))
            }
        } catch (e: Exception) {
            emit(UpdateStatus.UpdateFailed("Failed to check for updates: ${e.message}", e))
        }
    }.flowOn(Dispatchers.IO)

    override fun downloadAndInstall(versionInfo: ExtractorVersionInfo): Flow<UpdateStatus> = flow {
        val tempFile = File(binDir, "yt-dlp.tmp")

        try {
            val request = Request.Builder()
                .url(versionInfo.downloadUrl)
                .header("User-Agent", "YTMusic-Extractor-Updater/1.0")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                emit(UpdateStatus.UpdateFailed("Download HTTP error: ${response.code}"))
                return@flow
            }

            val body = response.body
            if (body == null) {
                emit(UpdateStatus.UpdateFailed("Download body was null"))
                return@flow
            }

            val totalBytes = body.contentLength()
            var bytesDownloaded = 0L

            body.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    var lastProgressEmit = 0L

                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesDownloaded += read

                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastProgressEmit > 200L || bytesDownloaded == totalBytes) {
                            val progress = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes else 0f
                            emit(
                                UpdateStatus.Downloading(
                                    progress = progress,
                                    bytesDownloaded = bytesDownloaded,
                                    totalBytes = totalBytes
                                )
                            )
                            lastProgressEmit = currentTime
                        }
                    }
                    output.flush()
                }
            }

            // Verify checksum if provided
            if (!versionInfo.checksumSha256.isNullOrBlank()) {
                val computedHash = computeSha256(tempFile)
                if (!computedHash.equals(versionInfo.checksumSha256, ignoreCase = true)) {
                    tempFile.delete()
                    emit(
                        UpdateStatus.UpdateFailed(
                            "Checksum mismatch: expected ${versionInfo.checksumSha256}, got $computedHash"
                        )
                    )
                    return@flow
                }
                emit(UpdateStatus.Verified(versionInfo.version))
            }

            // Atomic rename to replace existing binary
            if (binaryFile.exists()) {
                binaryFile.delete()
            }
            if (!tempFile.renameTo(binaryFile)) {
                // Fallback copy if rename fails
                tempFile.copyTo(binaryFile, overwrite = true)
                tempFile.delete()
            }

            // Ensure executable permissions
            binaryFile.setExecutable(true, false)
            binaryFile.setReadable(true, false)

            try {
                Runtime.getRuntime().exec(arrayOf("chmod", "755", binaryFile.absolutePath)).waitFor()
            } catch (_: Exception) {
                // Ignore if chmod command unavailable
            }

            // Save new version
            prefs.edit().putString("current_version", versionInfo.version).apply()

            emit(UpdateStatus.UpdateSuccess(versionInfo.version))
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            emit(UpdateStatus.UpdateFailed("Installation failed: ${e.message}", e))
        }
    }.flowOn(Dispatchers.IO)

    private fun isNewerVersion(remoteVersion: String, localVersion: String): Boolean {
        val cleanRemote = remoteVersion.trimStart('v', 'r').replace(".", "")
        val cleanLocal = localVersion.trimStart('v', 'r').replace(".", "")
        return (cleanRemote.toLongOrNull() ?: 0L) > (cleanLocal.toLongOrNull() ?: 0L)
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
