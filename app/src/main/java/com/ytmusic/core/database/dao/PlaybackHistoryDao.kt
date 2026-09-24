package com.ytmusic.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ytmusic.core.database.entity.PlaybackHistoryEntity
import com.ytmusic.core.database.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: PlaybackHistoryEntity)

    @Query("""
        SELECT t.* FROM tracks t
        INNER JOIN (
            SELECT trackId, MAX(playedAt) AS maxPlayedAt
            FROM playback_history
            GROUP BY trackId
            ORDER BY maxPlayedAt DESC
            LIMIT :limit
        ) h ON t.id = h.trackId
        ORDER BY h.maxPlayedAt DESC
    """)
    fun observeRecentlyPlayedTracks(limit: Int = 50): Flow<List<TrackEntity>>

    @Query("""
        SELECT t.* FROM tracks t
        INNER JOIN (
            SELECT trackId, COUNT(*) AS playCount, MAX(playedAt) AS lastPlayedAt
            FROM playback_history
            GROUP BY trackId
            ORDER BY playCount DESC, lastPlayedAt DESC
            LIMIT :limit
        ) h ON t.id = h.trackId
        ORDER BY h.playCount DESC, h.lastPlayedAt DESC
    """)
    fun observeMostPlayedTracks(limit: Int = 50): Flow<List<TrackEntity>>

    @Query("DELETE FROM playback_history")
    suspend fun clearHistory()
}
