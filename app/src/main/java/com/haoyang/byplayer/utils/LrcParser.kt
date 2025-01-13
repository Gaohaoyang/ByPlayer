package com.haoyang.byplayer.utils

import java.io.File
import java.io.IOException

data class LrcLine(
    val timeMs: Long,
    val text: String
)

class LrcParser {
    fun parseLrcFile(lrcPath: String): List<LrcLine> {
        try {
            val file = File(lrcPath)
            if (!file.exists()) return emptyList()

            return file.readLines()
                .mapNotNull { line -> parseLrcLine(line) }
                .sortedBy { it.timeMs }
        } catch (e: IOException) {
            e.printStackTrace()
            return emptyList()
        }
    }

    private fun parseLrcLine(line: String): LrcLine? {
        val timeRegex = "\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})](.*)".toRegex()
        val matchResult = timeRegex.find(line) ?: return null

        val (minutes, seconds, milliseconds, text) = matchResult.destructured
        val timeMs = minutes.toLong() * 60 * 1000 +
                    seconds.toLong() * 1000 +
                    milliseconds.padEnd(3, '0').toLong()

        return LrcLine(timeMs, text.trim())
    }

    fun findCurrentLyric(lyrics: List<LrcLine>, currentTimeMs: Long): String {
        if (lyrics.isEmpty()) return ""

        val index = lyrics.binarySearch { it.timeMs.compareTo(currentTimeMs) }
        val currentIndex = if (index < 0) (-index - 2) else index

        return if (currentIndex >= 0 && currentIndex < lyrics.size) {
            lyrics[currentIndex].text
        } else ""
    }
}
