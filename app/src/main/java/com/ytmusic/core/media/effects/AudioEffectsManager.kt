package com.ytmusic.core.media.effects

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

data class EqualizerBandInfo(
    val bandIndex: Short,
    val centerFreqHz: Int,
    val minLevelMb: Short,
    val maxLevelMb: Short,
    val currentLevelMb: Short
)

@Singleton
class AudioEffectsManager @Inject constructor() {

    private val tag = "AudioEffectsManager"
    private var currentAudioSessionId: Int = 0

    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null

    private var isNormalizationEnabled: Boolean = true
    private var isEqualizerEnabled: Boolean = false
    private var isBassBoostEnabled: Boolean = false
    private var currentBassBoostStrength: Short = 0

    /**
     * Attaches audio effects to the active ExoPlayer audioSessionId.
     */
    fun attachToAudioSession(audioSessionId: Int) {
        if (audioSessionId <= 0 || audioSessionId == currentAudioSessionId) return
        release()
        currentAudioSessionId = audioSessionId

        try {
            loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                enabled = isNormalizationEnabled
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize LoudnessEnhancer: ${e.message}")
        }

        try {
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = isEqualizerEnabled
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize Equalizer: ${e.message}")
        }

        try {
            bassBoost = BassBoost(0, audioSessionId).apply {
                enabled = isBassBoostEnabled
                if (strengthSupported) {
                    setStrength(currentBassBoostStrength)
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize BassBoost: ${e.message}")
        }
    }

    /**
     * Applies volume normalization (target -14 LUFS standard) using the track's extracted loudnessDb.
     */
    fun applyNormalizationForTrack(loudnessDb: Double?) {
        val enhancer = loudnessEnhancer ?: return
        if (!isNormalizationEnabled) {
            enhancer.enabled = false
            return
        }

        enhancer.enabled = true
        if (loudnessDb == null) {
            enhancer.setTargetGain(0)
            return
        }

        // Standard target: -14 LUFS.
        // If track loudness is -18 dB, difference is -4 dB (needs +4 dB = +400 mB boost)
        // If track loudness is -10 dB, it is already loud, no boost needed (0 mB)
        val targetLufs = -14.0
        val diffDb = targetLufs - loudnessDb
        val gainMb = if (diffDb > 0.0) {
            (diffDb * 100.0).toInt().coerceIn(0, 800) // Max +8 dB boost to prevent clipping
        } else {
            0
        }

        try {
            enhancer.setTargetGain(gainMb)
        } catch (e: Exception) {
            Log.e(tag, "Failed to set target gain: ${e.message}")
        }
    }

    fun setNormalizationEnabled(enabled: Boolean) {
        isNormalizationEnabled = enabled
        loudnessEnhancer?.enabled = enabled
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        isEqualizerEnabled = enabled
        equalizer?.enabled = enabled
    }

    fun setBassBoostStrength(strength: Short) {
        currentBassBoostStrength = strength.coerceIn(0, 1000)
        isBassBoostEnabled = currentBassBoostStrength > 0
        bassBoost?.let {
            it.enabled = isBassBoostEnabled
            if (it.strengthSupported) {
                it.setStrength(currentBassBoostStrength)
            }
        }
    }

    fun getEqualizerBands(): List<EqualizerBandInfo> {
        val eq = equalizer ?: return emptyList()
        val numBands = eq.numberOfBands
        val minLevel = eq.bandLevelRange[0]
        val maxLevel = eq.bandLevelRange[1]

        val list = mutableListOf<EqualizerBandInfo>()
        for (i in 0 until numBands) {
            val bandIndex = i.toShort()
            val centerFreq = eq.getCenterFreq(bandIndex) / 1000 // Convert mHz to Hz
            val currentLevel = eq.getBandLevel(bandIndex)
            list.add(
                EqualizerBandInfo(
                    bandIndex = bandIndex,
                    centerFreqHz = centerFreq,
                    minLevelMb = minLevel,
                    maxLevelMb = maxLevel,
                    currentLevelMb = currentLevel
                )
            )
        }
        return list
    }

    fun setEqualizerBandLevel(bandIndex: Short, levelMb: Short) {
        val eq = equalizer ?: return
        val minLevel = eq.bandLevelRange[0]
        val maxLevel = eq.bandLevelRange[1]
        val safeLevel = levelMb.coerceIn(minLevel, maxLevel)
        try {
            eq.setBandLevel(bandIndex, safeLevel)
        } catch (e: Exception) {
            Log.e(tag, "Failed to set band level: ${e.message}")
        }
    }

    fun release() {
        loudnessEnhancer?.release()
        loudnessEnhancer = null

        equalizer?.release()
        equalizer = null

        bassBoost?.release()
        bassBoost = null

        currentAudioSessionId = 0
    }
}
