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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InnerTubeClient @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun getStreamInfo(
        videoId: String,
        clientType: InnerTubeClientType = InnerTubeClientType.ANDROID_MUSIC
    ): ExtractionResult = withContext(Dispatchers.IO) {
        val payload = buildPlayerPayload(videoId, clientType)
        val requestBody = payload.toString().toRequestBody(jsonMediaType)

        val requestBuilder = Request.Builder()
            .url("https://music.youtube.com/youtubei/v1/player?prettyPrint=false")
            .post(requestBody)
            .header("User-Agent", clientType.userAgent)
            .header("Content-Type", "application/json")
            .header("X-YouTube-Client-Name", when (clientType) {
                InnerTubeClientType.ANDROID_MUSIC -> "21"
                InnerTubeClientType.WEB_REMIX -> "67"
                InnerTubeClientType.IOS -> "5"
            })
            .header("X-YouTube-Client-Version", clientType.clientVersion)

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

    private fun buildPlayerPayload(videoId: String, clientType: InnerTubeClientType): JSONObject {
        val root = JSONObject()
        root.put("videoId", videoId)

        val context = JSONObject()
        val client = JSONObject()
        client.put("clientName", clientType.clientName)
        client.put("clientVersion", clientType.clientVersion)
        client.put("hl", "en")
        client.put("gl", "US")

        when (clientType) {
            InnerTubeClientType.ANDROID_MUSIC -> {
                client.put("androidSdkVersion", 34)
                client.put("osName", "Android")
                client.put("osVersion", "14")
            }
            InnerTubeClientType.IOS -> {
                client.put("deviceModel", "iPhone16,2")
                client.put("osName", "iOS")
                client.put("osVersion", "17.5.1")
            }
            InnerTubeClientType.WEB_REMIX -> {
                client.put("originalUrl", "https://music.youtube.com/watch?v=$videoId")
            }
        }
        context.put("client", client)
        root.put("context", context)

        val playbackContext = JSONObject()
        val contentPlaybackContext = JSONObject()
        contentPlaybackContext.put("signatureTimestamp", 19700)
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
                "UNPLAYABLE" ->
                    throw ExtractorException.ContentUnavailableException(videoId, reason)
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
        val bestThumbnailUrl = thumbnails.maxByOrNull { it.width * it.height }?.url ?: ""

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

        // 3. Extract Global Loudness if present
        val loudnessDb = root.optJSONObject("playerConfig")
            ?.optJSONObject("audioConfig")
            ?.optDouble("loudnessDb")
            ?.takeIf { !it.isNaN() }

        // 4. Parse Streaming Formats (audio streams)
        val streamingData = root.optJSONObject("streamingData")
            ?: throw ExtractorException.ParsingException("No streamingData found for video $videoId")

        val audioStreams = mutableListOf<AudioStream>()

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
                // Check cipher/signatureCipher
                val cipher = format.optString("signatureCipher", format.optString("cipher", ""))
                if (cipher.isNotEmpty()) {
                    extractUrlFromCipher(cipher)
                } else {
                    null
                }
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

        val source = when (clientType) {
            InnerTubeClientType.ANDROID_MUSIC -> ExtractorSource.INNERTUBE_ANDROID
            InnerTubeClientType.WEB_REMIX -> ExtractorSource.INNERTUBE_WEB
            InnerTubeClientType.IOS -> ExtractorSource.FALLBACK
        }

        return ExtractionResult(
            videoId = videoId,
            metadata = metadata,
            audioStreams = audioStreams,
            source = source
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
