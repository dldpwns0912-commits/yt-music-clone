package com.ytmusic.core.extractor

import com.ytmusic.core.extractor.model.AudioQuality
import com.ytmusic.core.extractor.model.ExtractionResult
import com.ytmusic.core.extractor.model.TrackMetadata

interface YoutubeStreamExtractor {
    suspend fun extractStream(videoId: String, forceRefresh: Boolean = false): ExtractionResult

    suspend fun extractStreamUrl(
        videoId: String,
        quality: AudioQuality = AudioQuality.HIGH
    ): String

    suspend fun searchVideos(query: String): List<TrackMetadata>

    suspend fun fetchPlaylist(playlistId: String): PlaylistInfo?
}
