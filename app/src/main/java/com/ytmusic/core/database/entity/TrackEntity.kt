package com.ytmusic.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val artist: String,
    val artistId: String? = null,
    val album: String? = null,
    val albumId: String? = null,
    val durationMs: Long = 0L,
    val thumbnailUrl: String = "",
    val localFilePath: String? = null,
    val isDownloaded: Boolean = false,
    val downloadedAt: Long? = null,
    val audioQuality: String? = null,
    val fileSize: Long = 0L,
    val mimeType: String = "audio/mp4"
)
