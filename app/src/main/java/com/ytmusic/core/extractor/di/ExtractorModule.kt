package com.ytmusic.core.extractor.di

import com.ytmusic.core.extractor.CompositeStreamExtractor
import com.ytmusic.core.extractor.YoutubeStreamExtractor
import com.ytmusic.core.extractor.updater.ExtractorUpdateManager
import com.ytmusic.core.extractor.updater.ExtractorUpdateManagerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ExtractorModule {

    @Binds
    @Singleton
    abstract fun bindYoutubeStreamExtractor(
        impl: CompositeStreamExtractor
    ): YoutubeStreamExtractor

    @Binds
    @Singleton
    abstract fun bindExtractorUpdateManager(
        impl: ExtractorUpdateManagerImpl
    ): ExtractorUpdateManager
}
