package com.ytmusic.feature.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.ButtonDefaults
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionParametersOf
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.ytmusic.MainActivity

class MusicGlanceWidget : GlanceAppWidget() {

    companion object {
        const val PREFS_NAME = "yt_widget_prefs"
        const val KEY_TITLE = "widget_title"
        const val KEY_ARTIST = "widget_artist"
        const val KEY_IS_PLAYING = "widget_is_playing"

        fun updateWidgetState(context: Context, title: String, artist: String, isPlaying: Boolean) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_TITLE, title)
                .putString(KEY_ARTIST, artist)
                .putBoolean(KEY_IS_PLAYING, isPlaying)
                .apply()
        }
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val title = prefs.getString(KEY_TITLE, "YouTube Music") ?: "YouTube Music"
        val artist = prefs.getString(KEY_ARTIST, "Tap to open player") ?: "Tap to open player"
        val isPlaying = prefs.getBoolean(KEY_IS_PLAYING, false)

        provideContent {
            WidgetContent(context, title, artist, isPlaying)
        }
    }

    @Composable
    private fun WidgetContent(
        context: Context,
        title: String,
        artist: String,
        isPlaying: Boolean
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(16.dp)
                .background(Color(0xFF212121))
                .padding(12.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album art placeholder / icon
            Box(
                modifier = GlanceModifier
                    .size(48.dp)
                    .cornerRadius(8.dp)
                    .background(Color(0xFF333333)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(android.R.drawable.ic_media_play),
                    contentDescription = if (isPlaying) "Playing" else "Paused",
                    modifier = GlanceModifier.size(24.dp)
                )
            }

            Spacer(modifier = GlanceModifier.width(12.dp))

            // Track metadata
            Column(
                modifier = GlanceModifier.defaultWeight()
            ) {
                Text(
                    text = title,
                    style = TextStyle(
                        color = androidx.glance.unit.ColorProvider(Color.White),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    text = artist,
                    style = TextStyle(
                        color = androidx.glance.unit.ColorProvider(Color(0xFFAAAAAA)),
                        fontSize = 12.sp
                    ),
                    maxLines = 1
                )
            }

            Spacer(modifier = GlanceModifier.width(8.dp))

            // Playback controls
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    text = "⏮",
                    onClick = actionRunCallback<WidgetActionCallback>(
                        actionParametersOf(WidgetActionCallback.ACTION_KEY to WidgetActionCallback.ACTION_PREV)
                    ),
                    modifier = GlanceModifier.size(36.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = androidx.glance.unit.ColorProvider(Color.Transparent),
                        contentColor = androidx.glance.unit.ColorProvider(Color.White)
                    )
                )

                Spacer(modifier = GlanceModifier.width(4.dp))

                Button(
                    text = if (isPlaying) "⏸" else "▶",
                    onClick = actionRunCallback<WidgetActionCallback>(
                        actionParametersOf(WidgetActionCallback.ACTION_KEY to WidgetActionCallback.ACTION_PLAY_PAUSE)
                    ),
                    modifier = GlanceModifier.size(36.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = androidx.glance.unit.ColorProvider(Color(0xFFFF0000)),
                        contentColor = androidx.glance.unit.ColorProvider(Color.White)
                    )
                )

                Spacer(modifier = GlanceModifier.width(4.dp))

                Button(
                    text = "⏭",
                    onClick = actionRunCallback<WidgetActionCallback>(
                        actionParametersOf(WidgetActionCallback.ACTION_KEY to WidgetActionCallback.ACTION_NEXT)
                    ),
                    modifier = GlanceModifier.size(36.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = androidx.glance.unit.ColorProvider(Color.Transparent),
                        contentColor = androidx.glance.unit.ColorProvider(Color.White)
                    )
                )
            }
        }
    }
}
