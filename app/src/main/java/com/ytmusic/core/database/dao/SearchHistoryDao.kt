package com.ytmusic.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ytmusic.core.database.entity.SearchHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSearch(search: SearchHistoryEntity)

    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecentSearches(limit: Int = 20): Flow<List<SearchHistoryEntity>>

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun deleteSearch(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearAllSearches()
}
