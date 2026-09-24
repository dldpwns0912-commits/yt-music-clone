package com.ytmusic.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playback_history",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("trackId"),
        Index("playedAt")
    ]
)
data class PlaybackHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val historyId: Long = 0,
    val trackId: String,
    val playedAt: Long = System.currentTimeMillis(),
    val playbackDurationMs: Long = 0L,
    val completed: Boolean = false
)
