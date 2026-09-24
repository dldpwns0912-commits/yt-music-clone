package com.ytmusic.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ytmusic.core.database.dao.PlaybackHistoryDao
import com.ytmusic.core.database.dao.PlaylistDao
import com.ytmusic.core.database.dao.SearchHistoryDao
import com.ytmusic.core.database.dao.TrackDao
import com.ytmusic.core.database.entity.PlaybackHistoryEntity
import com.ytmusic.core.database.entity.PlaylistEntity
import com.ytmusic.core.database.entity.PlaylistTrackCrossRef
import com.ytmusic.core.database.entity.SearchHistoryEntity
import com.ytmusic.core.database.entity.TrackEntity

@Database(
    entities = [
        TrackEntity::class,
        PlaylistEntity::class,
        PlaylistTrackCrossRef::class,
        PlaybackHistoryEntity::class,
        SearchHistoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class YTMusicDatabase : RoomDatabase() {

    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun playbackHistoryDao(): PlaybackHistoryDao
    abstract fun searchHistoryDao(): SearchHistoryDao

    companion object {
        const val DATABASE_NAME = "yt_music_database.db"

        val PREPOPULATE_CALLBACK = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                val now = System.currentTimeMillis()
                db.execSQL(
                    "INSERT INTO playlists (id, name, description, isSystemGenerated, createdAt, updatedAt) " +
                    "VALUES ('favorites', 'Favorite Tracks', 'Your loved tracks', 1, $now, $now)"
                )
                db.execSQL(
                    "INSERT INTO playlists (id, name, description, isSystemGenerated, createdAt, updatedAt) " +
                    "VALUES ('downloads', 'Offline Downloads', 'Downloaded tracks for offline playback', 1, $now, $now)"
                )
            }
        }
    }
}
