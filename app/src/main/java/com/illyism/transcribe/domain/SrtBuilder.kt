package com.illyism.transcribe.domain

object SrtBuilder {
    fun formatTime(seconds: Double): String {
        val totalMillis = (seconds * 1000).toLong().coerceAtLeast(0)
        val hours = totalMillis / 3_600_000
        val minutes = (totalMillis % 3_600_000) / 60_000
        val secs = (totalMillis % 60_000) / 1000
        val millis = totalMillis % 1000
        return "%02d:%02d:%02d,%03d".format(hours, minutes, secs, millis)
    }

    fun fromSegments(segments: List<WhisperSegment>): String {
        val sb = StringBuilder()
        segments.forEachIndexed { index, segment ->
            sb.append(index + 1).append('\n')
            sb.append(formatTime(segment.start))
                .append(" --> ")
                .append(formatTime(segment.end))
                .append('\n')
            sb.append(segment.text.trim()).append("\n\n")
        }
        return sb.toString()
    }

    fun preview(srt: String, maxChars: Int = 500): String {
        return if (srt.length <= maxChars) srt else srt.take(maxChars).trimEnd() + "…"
    }

    /** Plain transcript without sequence numbers or timestamps. */
    fun plainText(srt: String): String {
        if (srt.isBlank()) return ""
        val blocks = srt.trim().split(Regex("\\n\\s*\\n"))
        return blocks.mapNotNull { block ->
            val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            when {
                lines.isEmpty() -> null
                lines.size == 1 -> lines[0].takeUnless { it.toIntOrNull() != null || it.contains("-->") }
                else -> {
                    val textLines = lines.dropWhile { it.toIntOrNull() != null || it.contains("-->") }
                    textLines.joinToString(" ").ifBlank { null }
                }
            }
        }.joinToString(" ").replace(Regex("\\s+"), " ").trim()
    }

    fun segmentCount(srt: String): Int {
        if (srt.isBlank()) return 0
        return Regex("^\\d+\\s*$", RegexOption.MULTILINE).findAll(srt).count().coerceAtLeast(
            srt.trim().split(Regex("\\n\\s*\\n")).count { it.isNotBlank() }
        )
    }

    /** Duration in seconds from the last cue end time, if parseable. */
    fun durationSeconds(srt: String): Double {
        val match = Regex("""(\d{2}):(\d{2}):(\d{2}),(\d{3})\s*-->\s*(\d{2}):(\d{2}):(\d{2}),(\d{3})""")
            .findAll(srt)
            .lastOrNull()
            ?: return 0.0
        val endH = match.groupValues[5].toDouble()
        val endM = match.groupValues[6].toDouble()
        val endS = match.groupValues[7].toDouble()
        val endMs = match.groupValues[8].toDouble()
        return endH * 3600 + endM * 60 + endS + endMs / 1000.0
    }

    fun formatClock(seconds: Double): String {
        val total = seconds.toInt().coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }
}
