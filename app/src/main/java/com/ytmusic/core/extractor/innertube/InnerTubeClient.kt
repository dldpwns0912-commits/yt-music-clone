package com.ytmusic.core.extractor.innertube

import com.ytmusic.core.extractor.model.AudioCodec
import com.ytmusic.core.extractor.model.AudioStream
import com.ytmusic.core.extractor.model.ExtractionResult
import com.ytmusic.core.extractor.model.ExtractorException
import com.ytmusic.core.extractor.model.ExtractorSource
import com.ytmusic.core.extractor.model.Thumbnail
import com.ytmusic.core.extractor.model.TrackMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InnerTubeClient @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    @Volatile
    private var cachedVisitorId: String? = null

    private suspend fun getVisitorId(): String = withContext(Dispatchers.IO) {
        cachedVisitorId?.let { return@withContext it }
        try {
            val req = Request.Builder()
                .url("https://www.youtube.com/sw.js_data")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
            val resp = okHttpClient.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            val matcher = Pattern.compile("\"(Cgt[^\"]+)\"").matcher(body)
            if (matcher.find()) {
                val visitor = matcher.group(1)
                cachedVisitorId = visitor
                return@withContext visitor
            }
        } catch (_: Exception) {}
        return@withContext "CgtmbHpSY2hpMWN0USjS0-jVBjIKCgJLUhIEGgAgamLfAgrcAjIyLllUPWpBd2l6R0tZ"
    }

    suspend fun getStreamInfo(
        videoId: String,
        clientType: InnerTubeClientType = InnerTubeClientType.VISIONOS
    ): ExtractionResult = withContext(Dispatchers.IO) {
        val visitorId = getVisitorId()
        val payload = buildPlayerPayload(videoId, clientType)
        val requestBody = payload.toString().toRequestBody(jsonMediaType)

        val requestBuilder = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
            .post(requestBody)
            .header("User-Agent", clientType.userAgent)
            .header("Content-Type", "application/json")
            .header("X-YouTube-Client-Name", clientType.clientNumber)
            .header("X-YouTube-Client-Version", clientType.clientVersion)
            .header("Origin", "https://www.youtube.com")
            .header("X-Goog-Visitor-Id", visitorId)

        clientType.referer?.let { referer ->
            requestBuilder.header("Referer", referer)
        }

        val response = try {
            okHttpClient.newCall(requestBuilder.build()).execute()
        } catch (e: Exception) {
            throw ExtractorException.NetworkException("Failed to connect to YouTube InnerTube: ${e.message}", e)
        }

        val bodyString = response.body?.string()
            ?: throw ExtractorException.ParsingException("Empty response body from YouTube InnerTube")

        if (!response.isSuccessful) {
            throw ExtractorException.NetworkException(
                "InnerTube HTTP error code: ${response.code}, message: ${response.message}"
            )
        }

        parsePlayerResponse(videoId, bodyString, clientType)
    }

    suspend fun search(query: String): List<TrackMetadata> = withContext(Dispatchers.IO) {
        val root = JSONObject().apply {
            put("query", query)
            val context = JSONObject().apply {
                val client = JSONObject().apply {
                    put("clientName", "WEB")
                    put("clientVersion", "2.20240920.01.00")
                    put("hl", "ko")
                    put("gl", "KR")
                }
                put("client", client)
            }
            put("context", context)
        }

        val req = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/search?prettyPrint=false")
            .post(root.toString().toRequestBody(jsonMediaType))
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("Content-Type", "application/json")
            .build()

        try {
            val res = okHttpClient.newCall(req).execute()
            val body = res.body?.string() ?: return@withContext emptyList()
            parseSearchResults(body)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseSearchResults(jsonString: String): List<TrackMetadata> {
        val results = mutableListOf<TrackMetadata>()
        try {
            val root = JSONObject(jsonString)
            val contents = root.optJSONObject("contents")
                ?.optJSONObject("twoColumnSearchResultsRenderer")
                ?.optJSONObject("primaryContents")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents") ?: return emptyList()

            for (i in 0 until contents.length()) {
                val section = contents.optJSONObject(i) ?: continue
                val itemSection = section.optJSONObject("itemSectionRenderer") ?: continue
                val items = itemSection.optJSONArray("contents") ?: continue

                for (j in 0 until items.length()) {
                    val item = items.optJSONObject(j) ?: continue
                    val video = item.optJSONObject("videoRenderer") ?: continue

                    val videoId = video.optString("videoId")
                    if (videoId.isNullOrBlank()) continue

                    val titleRuns = video.optJSONObject("title")?.optJSONArray("runs")
                    val title = titleRuns?.optJSONObject(0)?.optString("text") ?: "YouTube Track"

                    val ownerRuns = video.optJSONObject("ownerText")?.optJSONArray("runs")
                    val artist = ownerRuns?.optJSONObject(0)?.optString("text") ?: "Unknown Artist"

                    val thumbs = video.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                    val thumbUrl = if (thumbs != null && thumbs.length() > 0) {
                        thumbs.optJSONObject(thumbs.length() - 1)?.optString("url") ?: ""
                    } else "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

                    val durationStr = video.optJSONObject("lengthText")?.optString("simpleText") ?: ""
                    val durationMs = parseDurationMs(durationStr)

                    results.add(
                        TrackMetadata(
                            id = videoId,
                            title = title,
                            artist = artist,
                            thumbnailUrl = thumbUrl,
                            durationMs = durationMs
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        return results
    }

    private fun parseDurationMs(durationStr: String): Long {
        if (durationStr.isBlank()) return 0L
        val parts = durationStr.split(":").mapNotNull { it.trim().toLongOrNull() }
        return when (parts.size) {
            3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000L
            2 -> (parts[0] * 60 + parts[1]) * 1000L
            1 -> parts[0] * 1000L
            else -> 0L
        }
    }

    private fun buildPlayerPayload(videoId: String, clientType: InnerTubeClientType): JSONObject {
        val root = JSONObject()
        root.put("videoId", videoId)
        root.put("contentCheckOk", true)
        root.put("racyCheckOk", true)

        val context = JSONObject()
        val client = JSONObject()
        client.put("clientName", clientType.clientName)
        client.put("clientVersion", clientType.clientVersion)
        client.put("hl", "en")
        client.put("gl", "US")

        when (clientType) {
            InnerTubeClientType.VISIONOS -> {
                client.put("deviceMake", "Apple")
                client.put("deviceModel", "RealityDevice17,1")
                client.put("osName", "visionOS")
                client.put("osVersion", "26.5.23O471")
            }
            InnerTubeClientType.ANDROID_MUSIC -> {
                client.put("androidSdkVersion", 34)
                client.put("osName", "Android")
                client.put("osVersion", "14")
            }
            InnerTubeClientType.WEB, InnerTubeClientType.WEB_REMIX -> {
                client.put("originalUrl", "https://www.youtube.com/watch?v=$videoId")
            }
        }
        context.put("client", client)
        root.put("context", context)

        val playbackContext = JSONObject()
        val contentPlaybackContext = JSONObject()
        contentPlaybackContext.put("html5Preference", "HTML5_PREF_WANTS")
        contentPlaybackContext.put("signatureTimestamp", 20717)
        playbackContext.put("contentPlaybackContext", contentPlaybackContext)
        root.put("playbackContext", playbackContext)

        return root
    }

    private fun parsePlayerResponse(
        videoId: String,
        jsonString: String,
        clientType: InnerTubeClientType
    ): ExtractionResult {
        val root = try {
            JSONObject(jsonString)
        } catch (e: Exception) {
            throw ExtractorException.ParsingException("Invalid JSON format from InnerTube response", e)
        }

        // 1. Check playabilityStatus
        val playabilityStatus = root.optJSONObject("playabilityStatus")
        val status = playabilityStatus?.optString("status") ?: "UNKNOWN"

        if (status != "OK") {
            val reason = playabilityStatus?.optString("reason") ?: "Status: $status"
            when (status) {
                "LOGIN_REQUIRED", "AGE_CHECK_REQUIRED" ->
                    throw ExtractorException.AgeRestrictedException(videoId)
                else ->
                    throw ExtractorException.ContentUnavailableException(videoId, reason)
            }
        }

        // 2. Parse Video Details
        val videoDetails = root.optJSONObject("videoDetails")
            ?: throw ExtractorException.ParsingException("Missing videoDetails in InnerTube response")

        val title = videoDetails.optString("title", "Unknown Title")
        val author = videoDetails.optString("author", "Unknown Artist")
        val lengthSeconds = videoDetails.optString("lengthSeconds", "0").toLongOrNull() ?: 0L
        val durationMs = lengthSeconds * 1000L
        val isLive = videoDetails.optBoolean("isLiveContent", false)
        val viewCount = videoDetails.optString("viewCount", "0").toLongOrNull()

        // Thumbnails
        val thumbnailObj = videoDetails.optJSONObject("thumbnail")
        val thumbnailArray = thumbnailObj?.optJSONArray("thumbnails") ?: JSONArray()
        val thumbnails = mutableListOf<Thumbnail>()
        for (i in 0 until thumbnailArray.length()) {
            val thumb = thumbnailArray.optJSONObject(i) ?: continue
            val url = thumb.optString("url")
            val w = thumb.optInt("width", 0)
            val h = thumb.optInt("height", 0)
            if (url.isNotEmpty()) {
                thumbnails.add(Thumbnail(url = url, width = w, height = h))
            }
        }
        val bestThumbnailUrl = thumbnails.maxByOrNull { it.width * it.height }?.url
            ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

        val metadata = TrackMetadata(
            id = videoId,
            title = title,
            artist = author,
            durationMs = durationMs,
            thumbnailUrl = bestThumbnailUrl,
            thumbnails = thumbnails,
            isLive = isLive,
            viewCount = viewCount
        )

        // 3. Extract Loudness
        val loudnessDb = root.optJSONObject("playerConfig")
            ?.optJSONObject("audioConfig")
            ?.optDouble("loudnessDb")
            ?.takeIf { !it.isNaN() }

        // 4. Parse Streaming Formats
        val streamingData = root.optJSONObject("streamingData")
            ?: throw ExtractorException.ParsingException("No streamingData found for video $videoId")

        val audioStreams = mutableListOf<AudioStream>()

        // Check for HLS Manifest Stream (m3u8 native ExoPlayer playback)
        val hlsManifestUrl = streamingData.optString("hlsManifestUrl", "")
        if (hlsManifestUrl.isNotBlank()) {
            audioStreams.add(
                AudioStream(
                    url = hlsManifestUrl,
                    itag = 251,
                    mimeType = "application/x-mpegURL",
                    codec = AudioCodec.OPUS,
                    bitrate = 160000,
                    sampleRate = 48000,
                    contentLength = 0L,
                    approxDurationMs = durationMs,
                    loudnessDb = loudnessDb
                )
            )
        }

        // Check adaptive formats with direct URLs
        val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats") ?: JSONArray()
        for (i in 0 until adaptiveFormats.length()) {
            val format = adaptiveFormats.optJSONObject(i) ?: continue
            val mimeType = format.optString("mimeType", "")
            if (!mimeType.startsWith("audio/")) continue

            val itag = format.optInt("itag", 0)
            val bitrate = format.optInt("bitrate", 0)
            val sampleRate = format.optString("audioSampleRate", "44100").toIntOrNull() ?: 44100
            val approxDurationMs = format.optString("approxDurationMs", "0").toLongOrNull() ?: durationMs
            val contentLength = format.optString("contentLength", "0").toLongOrNull() ?: 0L
            val formatLoudness = if (format.has("loudnessDb")) format.optDouble("loudnessDb") else loudnessDb

            val rawUrl = format.optString("url", "")
            val streamUrl = if (rawUrl.isNotEmpty()) {
                rawUrl
            } else {
                val cipher = format.optString("signatureCipher", format.optString("cipher", ""))
                if (cipher.isNotEmpty()) extractUrlFromCipher(cipher) else null
            }

            if (!streamUrl.isNullOrEmpty()) {
                audioStreams.add(
                    AudioStream(
                        url = streamUrl,
                        itag = itag,
                        mimeType = mimeType,
                        codec = AudioCodec.fromMimeType(mimeType),
                        bitrate = bitrate,
                        sampleRate = sampleRate,
                        contentLength = contentLength,
                        approxDurationMs = approxDurationMs,
                        loudnessDb = formatLoudness
                    )
                )
            }
        }

        if (audioStreams.isEmpty()) {
            throw ExtractorException.ParsingException("No playable audio streams discovered for video $videoId")
        }

        return ExtractionResult(
            videoId = videoId,
            metadata = metadata,
            audioStreams = audioStreams,
            source = ExtractorSource.INNERTUBE_ANDROID
        )
    }

    private fun extractUrlFromCipher(cipher: String): String? {
        return try {
            val params = cipher.split("&")
            var url: String? = null
            for (param in params) {
                val idx = param.indexOf("=")
                if (idx > 0) {
                    val key = param.substring(0, idx)
                    val value = URLDecoder.decode(param.substring(idx + 1), StandardCharsets.UTF_8.name())
                    if (key == "url") {
                        url = value
                    }
                }
            }
            url
        } catch (_: Exception) {
            null
        }
    }
}
