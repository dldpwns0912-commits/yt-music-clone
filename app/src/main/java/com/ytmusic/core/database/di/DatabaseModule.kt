package com.ytmusic.core.database.di

import android.content.Context
import androidx.room.Room
import com.ytmusic.core.database.YTMusicDatabase
import com.ytmusic.core.database.dao.PlaybackHistoryDao
import com.ytmusic.core.database.dao.PlaylistDao
import com.ytmusic.core.database.dao.SearchHistoryDao
import com.ytmusic.core.database.dao.TrackDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): YTMusicDatabase {
        return Room.databaseBuilder(
            context,
            YTMusicDatabase::class.java,
            YTMusicDatabase.DATABASE_NAME
        )
            .addCallback(YTMusicDatabase.PREPOPULATE_CALLBACK)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideTrackDao(database: YTMusicDatabase): TrackDao = database.trackDao()

    @Provides
    @Singleton
    fun providePlaylistDao(database: YTMusicDatabase): PlaylistDao = database.playlistDao()

    @Provides
    @Singleton
    fun providePlaybackHistoryDao(database: YTMusicDatabase): PlaybackHistoryDao =
        database.playbackHistoryDao()

    @Provides
    @Singleton
    fun provideSearchHistoryDao(database: YTMusicDatabase): SearchHistoryDao =
        database.searchHistoryDao()
}
