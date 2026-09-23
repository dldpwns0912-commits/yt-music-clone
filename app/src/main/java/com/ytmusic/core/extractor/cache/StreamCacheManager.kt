package com.ytmusic.core.extractor.cache

import com.ytmusic.core.extractor.model.ExtractionResult
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamCacheManager @Inject constructor() {

    private val maxCacheEntries = 100
    private val cache = ConcurrentHashMap<String, ExtractionResult>()
    private val accessOrder = mutableListOf<String>()
    private val lock = Any()

    /**
     * Retrieves valid, unexpired stream data for [videoId].
     * Returns null if entry does not exist or all stream URLs have expired.
     */
    fun get(videoId: String): ExtractionResult? {
        val result = cache[videoId] ?: return null

        if (result.isAllExpired()) {
            evict(videoId)
            return null
        }

        synchronized(lock) {
            accessOrder.remove(videoId)
            accessOrder.add(videoId)
        }

        return result
    }

    /**
     * Caches [result] for [videoId], evicting oldest entries if capacity is reached.
     */
    fun put(videoId: String, result: ExtractionResult) {
        if (result.audioStreams.isEmpty() || result.isAllExpired()) return

        synchronized(lock) {
            if (cache.size >= maxCacheEntries && !cache.containsKey(videoId)) {
                // Evict the least recently used entry
                if (accessOrder.isNotEmpty()) {
                    val oldestKey = accessOrder.removeAt(0)
                    cache.remove(oldestKey)
                }
            }
            cache[videoId] = result
            accessOrder.remove(videoId)
            accessOrder.add(videoId)
        }
    }

    /**
     * Explicitly evicts an entry by videoId.
     */
    fun evict(videoId: String) {
        synchronized(lock) {
            cache.remove(videoId)
            accessOrder.remove(videoId)
        }
    }

    /**
     * Cleans up all expired entries across the cache.
     */
    fun evictExpired() {
        synchronized(lock) {
            val iterator = cache.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.value.isAllExpired()) {
                    iterator.remove()
                    accessOrder.remove(entry.key)
                }
            }
        }
    }

    /**
     * Clears all cached stream entries.
     */
    fun clear() {
        synchronized(lock) {
            cache.clear()
            accessOrder.clear()
        }
    }

    val size: Int get() = cache.size
}
