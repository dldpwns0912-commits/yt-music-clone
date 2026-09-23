package com.ytmusic.core.extractor

import com.ytmusic.core.extractor.model.AudioQuality
import com.ytmusic.core.extractor.model.ExtractionResult

interface YoutubeStreamExtractor {
    /**
     * Extracts full stream info and metadata for [videoId].
     * Utilizes stream cache if available and not expired unless [forceRefresh] is true.
     */
    suspend fun extractStream(videoId: String, forceRefresh: Boolean = false): ExtractionResult

    /**
     * Convenience method returning the direct playable audio stream URL for [videoId]
     * according to the specified [quality].
     */
    suspend fun extractStreamUrl(
        videoId: String,
        quality: AudioQuality = AudioQuality.HIGH
    ): String
}
