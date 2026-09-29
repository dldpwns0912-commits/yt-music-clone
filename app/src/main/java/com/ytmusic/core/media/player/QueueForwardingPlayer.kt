package com.ytmusic.core.media.player

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/**
 * Custom ForwardingPlayer that integrates ExoPlayer with MusicPlayerManager's queue management.
 * Exposes SEEK_TO_NEXT, SEEK_TO_PREVIOUS, and PLAY_PAUSE commands to Android MediaSession,
 * ensuring Samsung One UI 'Media Output' (미디어 출력), Bluetooth earphones (Galaxy Buds),
 * and system lock screen controls work seamlessly with native Android behavior.
 */
@OptIn(UnstableApi::class)
class QueueForwardingPlayer(
    player: Player,
    private val playerManager: MusicPlayerManager
) : ForwardingPlayer(player) {

    override fun getAvailableCommands(): Player.Commands {
        val base = super.getAvailableCommands()
        return base.buildUpon()
            .add(Player.COMMAND_PLAY_PAUSE)
            .add(Player.COMMAND_PREPARE)
            .add(Player.COMMAND_STOP)
            .add(Player.COMMAND_SEEK_TO_DEFAULT_POSITION)
            .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .add(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
            .add(Player.COMMAND_GET_TIMELINE)
            .add(Player.COMMAND_GET_METADATA)
            .build()
    }

    override fun isCommandAvailable(@Player.Command command: Int): Boolean {
        return when (command) {
            Player.COMMAND_PLAY_PAUSE,
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            Player.COMMAND_STOP -> true
            else -> super.isCommandAvailable(command)
        }
    }

    override fun hasNextMediaItem(): Boolean = true

    override fun hasPreviousMediaItem(): Boolean = true

    override fun seekToNext() {
        playerManager.skipToNext()
    }

    override fun seekToNextMediaItem() {
        playerManager.skipToNext()
    }

    override fun seekToPrevious() {
        playerManager.skipToPrevious()
    }

    override fun seekToPreviousMediaItem() {
        playerManager.skipToPrevious()
    }

    override fun play() {
        playerManager.play()
    }

    override fun pause() {
        playerManager.pause()
    }

    override fun stop() {
        playerManager.pause()
    }
}
