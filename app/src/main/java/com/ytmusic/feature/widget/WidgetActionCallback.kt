package com.ytmusic.feature.widget

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.ytmusic.core.media.service.MusicPlaybackService

class WidgetActionCallback : ActionCallback {

    companion object {
        val ACTION_KEY = ActionParameters.Key<String>("widget_action")
        const val ACTION_PLAY_PAUSE = "action_play_pause"
        const val ACTION_NEXT = "action_next"
        const val ACTION_PREV = "action_prev"
    }

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val action = parameters[ACTION_KEY] ?: return
        Log.d("WidgetActionCallback", "Widget action triggered: $action")

        val intent = Intent(context, MusicPlaybackService::class.java).apply {
            this.action = action
        }
        try {
            context.startService(intent)
        } catch (e: Exception) {
            Log.e("WidgetActionCallback", "Failed to send widget intent: ${e.message}")
        }
    }
}
