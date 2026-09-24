package com.ytmusic.core.media.player

import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CrossfadeManager @Inject constructor() {

    private val scope = CoroutineScope(Dispatchers.Main)
    private var fadeJob: Job? = null

    var crossfadeDurationMs: Long = 2000L

    fun fadeIn(player: Player, durationMs: Long = crossfadeDurationMs) {
        if (durationMs <= 0L) {
            player.volume = 1.0f
            return
        }

        fadeJob?.cancel()
        fadeJob = scope.launch {
            player.volume = 0.0f
            val steps = 20
            val interval = durationMs / steps
            for (i in 1..steps) {
                delay(interval)
                val volume = (i.toFloat() / steps.toFloat()).coerceIn(0.0f, 1.0f)
                player.volume = volume
            }
            player.volume = 1.0f
        }
    }

    fun fadeOut(
        player: Player,
        durationMs: Long = crossfadeDurationMs,
        onComplete: (() -> Unit)? = null
    ) {
        if (durationMs <= 0L) {
            player.volume = 0.0f
            onComplete?.invoke()
            player.volume = 1.0f
            return
        }

        fadeJob?.cancel()
        fadeJob = scope.launch {
            val startVolume = player.volume
            val steps = 20
            val interval = durationMs / steps
            for (i in 1..steps) {
                delay(interval)
                val factor = (1.0f - (i.toFloat() / steps.toFloat())).coerceIn(0.0f, 1.0f)
                player.volume = startVolume * factor
            }
            player.volume = 0.0f
            onComplete?.invoke()
            player.volume = 1.0f
        }
    }

    fun crossfadeToNext(
        player: Player,
        durationMs: Long = crossfadeDurationMs,
        switchAction: () -> Unit
    ) {
        fadeOut(player, durationMs / 2) {
            switchAction()
            fadeIn(player, durationMs / 2)
        }
    }

    fun cancel() {
        fadeJob?.cancel()
        fadeJob = null
    }
}
