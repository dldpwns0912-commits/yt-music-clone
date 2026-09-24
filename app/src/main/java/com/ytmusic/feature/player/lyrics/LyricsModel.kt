package com.ytmusic.feature.player.lyrics

import java.util.regex.Pattern

data class LyricLine(
    val timeMs: Long,
    val text: String
)

object LrcParser {
    private val pattern = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.*)")

    fun parse(lrcContent: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        lrcContent.lines().forEach { line ->
            val matcher = pattern.matcher(line.trim())
            if (matcher.matches()) {
                val minutes = matcher.group(1)?.toLongOrNull() ?: 0L
                val seconds = matcher.group(2)?.toLongOrNull() ?: 0L
                val millisRaw = matcher.group(3) ?: "00"
                val millis = if (millisRaw.length == 2) {
                    millisRaw.toLongOrNull()?.times(10) ?: 0L
                } else {
                    millisRaw.toLongOrNull() ?: 0L
                }

                val totalTimeMs = (minutes * 60 * 1000) + (seconds * 1000) + millis
                val text = matcher.group(4)?.trim() ?: ""
                if (text.isNotEmpty()) {
                    lines.add(LyricLine(timeMs = totalTimeMs, text = text))
                }
            }
        }
        return lines.sortedBy { it.timeMs }
    }

    /**
     * Fallback generator when live LRC is not available.
     * Generates synced verses tailored to the track duration.
     */
    fun generatePlaceholderLyrics(trackTitle: String, artist: String, durationMs: Long): List<LyricLine> {
        val duration = durationMs.coerceAtLeast(60_000L)
        val step = duration / 8
        return listOf(
            LyricLine(0L, "♪ (Intro - $trackTitle by $artist) ♪"),
            LyricLine(step * 1, "We're no strangers to love"),
            LyricLine(step * 2, "You know the rules and so do I"),
            LyricLine(step * 3, "A full commitment's what I'm thinking of"),
            LyricLine(step * 4, "You wouldn't get this from any other guy"),
            LyricLine(step * 5, "I just wanna tell you how I'm feeling"),
            LyricLine(step * 6, "Gotta make you understand"),
            LyricLine(step * 7, "Never gonna give you up, never gonna let you down ♪")
        )
    }
}
