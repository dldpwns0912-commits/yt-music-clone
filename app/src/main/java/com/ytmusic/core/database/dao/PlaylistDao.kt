package com.ytmusic.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.ytmusic.core.database.entity.PlaylistEntity
import com.ytmusic.core.database.entity.PlaylistTrackCrossRef
import com.ytmusic.core.database.model.PlaylistWithTracks
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylist(playlist: PlaylistEntity)

    @Query("SELECT * FROM playlists ORDER BY isSystemGenerated DESC, updatedAt DESC")
    fun observeAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylistById(id: String): PlaylistEntity?

    @Transaction
    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    fun observePlaylistWithTracks(playlistId: String): Flow<PlaylistWithTracks?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrossRef(crossRef: PlaylistTrackCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrossRefs(crossRefs: List<PlaylistTrackCrossRef>)

    @Query("DELETE FROM playlist_track_cross_ref WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String)

    @Query("DELETE FROM playlist_track_cross_ref WHERE playlistId = :playlistId")
    suspend fun clearPlaylistTracks(playlistId: String)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: String)

    @Transaction
    suspend fun reorderTracks(playlistId: String, trackIdsInOrder: List<String>) {
        clearPlaylistTracks(playlistId)
        val refs = trackIdsInOrder.mapIndexed { index, trackId ->
            PlaylistTrackCrossRef(
                playlistId = playlistId,
                trackId = trackId,
                position = index,
                addedAt = System.currentTimeMillis()
            )
        }
        insertCrossRefs(refs)
    }
}
