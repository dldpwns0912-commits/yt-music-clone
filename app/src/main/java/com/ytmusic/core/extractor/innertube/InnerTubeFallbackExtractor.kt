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
     * Extracts stream info using VISIONOS (HLS / 0 auth), with fallback to WEB
     */
    suspend fun extract(videoId: String): ExtractionResult {
        val errors = mutableListOf<Throwable>()

        // 1. Primary: VISIONOS client (HLS audio/video streaming, unblocked)
        try {
            return client.getStreamInfo(videoId, InnerTubeClientType.VISIONOS)
        } catch (e: ExtractorException.AgeRestrictedException) {
            throw e
        } catch (e: Throwable) {
            errors.add(e)
        }

        // 2. Secondary: WEB client
        try {
            return client.getStreamInfo(videoId, InnerTubeClientType.WEB)
        } catch (e: ExtractorException.AgeRestrictedException) {
            throw e
        } catch (e: Throwable) {
            errors.add(e)
        }

        throw ExtractorException.AllExtractorsFailedException(videoId, errors)
    }
}
