package com.ytmusic.core.extractor.model

enum class ExtractorSource {
    CACHE,
    YT_DLP,
    INNERTUBE_ANDROID,
    INNERTUBE_WEB,
    FALLBACK
}

data class ExtractionResult(
    val videoId: String,
    val metadata: TrackMetadata,
    val audioStreams: List<AudioStream>,
    val source: ExtractorSource,
    val extractedAtMs: Long = System.currentTimeMillis()
) {
    /**
     * Resolves the most appropriate AudioStream based on user preference and codec priority.
     * High quality favors Opus ~160kbps (itag 251) or AAC ~140kbps (itag 140).
     * Medium quality favors Opus ~70kbps (itag 250) or AAC ~70kbps.
     * Low quality favors Opus ~50kbps (itag 249).
     */
    fun selectBestStream(quality: AudioQuality = AudioQuality.HIGH): AudioStream? {
        if (audioStreams.isEmpty()) return null

        val validStreams = audioStreams.filter { !it.isExpired() }
        val streamPool = validStreams.ifEmpty { audioStreams }

        return when (quality) {
            AudioQuality.HIGH -> {
                // Highest bitrate first, preferring Opus over AAC if similar bitrate
                streamPool.maxWithOrNull(
                    compareBy<AudioStream> { it.bitrate }
                        .thenBy { it.codec == AudioCodec.OPUS }
                )
            }
            AudioQuality.MEDIUM -> {
                // Moderate bitrate around 70k - 130k
                val mediumTargets = streamPool.filter { it.bitrate in 60_000..130_000 }
                if (mediumTargets.isNotEmpty()) {
                    mediumTargets.maxByOrNull { it.bitrate }
                } else {
                    // Fallback to closest under 160k
                    streamPool.sortedBy { it.bitrate }.getOrNull(streamPool.size / 2)
                        ?: streamPool.firstOrNull()
                }
            }
            AudioQuality.LOW -> {
                // Lowest bitrate to save bandwidth
                streamPool.minByOrNull { it.bitrate }
            }
        }
    }

    fun isAllExpired(): Boolean {
        return audioStreams.isNotEmpty() && audioStreams.all { it.isExpired() }
    }
}
