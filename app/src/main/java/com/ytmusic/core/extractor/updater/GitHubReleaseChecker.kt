package com.ytmusic.core.extractor.updater

import com.ytmusic.core.extractor.model.ExtractorException
import com.ytmusic.core.extractor.model.ExtractorVersionInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitHubReleaseChecker @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private val releaseApiUrl = "https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest"

    /**
     * Queries the latest release metadata from GitHub API.
     */
    suspend fun fetchLatestRelease(): ExtractorVersionInfo = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(releaseApiUrl)
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "YTMusic-Extractor-Updater/1.0")
            .build()

        val response = try {
            okHttpClient.newCall(request).execute()
        } catch (e: Exception) {
            throw ExtractorException.NetworkException("Failed to query GitHub release: ${e.message}", e)
        }

        val body = response.body?.string()
            ?: throw ExtractorException.ParsingException("Empty release response body from GitHub")

        if (!response.isSuccessful) {
            throw ExtractorException.NetworkException(
                "GitHub API returned HTTP ${response.code}: ${response.message}"
            )
        }

        parseReleaseJson(body)
    }

    private fun parseReleaseJson(jsonString: String): ExtractorVersionInfo {
        val root = try {
            JSONObject(jsonString)
        } catch (e: Exception) {
            throw ExtractorException.ParsingException("Failed to parse GitHub release JSON", e)
        }

        val tagName = root.optString("tag_name", "")
        val publishedAt = root.optString("published_at", "")
        val body = root.optString("body", "")
        val assets = root.optJSONArray("assets") ?: JSONArray()

        var downloadUrl: String? = null
        var binarySize = 0L
        var checksumUrl: String? = null

        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name", "")
            val url = asset.optString("browser_download_url", "")
            val size = asset.optLong("size", 0L)

            // Look for the standalone yt-dlp binary or script
            if (name == "yt-dlp" || name == "yt-dlp_linux") {
                downloadUrl = url
                binarySize = size
            } else if (name == "SHA2-256SUMS") {
                checksumUrl = url
            }
        }

        // Fallback to first available asset if exact name not matched
        if (downloadUrl == null && assets.length() > 0) {
            val fallbackAsset = assets.optJSONObject(0)
            downloadUrl = fallbackAsset?.optString("browser_download_url")
            binarySize = fallbackAsset?.optLong("size", 0L) ?: 0L
        }

        if (downloadUrl.isNullOrEmpty()) {
            throw ExtractorException.ParsingException("No compatible extractor assets found in GitHub release: $tagName")
        }

        val checksum = checksumUrl?.let { fetchChecksum(it, "yt-dlp") }

        return ExtractorVersionInfo(
            version = tagName,
            releaseDate = publishedAt,
            releaseNotes = body,
            downloadUrl = downloadUrl,
            checksumSha256 = checksum,
            binarySize = binarySize
        )
    }

    private fun fetchChecksum(checksumUrl: String, targetFilename: String): String? {
        return try {
            val request = Request.Builder()
                .url(checksumUrl)
                .header("User-Agent", "YTMusic-Extractor-Updater/1.0")
                .build()
            val response = okHttpClient.newCall(request).execute()
            val text = response.body?.string() ?: return null

            text.lineSequence()
                .firstOrNull { it.contains(targetFilename) }
                ?.split("\\s+".toRegex())
                ?.firstOrNull()
        } catch (_: Exception) {
            null
        }
    }
}
