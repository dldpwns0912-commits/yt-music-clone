package com.ytmusic.core.backup

import com.ytmusic.core.database.dao.PlaylistDao
import com.ytmusic.core.database.dao.TrackDao
import com.ytmusic.core.database.entity.PlaylistEntity
import com.ytmusic.core.database.entity.PlaylistTrackCrossRef
import com.ytmusic.core.database.entity.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class BackupRestoreResult(
    val playlistsRestored: Int,
    val tracksRestored: Int,
    val success: Boolean,
    val errorMessage: String? = null
)

@Singleton
class PlaylistBackupManager @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val trackDao: TrackDao
) {
    suspend fun exportBackupJson(): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())

        val playlistsArray = JSONArray()
        val allPlaylists = playlistDao.observeAllPlaylists().first()

        for (playlist in allPlaylists) {
            val playlistWithTracks = playlistDao.observePlaylistWithTracks(playlist.id).first()
                ?: continue

            val playlistObj = JSONObject()
            playlistObj.put("id", playlist.id)
            playlistObj.put("name", playlist.name)
            playlistObj.put("description", playlist.description ?: "")
            playlistObj.put("isSystemGenerated", playlist.isSystemGenerated)
            playlistObj.put("createdAt", playlist.createdAt)
            playlistObj.put("updatedAt", playlist.updatedAt)

            val tracksArray = JSONArray()
            for (track in playlistWithTracks.tracks) {
                val trackObj = JSONObject()
                trackObj.put("id", track.id)
                trackObj.put("title", track.title)
                trackObj.put("artist", track.artist)
                trackObj.put("artistId", track.artistId ?: "")
                trackObj.put("album", track.album ?: "")
                trackObj.put("albumId", track.albumId ?: "")
                trackObj.put("durationMs", track.durationMs)
                trackObj.put("thumbnailUrl", track.thumbnailUrl)
                trackObj.put("mimeType", track.mimeType)
                tracksArray.put(trackObj)
            }
            playlistObj.put("tracks", tracksArray)
            playlistsArray.put(playlistObj)
        }

        root.put("playlists", playlistsArray)
        root.toString(2)
    }

    suspend fun restoreBackupJson(jsonString: String): BackupRestoreResult = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonString)
            val playlistsArray = root.optJSONArray("playlists")
                ?: return@withContext BackupRestoreResult(0, 0, false, "Invalid JSON: missing playlists array")

            var playlistCount = 0
            var trackCount = 0

            for (i in 0 until playlistsArray.length()) {
                val playlistObj = playlistsArray.optJSONObject(i) ?: continue
                val rawId = playlistObj.optString("id", "")
                val name = playlistObj.optString("name", "Restored Playlist")
                val description = playlistObj.optString("description", "")
                val isSystem = playlistObj.optBoolean("isSystemGenerated", false)
                val createdAt = playlistObj.optLong("createdAt", System.currentTimeMillis())

                // Avoid collision with system playlist IDs if custom
                val playlistId = if (isSystem) rawId else if (rawId.isNotEmpty()) rawId else UUID.randomUUID().toString()

                val playlistEntity = PlaylistEntity(
                    id = playlistId,
                    name = name,
                    description = description.ifEmpty { null },
                    isSystemGenerated = isSystem,
                    createdAt = createdAt,
                    updatedAt = System.currentTimeMillis()
                )
                playlistDao.upsertPlaylist(playlistEntity)
                playlistCount++

                val tracksArray = playlistObj.optJSONArray("tracks") ?: JSONArray()
                val trackIdsInOrder = mutableListOf<String>()

                for (j in 0 until tracksArray.length()) {
                    val trackObj = tracksArray.optJSONObject(j) ?: continue
                    val trackId = trackObj.optString("id", "")
                    if (trackId.isEmpty()) continue

                    val trackEntity = TrackEntity(
                        id = trackId,
                        title = trackObj.optString("title", "Unknown"),
                        artist = trackObj.optString("artist", "Unknown"),
                        artistId = trackObj.optString("artistId").ifEmpty { null },
                        album = trackObj.optString("album").ifEmpty { null },
                        albumId = trackObj.optString("albumId").ifEmpty { null },
                        durationMs = trackObj.optLong("durationMs", 0L),
                        thumbnailUrl = trackObj.optString("thumbnailUrl", ""),
                        mimeType = trackObj.optString("mimeType", "audio/mp4")
                    )
                    trackDao.upsertTrack(trackEntity)
                    trackIdsInOrder.add(trackId)
                    trackCount++
                }

                if (trackIdsInOrder.isNotEmpty()) {
                    val crossRefs = trackIdsInOrder.mapIndexed { index, tId ->
                        PlaylistTrackCrossRef(
                            playlistId = playlistId,
                            trackId = tId,
                            position = index
                        )
                    }
                    playlistDao.insertCrossRefs(crossRefs)
                }
            }

            BackupRestoreResult(playlistCount, trackCount, true)
        } catch (e: Exception) {
            BackupRestoreResult(0, 0, false, "Failed to restore backup: ${e.message}")
        }
    }
}
