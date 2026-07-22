package com.illyism.transcribe.domain

object SrtBuilder {
    data class Cue(
        val index: Int,
        val startSeconds: Double,
        val endSeconds: Double,
        val text: String
    )

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

    fun parse(srt: String): List<Cue> {
        if (srt.isBlank()) return emptyList()
        return srt.trim().split(Regex("\\n\\s*\\n")).mapNotNull { block ->
            val lines = block.lines().map(String::trim).filter(String::isNotBlank)
            val timestampIndex = lines.indexOfFirst { it.contains("-->") }
            if (timestampIndex < 0) return@mapNotNull null
            val match = TIMESTAMP.find(lines[timestampIndex]) ?: return@mapNotNull null
            val index = lines.firstOrNull()?.toIntOrNull() ?: 0
            val text = lines.drop(timestampIndex + 1).joinToString(" ").trim()
            if (text.isBlank()) return@mapNotNull null
            Cue(
                index = index,
                startSeconds = timestampSeconds(match, 1),
                endSeconds = timestampSeconds(match, 5),
                text = text
            )
        }
    }

    fun segmentCount(srt: String): Int {
        if (srt.isBlank()) return 0
        return Regex("^\\d+\\s*$", RegexOption.MULTILINE).findAll(srt).count().coerceAtLeast(
            srt.trim().split(Regex("\\n\\s*\\n")).count { it.isNotBlank() }
        )
    }

    /** Duration in seconds from the last cue end time, if parseable. */
    fun durationSeconds(srt: String): Double {
        val match = TIMESTAMP
            .findAll(srt)
            .lastOrNull()
            ?: return 0.0
        val endH = match.groupValues[5].toDouble()
        val endM = match.groupValues[6].toDouble()
        val endS = match.groupValues[7].toDouble()
        val endMs = match.groupValues[8].toDouble()
        return endH * 3600 + endM * 60 + endS + endMs / 1000.0
    }

    private fun timestampSeconds(match: MatchResult, offset: Int): Double =
        match.groupValues[offset].toDouble() * 3600 +
            match.groupValues[offset + 1].toDouble() * 60 +
            match.groupValues[offset + 2].toDouble() +
            match.groupValues[offset + 3].toDouble() / 1000.0

    private val TIMESTAMP =
        Regex("""(\d{2}):(\d{2}):(\d{2}),(\d{3})\s*-->\s*(\d{2}):(\d{2}):(\d{2}),(\d{3})""")

    fun formatClock(seconds: Double): String {
        val total = seconds.toInt().coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    fun toMarkdown(srt: String, title: String): String {
        val body = plainText(srt).ifBlank { srt.trim() }
        return buildString {
            append("# ")
            append(title.removeSuffix(".srt").removeSuffix(".SRT"))
            append("\n\n")
            append(body)
            append('\n')
        }
    }
}

enum class ExportFormat(
    val extension: String,
    val label: String,
    val mimeType: String
) {
    TXT("txt", "Text (.txt)", "text/plain"),
    MD("md", "Markdown (.md)", "text/markdown"),
    SRT("srt", "Subtitles (.srt)", "application/x-subrip")
}
