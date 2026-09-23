package com.ytmusic.core.extractor

import com.ytmusic.core.extractor.cache.StreamCacheManager
import com.ytmusic.core.extractor.innertube.InnerTubeFallbackExtractor
import com.ytmusic.core.extractor.model.AudioQuality
import com.ytmusic.core.extractor.model.ExtractionResult
import com.ytmusic.core.extractor.model.ExtractorException
import com.ytmusic.core.extractor.model.ExtractorSource
import com.ytmusic.core.extractor.ytdlp.YtDlpExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CompositeStreamExtractor @Inject constructor(
    private val ytDlpExtractor: YtDlpExtractor,
    private val innerTubeFallbackExtractor: InnerTubeFallbackExtractor,
    private val cacheManager: StreamCacheManager
) : YoutubeStreamExtractor {

    override suspend fun extractStream(
        videoId: String,
        forceRefresh: Boolean
    ): ExtractionResult = withContext(Dispatchers.IO) {
        // 1. Fast Cache Check
        if (!forceRefresh) {
            val cached = cacheManager.get(videoId)
            if (cached != null && !cached.isAllExpired()) {
                return@withContext cached.copy(source = ExtractorSource.CACHE)
            }
        }

        val errors = mutableListOf<Throwable>()

        // 2. Primary Strategy: Dynamic yt-dlp binary if available
        if (ytDlpExtractor.isSupported()) {
            try {
                val result = ytDlpExtractor.extract(videoId)
                cacheManager.put(videoId, result)
                return@withContext result
            } catch (e: ExtractorException.AgeRestrictedException) {
                throw e
            } catch (e: Throwable) {
                errors.add(e)
            }
        }

        // 3. Fallback Strategy: InnerTube Direct HTTPS Extractor
        try {
            val result = innerTubeFallbackExtractor.extract(videoId)
            cacheManager.put(videoId, result)
            return@withContext result
        } catch (e: Throwable) {
            errors.add(e)
        }

        throw ExtractorException.AllExtractorsFailedException(videoId, errors)
    }

    override suspend fun extractStreamUrl(
        videoId: String,
        quality: AudioQuality
    ): String {
        val result = extractStream(videoId)
        val bestStream = result.selectBestStream(quality)
            ?: throw ExtractorException.ParsingException("No playable stream matching quality $quality for video $videoId")
        return bestStream.url
    }
}
