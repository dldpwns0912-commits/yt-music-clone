package com.ytmusic.feature.equalizer

import androidx.lifecycle.ViewModel
import com.ytmusic.core.media.effects.AudioEffectsManager
import com.ytmusic.core.media.effects.EqualizerBandInfo
import com.ytmusic.core.media.player.MusicPlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

enum class EqPreset(val label: String, val bandGainsMb: List<Short>) {
    FLAT("Flat", listOf(0, 0, 0, 0, 0)),
    BASS_BOOST("Bass Boost", listOf(600, 400, 100, 0, -100)),
    VOCAL("Vocal", listOf(-200, 100, 500, 300, 100)),
    ELECTRONIC("Electronic", listOf(400, 200, 0, 200, 400)),
    ROCK("Rock", listOf(500, 300, -100, 200, 500))
}

@HiltViewModel
class EqualizerViewModel @Inject constructor(
    private val audioEffectsManager: AudioEffectsManager,
    private val playerManager: MusicPlayerManager
) : ViewModel() {

    private val _isEqualizerEnabled = MutableStateFlow(true)
    val isEqualizerEnabled: StateFlow<Boolean> = _isEqualizerEnabled.asStateFlow()

    private val _bassBoostStrength = MutableStateFlow(0)
    val bassBoostStrength: StateFlow<Int> = _bassBoostStrength.asStateFlow()

    private val _isNormalizationEnabled = MutableStateFlow(true)
    val isNormalizationEnabled: StateFlow<Boolean> = _isNormalizationEnabled.asStateFlow()

    private val _bands = MutableStateFlow<List<EqualizerBandInfo>>(emptyList())
    val bands: StateFlow<List<EqualizerBandInfo>> = _bands.asStateFlow()

    private val _selectedPreset = MutableStateFlow(EqPreset.FLAT)
    val selectedPreset: StateFlow<EqPreset> = _selectedPreset.asStateFlow()

    init {
        loadBands()
    }

    fun loadBands() {
        val currentBands = audioEffectsManager.getEqualizerBands()
        _bands.value = if (currentBands.isNotEmpty()) {
            currentBands
        } else {
            // Default 5-band fallback representation (-15 dB to +15 dB, in millibels)
            listOf(
                EqualizerBandInfo(0, 60, -1500, 1500, 0),
                EqualizerBandInfo(1, 230, -1500, 1500, 0),
                EqualizerBandInfo(2, 910, -1500, 1500, 0),
                EqualizerBandInfo(3, 3600, -1500, 1500, 0),
                EqualizerBandInfo(4, 14000, -1500, 1500, 0)
            )
        }
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        _isEqualizerEnabled.value = enabled
        audioEffectsManager.setEqualizerEnabled(enabled)
        playerManager.setEqualizerEnabled(enabled)
    }

    fun setBandLevel(bandIndex: Short, levelMb: Short) {
        audioEffectsManager.setEqualizerBandLevel(bandIndex, levelMb)
        _bands.value = _bands.value.map {
            if (it.bandIndex == bandIndex) it.copy(currentLevelMb = levelMb) else it
        }
    }

    fun setBassBoost(strength: Int) {
        _bassBoostStrength.value = strength
        audioEffectsManager.setBassBoostStrength(strength.toShort())
    }

    fun setNormalizationEnabled(enabled: Boolean) {
        _isNormalizationEnabled.value = enabled
        audioEffectsManager.setNormalizationEnabled(enabled)
        playerManager.setVolumeNormalization(enabled)
    }

    fun applyPreset(preset: EqPreset) {
        _selectedPreset.value = preset
        val gains = preset.bandGainsMb
        val updated = _bands.value.mapIndexed { index, band ->
            val gain = gains.getOrNull(index) ?: 0
            audioEffectsManager.setEqualizerBandLevel(band.bandIndex, gain)
            band.copy(currentLevelMb = gain)
        }
        _bands.value = updated
    }
}
