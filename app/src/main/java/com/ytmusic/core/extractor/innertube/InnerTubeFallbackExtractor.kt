package com.ytmusic.core.extractor.innertube

import com.ytmusic.core.extractor.model.ExtractionResult
import com.ytmusic.core.extractor.model.ExtractorException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InnerTubeFallbackExtractor @Inject constructor(
    private val client: InnerTubeClient
) {
    /**
     * Extracts stream info with multi-client cascading fallback:
     * ANDROID_MUSIC -> WEB_REMIX -> IOS
     */
    suspend fun extract(videoId: String): ExtractionResult {
        val errors = mutableListOf<Throwable>()

        // 1. First attempt: ANDROID_MUSIC (clean direct streams)
        try {
            return client.getStreamInfo(videoId, InnerTubeClientType.ANDROID_MUSIC)
        } catch (e: ExtractorException.AgeRestrictedException) {
            throw e // Age restriction cannot be bypassed with simple client switch
        } catch (e: Throwable) {
            errors.add(e)
        }

        // 2. Second attempt: WEB_REMIX
        try {
            return client.getStreamInfo(videoId, InnerTubeClientType.WEB_REMIX)
        } catch (e: ExtractorException.AgeRestrictedException) {
            throw e
        } catch (e: Throwable) {
            errors.add(e)
        }

        // 3. Third attempt: IOS client
        try {
            return client.getStreamInfo(videoId, InnerTubeClientType.IOS)
        } catch (e: Throwable) {
            errors.add(e)
        }

        throw ExtractorException.AllExtractorsFailedException(videoId, errors)
    }
}
