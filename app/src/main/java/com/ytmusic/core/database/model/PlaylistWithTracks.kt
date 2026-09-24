package com.ytmusic.core.database.model

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import com.ytmusic.core.database.entity.PlaylistEntity
import com.ytmusic.core.database.entity.PlaylistTrackCrossRef
import com.ytmusic.core.database.entity.TrackEntity

data class PlaylistWithTracks(
    @Embedded
    val playlist: PlaylistEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = PlaylistTrackCrossRef::class,
            parentColumn = "playlistId",
            entityColumn = "trackId"
        )
    )
    val tracks: List<TrackEntity>
)
