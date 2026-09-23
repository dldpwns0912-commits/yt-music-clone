package com.ytmusic.core.extractor.model

data class Thumbnail(
    val url: String,
    val width: Int = 0,
    val height: Int = 0
)

data class TrackMetadata(
    val id: String,
    val title: String,
    val artist: String,
    val artistId: String? = null,
    val album: String? = null,
    val albumId: String? = null,
    val durationMs: Long = 0L,
    val thumbnailUrl: String = "",
    val thumbnails: List<Thumbnail> = emptyList(),
    val isLive: Boolean = false,
    val viewCount: Long? = null,
    val releaseYear: Int? = null
)
