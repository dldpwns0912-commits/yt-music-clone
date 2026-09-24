package com.ytmusic.core.media.player

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.ytmusic.core.database.dao.PlaybackHistoryDao
import com.ytmusic.core.database.dao.TrackDao
import com.ytmusic.core.database.entity.PlaybackHistoryEntity
import com.ytmusic.core.database.entity.TrackEntity
import com.ytmusic.core.extractor.YoutubeStreamExtractor
import com.ytmusic.core.extractor.model.AudioQuality
import com.ytmusic.core.extractor.model.TrackMetadata
import com.ytmusic.core.media.effects.AudioEffectsManager
import com.ytmusic.core.media.model.PlaybackState
import com.ytmusic.core.storage.StorageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicPlayerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    val exoPlayer: ExoPlayer,
    private val extractor: YoutubeStreamExtractor,
    private val storageManager: StorageManager,
    val audioEffectsManager: AudioEffectsManager,
    private val crossfadeManager: CrossfadeManager,
    private val trackDao: TrackDao,
    private val historyDao: PlaybackHistoryDao
) {
    private val tag = "MusicPlayerManager"
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _currentQueue = MutableStateFlow<List<TrackMetadata>>(emptyList())
    val currentQueue: StateFlow<List<TrackMetadata>> = _currentQueue.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    // Map videoId to extracted loudnessDb for volume normalization
    private val trackLoudnessCache = mutableMapOf<String, Double>()

    private var progressTrackingJob: Job? = null
    private var currentTrackPlayDurationMs = 0L
    private var hasRecordedHistoryForCurrentTrack = false

    init {
        setupPlayer()
        startProgressTracking()
    }

    private fun setupPlayer() {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playbackState.update { it.copy(isPlaying = isPlaying) }
            }

            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> {
                        _playbackState.update { it.copy(isLoading = true) }
                    }
                    Player.STATE_READY -> {
                        _playbackState.update {
                            it.copy(
                                isLoading = false,
                                durationMs = exoPlayer.duration.coerceAtLeast(0L),
                                bufferedPositionMs = exoPlayer.bufferedPosition.coerceAtLeast(0L)
                            )
                        }
                        // Attach audio effects if session id is valid
                        if (exoPlayer.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                            audioEffectsManager.attachToAudioSession(exoPlayer.audioSessionId)
                        }
                    }
                    Player.STATE_ENDED -> {
                        _playbackState.update { it.copy(isPlaying = false, isLoading = false) }
                        recordPlaybackHistory(completed = true)
                        handleTrackEnded()
                    }
                    Player.STATE_IDLE -> {
                        _playbackState.update { it.copy(isLoading = false) }
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val mediaId = mediaItem?.mediaId
                val track = _currentQueue.value.find { it.id == mediaId }
                val newIndex = _currentQueue.value.indexOfFirst { it.id == mediaId }

                if (newIndex >= 0) {
                    _currentIndex.value = newIndex
                }

                // Record history for previous track before switching
                recordPlaybackHistory(completed = false)
                currentTrackPlayDurationMs = 0L
                hasRecordedHistoryForCurrentTrack = false

                _playbackState.update {
                    it.copy(
                        currentTrack = track,
                        currentPositionMs = 0L,
                        durationMs = exoPlayer.duration.coerceAtLeast(0L),
                        bufferedPositionMs = 0L
                    )
                }

                // Volume Normalization via LoudnessEnhancer
                if (mediaId != null) {
                    val loudnessDb = trackLoudnessCache[mediaId]
                    audioEffectsManager.applyNormalizationForTrack(loudnessDb)
                }

                // Pre-buffer the next track in queue for gapless playback
                preBufferNextTrack()
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(tag, "ExoPlayer error: ${error.errorCodeName} - ${error.message}", error)
                _playbackState.update { it.copy(isLoading = false, isPlaying = false) }
            }
        })
    }

    /**
     * Starts continuous progress and buffer position monitoring coroutine.
     */
    private fun startProgressTracking() {
        progressTrackingJob?.cancel()
        progressTrackingJob = coroutineScope.launch {
            while (isActive) {
                if (exoPlayer.isPlaying) {
                    val currentPos = exoPlayer.currentPosition.coerceAtLeast(0L)
                    val bufferedPos = exoPlayer.bufferedPosition.coerceAtLeast(0L)
                    val duration = exoPlayer.duration.coerceAtLeast(0L)

                    _playbackState.update {
                        it.copy(
                            currentPositionMs = currentPos,
                            bufferedPositionMs = bufferedPos,
                            durationMs = duration
                        )
                    }

                    currentTrackPlayDurationMs += 500L
                    // If played for more than 30 seconds, record to history
                    if (currentTrackPlayDurationMs >= 30_000L && !hasRecordedHistoryForCurrentTrack) {
                        recordPlaybackHistory(completed = false)
                        hasRecordedHistoryForCurrentTrack = true
                    }
                }
                delay(500L)
            }
        }
    }

    /**
     * Plays a track immediately, optionally replacing or updating the active queue.
     */
    fun playTrack(track: TrackMetadata, queue: List<TrackMetadata> = listOf(track)) {
        coroutineScope.launch {
            _currentQueue.value = queue
            val targetIndex = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
            _currentIndex.value = targetIndex
            _playbackState.update { it.copy(currentTrack = track, isLoading = true) }

            try {
                val mediaItem = resolveMediaItem(track)
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                crossfadeManager.fadeIn(exoPlayer)
                exoPlayer.play()

                // Proactively pre-buffer the upcoming track
                preBufferNextTrack()
            } catch (e: Exception) {
                Log.e(tag, "Failed to play track: ${track.id}", e)
                _playbackState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun play() {
        if (exoPlayer.playbackState == Player.STATE_ENDED) {
            exoPlayer.seekTo(0)
        }
        exoPlayer.play()
    }

    fun pause() {
        exoPlayer.pause()
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs.coerceAtLeast(0L))
        _playbackState.update { it.copy(currentPositionMs = positionMs) }
    }

    fun skipToNext() {
        val queue = _currentQueue.value
        val nextIdx = _currentIndex.value + 1
        if (nextIdx in queue.indices) {
            crossfadeManager.crossfadeToNext(exoPlayer) {
                playTrackAtIndex(nextIdx)
            }
        } else if (_playbackState.value.repeatMode == Player.REPEAT_MODE_ALL && queue.isNotEmpty()) {
            crossfadeManager.crossfadeToNext(exoPlayer) {
                playTrackAtIndex(0)
            }
        }
    }

    fun skipToPrevious() {
        if (exoPlayer.currentPosition > 3000L) {
            // If played more than 3 seconds, restart current track
            seekTo(0L)
            return
        }
        val prevIdx = _currentIndex.value - 1
        if (prevIdx >= 0 && prevIdx < _currentQueue.value.size) {
            crossfadeManager.crossfadeToNext(exoPlayer) {
                playTrackAtIndex(prevIdx)
            }
        } else {
            seekTo(0L)
        }
    }

    private fun playTrackAtIndex(index: Int) {
        val track = _currentQueue.value.getOrNull(index) ?: return
        _currentIndex.value = index
        _playbackState.update { it.copy(currentTrack = track, isLoading = true) }

        coroutineScope.launch {
            try {
                val mediaItem = resolveMediaItem(track)
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                exoPlayer.play()
                preBufferNextTrack()
            } catch (e: Exception) {
                Log.e(tag, "Error loading track at index $index: ${e.message}")
                _playbackState.update { it.copy(isLoading = false) }
            }
        }
    }

    /**
     * Resolves a playable MediaItem. Checks for local downloaded files first;
     * otherwise, extracts stream URL using YoutubeStreamExtractor and caches loudness info.
     */
    suspend fun resolveMediaItem(track: TrackMetadata): MediaItem {
        // 1. Check local download in Room DB
        val localTrack = trackDao.getTrackById(track.id)
        if (localTrack?.isDownloaded == true && !localTrack.localFilePath.isNullOrEmpty()) {
            val file = File(localTrack.localFilePath)
            if (file.exists()) {
                return buildMediaItem(track, Uri.fromFile(file), loudnessDb = 0.0)
            }
        }

        // 2. Extract playable streaming URL via yt-dlp / InnerTube extractor
        val extraction = extractor.extractStream(track.id)
        val bestStream = extraction.selectBestStream(AudioQuality.HIGH)
            ?: throw IllegalStateException("No audio streams available for ${track.id}")

        bestStream.loudnessDb?.let { trackLoudnessCache[track.id] = it }

        // Also ensure track is registered in Room database for foreign-key history integrity
        trackDao.upsertTrack(
            TrackEntity(
                id = track.id,
                title = track.title,
                artist = track.artist,
                artistId = track.artistId,
                album = track.album,
                albumId = track.albumId,
                durationMs = track.durationMs,
                thumbnailUrl = track.thumbnailUrl
            )
        )

        return buildMediaItem(track, Uri.parse(bestStream.url), bestStream.loudnessDb)
    }

    private fun buildMediaItem(track: TrackMetadata, uri: Uri, loudnessDb: Double?): MediaItem {
        val extras = Bundle().apply {
            putDouble("loudnessDb", loudnessDb ?: 0.0)
            putString("artistId", track.artistId)
            putString("albumId", track.albumId)
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .setArtworkUri(if (track.thumbnailUrl.isNotBlank()) Uri.parse(track.thumbnailUrl) else null)
            .setExtras(extras)
            .build()

        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()
    }

    /**
     * Pre-extracts and stages the next track into ExoPlayer's playlist queue for gapless playback.
     */
    private fun preBufferNextTrack() {
        val queue = _currentQueue.value
        val nextIdx = _currentIndex.value + 1
        val nextTrack = if (nextIdx in queue.indices) {
            queue[nextIdx]
        } else if (_playbackState.value.repeatMode == Player.REPEAT_MODE_ALL && queue.isNotEmpty()) {
            queue[0]
        } else {
            null
        } ?: return

        coroutineScope.launch {
            try {
                // Ensure stream URL is pre-extracted and cached in extractor
                extractor.extractStream(nextTrack.id)
            } catch (e: Exception) {
                Log.w(tag, "Pre-buffering next track failed: ${e.message}")
            }
        }
    }

    private fun handleTrackEnded() {
        val queue = _currentQueue.value
        val nextIdx = _currentIndex.value + 1
        when {
            _playbackState.value.repeatMode == Player.REPEAT_MODE_ONE -> {
                seekTo(0L)
                play()
            }
            nextIdx in queue.indices -> {
                playTrackAtIndex(nextIdx)
            }
            _playbackState.value.repeatMode == Player.REPEAT_MODE_ALL && queue.isNotEmpty() -> {
                playTrackAtIndex(0)
            }
        }
    }

    private fun recordPlaybackHistory(completed: Boolean) {
        val currentTrack = _playbackState.value.currentTrack ?: return
        val playDuration = currentTrackPlayDurationMs
        if (playDuration < 5000L && !completed) return

        coroutineScope.launch(Dispatchers.IO) {
            try {
                historyDao.insertHistory(
                    PlaybackHistoryEntity(
                        trackId = currentTrack.id,
                        playedAt = System.currentTimeMillis(),
                        playbackDurationMs = playDuration,
                        completed = completed
                    )
                )
            } catch (e: Exception) {
                Log.e(tag, "Failed to record playback history: ${e.message}")
            }
        }
    }

    // Playback modifiers
    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.8f, 2.0f)
        exoPlayer.setPlaybackSpeed(clamped)
        _playbackState.update { it.copy(playbackSpeed = clamped) }
    }

    fun setRepeatMode(mode: Int) {
        val safeMode = when (mode) {
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
        exoPlayer.repeatMode = safeMode
        _playbackState.update { it.copy(repeatMode = safeMode) }
    }

    fun setShuffleMode(enabled: Boolean) {
        exoPlayer.shuffleModeEnabled = enabled
        _playbackState.update { it.copy(shuffleModeEnabled = enabled) }
    }

    fun setVolumeNormalization(enabled: Boolean) {
        audioEffectsManager.setNormalizationEnabled(enabled)
        _playbackState.update { it.copy(volumeNormalizationEnabled = enabled) }
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        audioEffectsManager.setEqualizerEnabled(enabled)
        _playbackState.update { it.copy(equalizerEnabled = enabled) }
    }

    fun setCrossfadeDuration(durationMs: Long) {
        crossfadeManager.crossfadeDurationMs = durationMs
        _playbackState.update { it.copy(crossfadeDurationMs = durationMs) }
    }

    // Queue Management
    fun addToQueue(track: TrackMetadata) {
        _currentQueue.update { it + track }
    }

    fun playNext(track: TrackMetadata) {
        val currentIdx = _currentIndex.value
        val list = _currentQueue.value.toMutableList()
        if (currentIdx in list.indices) {
            list.add(currentIdx + 1, track)
        } else {
            list.add(track)
        }
        _currentQueue.value = list
    }

    fun removeFromQueue(index: Int) {
        val list = _currentQueue.value.toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            _currentQueue.value = list
            if (index < _currentIndex.value) {
                _currentIndex.update { it - 1 }
            }
        }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val list = _currentQueue.value.toMutableList()
        if (fromIndex in list.indices && toIndex in list.indices) {
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            _currentQueue.value = list
            if (_currentIndex.value == fromIndex) {
                _currentIndex.value = toIndex
            }
        }
    }

    fun clearQueue() {
        _currentQueue.value = emptyList()
        _currentIndex.value = -1
        exoPlayer.clearMediaItems()
        _playbackState.update { it.copy(currentTrack = null, isPlaying = false) }
    }

    fun release() {
        progressTrackingJob?.cancel()
        crossfadeManager.cancel()
        audioEffectsManager.release()
        exoPlayer.release()
    }
}
