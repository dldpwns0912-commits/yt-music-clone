package com.ytmusic.core.extractor.model

data class ExtractorVersionInfo(
    val version: String,
    val releaseDate: String,
    val releaseNotes: String = "",
    val downloadUrl: String,
    val checksumSha256: String? = null,
    val binarySize: Long = 0L
)

sealed interface UpdateStatus {
    data class UpToDate(val currentVersion: String) : UpdateStatus
    data class UpdateAvailable(
        val currentVersion: String,
        val newVersion: ExtractorVersionInfo
    ) : UpdateStatus
    data class Downloading(
        val progress: Float,
        val bytesDownloaded: Long,
        val totalBytes: Long
    ) : UpdateStatus
    data class Verified(val version: String) : UpdateStatus
    data class UpdateSuccess(val newVersion: String) : UpdateStatus
    data class UpdateFailed(val reason: String, val cause: Throwable? = null) : UpdateStatus
}
