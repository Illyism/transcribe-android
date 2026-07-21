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
}
