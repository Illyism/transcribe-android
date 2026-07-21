package com.illyism.transcribe.domain

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import kotlin.math.max

class FfmpegProcessor {
    fun getDurationSeconds(inputPath: String): Double {
        val session = FFprobeKit.getMediaInformation(inputPath)
        val info = session.mediaInformation
            ?: throw IllegalStateException("Could not probe media: $inputPath")
        return info.duration?.toDoubleOrNull()
            ?: throw IllegalStateException("Missing duration for $inputPath")
    }

    fun extractAudio(inputPath: String, outputFile: File) {
        outputFile.parentFile?.mkdirs()
        // FFmpeg 8 removed runtime `-ac N`; force mono via aformat instead.
        val cmd = listOf(
            "-i", quote(inputPath),
            "-vn",
            "-af", "aformat=channel_layouts=mono",
            "-ar", "16000",
            "-acodec", "libmp3lame",
            "-q:a", "2",
            "-y",
            quote(outputFile.absolutePath)
        ).joinToString(" ")
        run(cmd, "Audio extraction failed")
    }

    fun optimizeSpeed(inputFile: File, outputFile: File, speedFactor: Double = SPEED_FACTOR) {
        outputFile.parentFile?.mkdirs()
        val cmd = listOf(
            "-i", quote(inputFile.absolutePath),
            "-filter:a", "atempo=$speedFactor,aformat=channel_layouts=mono",
            "-ar", "16000",
            "-acodec", "libmp3lame",
            "-q:a", "2",
            "-y",
            quote(outputFile.absolutePath)
        ).joinToString(" ")
        run(cmd, "Audio optimization failed")
    }

    fun compressToOgg(inputFile: File, outputFile: File, bitrateKbps: Int) {
        outputFile.parentFile?.mkdirs()
        val safeBitrate = bitrateKbps.coerceIn(24, 64)
        val cmd = listOf(
            "-i", quote(inputFile.absolutePath),
            "-af", "aformat=channel_layouts=mono",
            "-acodec", "libopus",
            "-b:a", "${safeBitrate}k",
            "-f", "ogg",
            "-y",
            quote(outputFile.absolutePath)
        ).joinToString(" ")
        run(cmd, "Audio compression failed")
    }

    fun splitIntoChunks(inputFile: File, chunkSeconds: Double, outputDir: File, prefix: String): List<File> {
        outputDir.mkdirs()
        val ext = inputFile.extension.ifBlank { "mp3" }
        val pattern = File(outputDir, "${prefix}_%03d.$ext").absolutePath
        val cmd = listOf(
            "-i", quote(inputFile.absolutePath),
            "-f", "segment",
            "-segment_time", chunkSeconds.toString(),
            "-reset_timestamps", "1",
            "-c", "copy",
            "-y",
            quote(pattern)
        ).joinToString(" ")
        run(cmd, "Audio chunking failed")

        val chunks = outputDir.listFiles()
            ?.filter { it.name.startsWith("${prefix}_") && it.extension.equals(ext, ignoreCase = true) }
            ?.sortedBy { it.name }
            .orEmpty()

        if (chunks.isEmpty()) {
            throw IllegalStateException("Chunking produced no output files")
        }
        return chunks
    }

    private fun run(command: String, errorPrefix: String) {
        val session = FFmpegKit.execute(command)
        if (!ReturnCode.isSuccess(session.returnCode)) {
            val tail = session.allLogsAsString
                ?.lineSequence()
                ?.toList()
                ?.takeLast(8)
                ?.joinToString("\n")
                .orEmpty()
            throw IllegalStateException(
                buildString {
                    append(errorPrefix)
                    append(" (code ")
                    append(session.returnCode)
                    append(')')
                    if (tail.isNotBlank()) {
                        append("\n")
                        append(tail)
                    }
                }
            )
        }
    }

    private fun quote(path: String): String {
        // FFmpegKit command parser splits on spaces; quote paths with spaces.
        return if (path.any { it.isWhitespace() }) "\"$path\"" else path
    }

    companion object {
        const val SPEED_FACTOR = 1.2
        const val MAX_UPLOAD_BYTES = 24L * 1024 * 1024

        fun targetBitrateForSize(durationSeconds: Double, maxBytes: Long = MAX_UPLOAD_BYTES): Int {
            if (durationSeconds <= 0) return 32
            val target = ((maxBytes / durationSeconds) * 8.0 / 1000.0) - 5.0
            return max(24, target.toInt()).coerceAtMost(64)
        }
    }
}
