package com.ytmusic.core.extractor.ytdlp

import com.ytmusic.core.extractor.model.AudioCodec
import com.ytmusic.core.extractor.model.AudioStream
import com.ytmusic.core.extractor.model.ExtractionResult
import com.ytmusic.core.extractor.model.ExtractorException
import com.ytmusic.core.extractor.model.ExtractorSource
import com.ytmusic.core.extractor.model.Thumbnail
import com.ytmusic.core.extractor.model.TrackMetadata
import com.ytmusic.core.extractor.updater.ExtractorUpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YtDlpExtractor @Inject constructor(
    private val updateManager: ExtractorUpdateManager
) {

    fun isSupported(): Boolean = updateManager.isBinaryAvailable()

    suspend fun extract(videoId: String): ExtractionResult = withContext(Dispatchers.IO) {
        if (!isSupported()) {
            throw ExtractorException.ProcessExecutionException(-1, "yt-dlp binary is not installed or executable")
        }

        val binary = updateManager.getBinaryFile()
        val videoUrl = "https://www.youtube.com/watch?v=$videoId"

        val command = listOf(
            binary.absolutePath,
            "--dump-single-json",
            "--no-warnings",
            "--no-playlist",
            "--skip-download",
            "-f", "bestaudio/best",
            videoUrl
        )

        withTimeout(20_000L) {
            val process = ProcessBuilder(command)
                .redirectErrorStream(false)
                .start()

            val stdoutReader = BufferedReader(InputStreamReader(process.inputStream))
            val stderrReader = BufferedReader(InputStreamReader(process.errorStream))

            val stdoutBuilder = StringBuilder()
            val stderrBuilder = StringBuilder()

            val stdoutJob = Thread {
                var line: String?
                while (stdoutReader.readLine().also { line = it } != null) {
                    stdoutBuilder.appendLine(line)
                }
            }.apply { start() }

            val stderrJob = Thread {
                var line: String?
                while (stderrReader.readLine().also { line = it } != null) {
                    stderrBuilder.appendLine(line)
                }
            }.apply { start() }

            val finished = process.waitFor(18, TimeUnit.SECONDS)
            stdoutJob.join(1000)
            stderrJob.join(1000)

            if (!finished) {
                process.destroyForcibly()
                throw ExtractorException.ProcessExecutionException(-1, "yt-dlp execution timed out after 18 seconds")
            }

            val exitCode = process.exitValue()
            if (exitCode != 0) {
                throw ExtractorException.ProcessExecutionException(exitCode, stderrBuilder.toString().trim())
            }

            parseJsonOutput(videoId, stdoutBuilder.toString())
        }
    }

    private fun parseJsonOutput(videoId: String, jsonString: String): ExtractionResult {
        val root = try {
            JSONObject(jsonString)
        } catch (e: Exception) {
            throw ExtractorException.ParsingException("Failed to parse yt-dlp JSON output: ${e.message}", e)
        }

        val title = root.optString("title", "Unknown Title")
        val artist = root.optString("uploader", root.optString("channel", "Unknown Artist"))
        val duration = root.optDouble("duration", 0.0).toLong() * 1000L
        val viewCount = root.optLong("view_count", 0L)
        val defaultThumbnail = root.optString("thumbnail", "")

        val thumbnails = mutableListOf<Thumbnail>()
        val thumbsArray = root.optJSONArray("thumbnails") ?: JSONArray()
        for (i in 0 until thumbsArray.length()) {
            val thumb = thumbsArray.optJSONObject(i) ?: continue
            val url = thumb.optString("url", "")
            val w = thumb.optInt("width", 0)
            val h = thumb.optInt("height", 0)
            if (url.isNotEmpty()) {
                thumbnails.add(Thumbnail(url = url, width = w, height = h))
            }
        }
        val bestThumbnail = thumbnails.maxByOrNull { it.width * it.height }?.url
            ?: defaultThumbnail

        val metadata = TrackMetadata(
            id = videoId,
            title = title,
            artist = artist,
            durationMs = duration,
            thumbnailUrl = bestThumbnail,
            thumbnails = thumbnails,
            viewCount = viewCount
        )

        val audioStreams = mutableListOf<AudioStream>()
        val formatsArray = root.optJSONArray("formats") ?: JSONArray()

        for (i in 0 until formatsArray.length()) {
            val format = formatsArray.optJSONObject(i) ?: continue
            val vcodec = format.optString("vcodec", "none")
            val acodec = format.optString("acodec", "none")
            val url = format.optString("url", "")

            // Only pick formats with audio
            if (url.isEmpty() || acodec == "none") continue

            val itag = format.optInt("format_id", "0").toIntOrNull() ?: format.optInt("itag", 0)
            val ext = format.optString("ext", "m4a")
            val mimeType = "audio/$ext; codecs=\"$acodec\""
            val abr = format.optDouble("abr", 0.0).toInt() * 1000
            val tbr = format.optDouble("tbr", 0.0).toInt() * 1000
            val bitrate = if (abr > 0) abr else if (tbr > 0) tbr else 128_000
            val sampleRate = format.optInt("asr", 44100)
            val filesize = format.optLong("filesize", 0L)

            audioStreams.add(
                AudioStream(
                    url = url,
                    itag = itag,
                    mimeType = mimeType,
                    codec = AudioCodec.fromMimeType(mimeType),
                    bitrate = bitrate,
                    sampleRate = sampleRate,
                    contentLength = filesize,
                    approxDurationMs = duration
                )
            )
        }

        // Direct url if formats wasn't array or single stream was returned
        if (audioStreams.isEmpty()) {
            val directUrl = root.optString("url", "")
            if (directUrl.isNotEmpty()) {
                audioStreams.add(
                    AudioStream(
                        url = directUrl,
                        itag = 0,
                        mimeType = "audio/webm",
                        codec = AudioCodec.OPUS,
                        bitrate = 160_000,
                        approxDurationMs = duration
                    )
                )
            }
        }

        if (audioStreams.isEmpty()) {
            throw ExtractorException.ParsingException("No audio streams found in yt-dlp output for video: $videoId")
        }

        return ExtractionResult(
            videoId = videoId,
            metadata = metadata,
            audioStreams = audioStreams,
            source = ExtractorSource.YT_DLP
        )
    }
}
