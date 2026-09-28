package com.ytmusic.core.media.caption

import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

data class CaptionItem(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

@Singleton
class SubtitleManager @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private val cache = mutableMapOf<String, List<CaptionItem>>()

    suspend fun getCaptions(videoId: String): List<CaptionItem> = withContext(Dispatchers.IO) {
        if (videoId.startsWith("local_")) {
            return@withContext emptyList()
        }

        cache[videoId]?.let { return@withContext it }

        // Try Korean first, then English, then auto/default
        val langQueries = listOf("lang=ko", "lang=ko&kind=asr", "lang=en", "lang=en&kind=asr", "")
        for (query in langQueries) {
            val url = if (query.isNotEmpty()) {
                "https://www.youtube.com/api/timedtext?v=$videoId&$query&fmt=json3"
            } else {
                "https://www.youtube.com/api/timedtext?v=$videoId&fmt=json3"
            }

            try {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                    .build()

                val resp = okHttpClient.newCall(req).execute()
                val body = resp.body?.string() ?: ""
                if (body.contains("\"events\"")) {
                    val parsed = parseJson3(body)
                    if (parsed.isNotEmpty()) {
                        cache[videoId] = parsed
                        return@withContext parsed
                    }
                } else if (body.contains("<transcript>") || body.contains("<text")) {
                    val parsedXml = parseXml(body)
                    if (parsedXml.isNotEmpty()) {
                        cache[videoId] = parsedXml
                        return@withContext parsedXml
                    }
                }
            } catch (_: Exception) {}
        }

        emptyList()
    }

    private fun parseJson3(jsonStr: String): List<CaptionItem> {
        val items = mutableListOf<CaptionItem>()
        try {
            val root = JSONObject(jsonStr)
            val events = root.optJSONArray("events") ?: return emptyList()
            for (i in 0 until events.length()) {
                val event = events.optJSONObject(i) ?: continue
                val startMs = event.optLong("tStartMs", 0L)
                val durationMs = event.optLong("dDurationMs", 0L)
                val segs = event.optJSONArray("segs") ?: continue
                val sb = StringBuilder()
                for (j in 0 until segs.length()) {
                    val seg = segs.optJSONObject(j) ?: continue
                    val text = seg.optString("utf8", "")
                    sb.append(text)
                }
                val fullText = sb.toString().trim()
                if (fullText.isNotEmpty() && fullText != "\n") {
                    items.add(
                        CaptionItem(
                            startMs = startMs,
                            endMs = startMs + durationMs.coerceAtLeast(1500L),
                            text = fullText
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        return items
    }

    private fun parseXml(xmlStr: String): List<CaptionItem> {
        val items = mutableListOf<CaptionItem>()
        val pattern = Pattern.compile("<text start=\"([0-9.]+)\" dur=\"([0-9.]+)\"[^>]*>([^<]+)</text>")
        val matcher = pattern.matcher(xmlStr)
        while (matcher.find()) {
            val start = (matcher.group(1)?.toFloatOrNull() ?: 0f) * 1000f
            val dur = (matcher.group(2)?.toFloatOrNull() ?: 2f) * 1000f
            val rawText = matcher.group(3) ?: ""
            val cleanText = Html.fromHtml(rawText, Html.FROM_HTML_MODE_LEGACY).toString().trim()
            if (cleanText.isNotEmpty()) {
                items.add(
                    CaptionItem(
                        startMs = start.toLong(),
                        endMs = (start + dur).toLong(),
                        text = cleanText
                    )
                )
            }
        }
        return items
    }
}
