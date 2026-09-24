package com.ytmusic.core.media.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.ytmusic.MainActivity
import com.ytmusic.core.media.player.MusicPlayerManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MusicPlaybackService : MediaLibraryService() {

    private val tag = "MusicPlaybackService"

    @Inject
    lateinit var playerManager: MusicPlayerManager

    @Inject
    lateinit var callback: MusicLibrarySessionCallback

    @Inject
    lateinit var notificationManager: MediaNotificationManager

    private var mediaLibrarySession: MediaLibrarySession? = null

    // Handles unplugging headphones or disconnecting bluetooth audio
    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                Log.d(tag, "Audio becoming noisy -> pausing playback")
                playerManager.pause()
            }
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        // Set custom notification provider for persistent lock screen & system status controls
        setMediaNotificationProvider(notificationManager.createNotificationProvider())

        val sessionActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val sessionActivityPendingIntent = PendingIntent.getActivity(this, 0, sessionActivityIntent, flags)

        mediaLibrarySession = MediaLibrarySession.Builder(this, playerManager.exoPlayer, callback)
            .setSessionActivity(sessionActivityPendingIntent)
            .build()

        // Register noisy broadcast receiver
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        registerReceiver(becomingNoisyReceiver, filter)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaLibrarySession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(becomingNoisyReceiver)
        } catch (_: Exception) {}

        mediaLibrarySession?.run {
            player.release()
            release()
            mediaLibrarySession = null
        }
        playerManager.release()
        super.onDestroy()
    }
}
