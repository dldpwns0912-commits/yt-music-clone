package com.ytmusic.core.system

import android.content.Context
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log

class OfflineModeTileService : TileService() {

    private val tag = "OfflineModeTile"

    companion object {
        const val PREFS_NAME = "yt_system_prefs"
        const val KEY_OFFLINE_MODE = "pref_offline_only_mode"

        fun isOfflineModeEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_OFFLINE_MODE, false)
        }

        fun setOfflineModeEnabled(context: Context, enabled: Boolean) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_OFFLINE_MODE, enabled).apply()
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val currentState = isOfflineModeEnabled(this)
        val newState = !currentState
        setOfflineModeEnabled(this, newState)
        Log.d(tag, "Offline Mode toggled to: $newState")
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val isOffline = isOfflineModeEnabled(this)

        tile.state = if (isOffline) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "YT Offline"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (isOffline) "Offline Only" else "Streaming Enabled"
        }
        tile.updateTile()
    }
}
