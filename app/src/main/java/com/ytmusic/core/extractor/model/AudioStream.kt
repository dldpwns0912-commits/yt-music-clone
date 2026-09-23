package com.ytmusic.core.extractor.model

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

enum class AudioQuality {
    LOW,
    MEDIUM,
    HIGH;

    companion object {
        fun fromPreference(pref: String?): AudioQuality = when (pref?.uppercase()) {
            "LOW" -> LOW
            "HIGH" -> HIGH
            else -> MEDIUM
        }
    }
}

enum class AudioCodec {
    OPUS,
    AAC,
    UNKNOWN;

    companion object {
        fun fromMimeType(mimeType: String?): AudioCodec = when {
            mimeType == null -> UNKNOWN
            mimeType.contains("opus", ignoreCase = true) -> OPUS
            mimeType.contains("mp4a", ignoreCase = true) || mimeType.contains("aac", ignoreCase = true) -> AAC
            else -> UNKNOWN
        }
    }
}

data class AudioStream(
    val url: String,
    val itag: Int,
    val mimeType: String,
    val codec: AudioCodec = AudioCodec.fromMimeType(mimeType),
    val bitrate: Int,
    val sampleRate: Int = 44100,
    val contentLength: Long = 0L,
    val approxDurationMs: Long = 0L,
    val loudnessDb: Double? = null,
    val expiresAtMs: Long = extractExpiryEpochMs(url)
) {
    /**
     * Checks if the stream URL has expired or will expire within [bufferMs].
     * YouTube playback URLs generally expire 6 hours after generation.
     */
    fun isExpired(bufferMs: Long = 60_000L): Boolean {
        if (expiresAtMs <= 0L) return false
        return System.currentTimeMillis() + bufferMs >= expiresAtMs
    }

    companion object {
        fun extractExpiryEpochMs(url: String): Long {
            return try {
                val uri = URI(url)
                val query = uri.rawQuery ?: return 0L
                val pairs = query.split("&")
                for (pair in pairs) {
                    val idx = pair.indexOf("=")
                    if (idx > 0) {
                        val key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8.name())
                        if (key == "expire") {
                            val value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8.name())
                            val expireSeconds = value.toLongOrNull() ?: 0L
                            return expireSeconds * 1000L
                        }
                    }
                }
                0L
            } catch (_: Exception) {
                0L
            }
        }
    }
}
