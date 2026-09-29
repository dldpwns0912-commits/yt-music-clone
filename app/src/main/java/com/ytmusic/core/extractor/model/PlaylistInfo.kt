package com.ytmusic.core.extractor.model

data class PlaylistInfo(
    val id: String,
    val title: String,
    val channelName: String = "",
    val trackCount: Int = 0,
    val tracks: List<TrackMetadata> = emptyList(),
    val thumbnailUrl: String = ""
)
