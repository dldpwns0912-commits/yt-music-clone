package com.ytmusic.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.ytmusic.core.database.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrack(track: TrackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTracks(tracks: List<TrackEntity>)

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun getTrackById(id: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE id = :id")
    fun observeTrackById(id: String): Flow<TrackEntity?>

    @Query("SELECT * FROM tracks WHERE isDownloaded = 1 ORDER BY downloadedAt DESC")
    fun observeDownloadedTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE isDownloaded = 1 ORDER BY downloadedAt DESC")
    suspend fun getDownloadedTracks(): List<TrackEntity>

    @Query("""
        UPDATE tracks 
        SET isDownloaded = :isDownloaded, 
            localFilePath = :localPath, 
            downloadedAt = :downloadedAt, 
            fileSize = :fileSize,
            audioQuality = :quality
        WHERE id = :id
    """)
    suspend fun updateDownloadStatus(
        id: String,
        isDownloaded: Boolean,
        localPath: String?,
        downloadedAt: Long?,
        fileSize: Long,
        quality: String?
    )

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun deleteTrack(id: String)

    @Query("""
        SELECT * FROM tracks 
        WHERE title LIKE '%' || :query || '%' 
           OR artist LIKE '%' || :query || '%' 
           OR album LIKE '%' || :query || '%'
        ORDER BY title ASC
    """)
    fun searchTracks(query: String): Flow<List<TrackEntity>>
}
