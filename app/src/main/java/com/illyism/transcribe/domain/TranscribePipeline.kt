package com.illyism.transcribe.domain

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

class TranscribePipeline(
    private val context: Context,
    private val ffmpeg: FfmpegProcessor = FfmpegProcessor(),
    private val whisper: WhisperClient = WhisperClient()
) {
    suspend fun run(
        videoUri: Uri,
        displayName: String,
        videoBytes: Long,
        apiKey: String,
        model: String,
        chunkMinutes: Int,
        maxParallel: Int,
        optimize: Boolean,
        onProgress: (PipelineProgress) -> Unit
    ): PipelineResult = withContext(Dispatchers.IO) {
        val workDir = UriMediaAccess.workDir(context)
        workDir.deleteRecursively()
        workDir.mkdirs()

        val temps = mutableListOf<File>()
        val (pfd, inputPath) = UriMediaAccess.openFdPath(context, videoUri)

        try {
            onProgress(
                PipelineProgress(
                    stage = PipelineStage.EXTRACTING,
                    overallPercent = 5,
                    videoBytes = videoBytes,
                    message = "Extracting audio…"
                )
            )

            val extracted = File(workDir, "extracted_${System.currentTimeMillis()}.mp3")
            temps += extracted
            ffmpeg.extractAudio(inputPath, extracted)

            var audioFile = extracted
            var speedFactor = 1.0

            if (optimize) {
                onProgress(
                    PipelineProgress(
                        stage = PipelineStage.OPTIMIZING,
                        overallPercent = 20,
                        videoBytes = videoBytes,
                        audioBytes = extracted.length(),
                        message = "Optimizing audio…"
                    )
                )
                val optimized = File(workDir, "optimized_${System.currentTimeMillis()}.mp3")
                temps += optimized
                ffmpeg.optimizeSpeed(extracted, optimized)
                audioFile = optimized
                speedFactor = FfmpegProcessor.SPEED_FACTOR

                if (audioFile.length() > FfmpegProcessor.MAX_UPLOAD_BYTES) {
                    val duration = ffmpeg.getDurationSeconds(audioFile.absolutePath)
                    val bitrate = FfmpegProcessor.targetBitrateForSize(duration)
                    val ogg = File(workDir, "compressed_${System.currentTimeMillis()}.ogg")
                    temps += ogg
                    ffmpeg.compressToOgg(audioFile, ogg, bitrate)
                    audioFile = ogg
                }
            }

            val audioBytes = audioFile.length()
            onProgress(
                PipelineProgress(
                    stage = PipelineStage.CHUNKING,
                    overallPercent = 30,
                    videoBytes = videoBytes,
                    audioBytes = audioBytes,
                    message = "Chunking audio…"
                )
            )

            val chunkSecondsOriginal = max(60, chunkMinutes * 60).toDouble()
            val chunkSecondsOptimized = chunkSecondsOriginal / speedFactor
            val prefix = "chunks_${System.currentTimeMillis()}"
            val chunks = ffmpeg.splitIntoChunks(audioFile, chunkSecondsOptimized, workDir, prefix)
            temps += chunks

            val tooLarge = chunks.firstOrNull { it.length() > FfmpegProcessor.MAX_UPLOAD_BYTES }
            if (tooLarge != null) {
                throw IllegalStateException(
                    "Audio chunk is still too large for Whisper (~24MB). " +
                        "Try a smaller chunk length or disable Raw mode."
                )
            }

            val chunkDurations = chunks.map { ffmpeg.getDurationSeconds(it.absolutePath) }
            var cursor = 0.0
            val offsets = chunkDurations.map { d ->
                val o = cursor
                cursor += d
                o
            }

            onProgress(
                PipelineProgress(
                    stage = PipelineStage.TRANSCRIBING,
                    overallPercent = 35,
                    chunksDone = 0,
                    chunksTotal = chunks.size,
                    videoBytes = videoBytes,
                    audioBytes = audioBytes,
                    message = "Transcribing chunks…"
                )
            )

            val results = Array<WhisperResult?>(chunks.size) { null }
            val semaphore = Semaphore(maxParallel.coerceIn(1, 8))
            var doneCount = 0

            coroutineScope {
                chunks.mapIndexed { index, chunk ->
                    async {
                        semaphore.withPermit {
                            val result = whisper.transcribe(chunk, apiKey, model)
                            synchronized(results) {
                                results[index] = result
                                doneCount++
                                val pct = 35 + ((doneCount.toDouble() / chunks.size) * 55).toInt()
                                onProgress(
                                    PipelineProgress(
                                        stage = PipelineStage.TRANSCRIBING,
                                        overallPercent = pct.coerceAtMost(90),
                                        chunksDone = doneCount,
                                        chunksTotal = chunks.size,
                                        videoBytes = videoBytes,
                                        audioBytes = audioBytes,
                                        message = "Transcribing chunks ($doneCount/${chunks.size})"
                                    )
                                )
                            }
                            result
                        }
                    }
                }.awaitAll()
            }

            onProgress(
                PipelineProgress(
                    stage = PipelineStage.SAVING,
                    overallPercent = 95,
                    chunksDone = chunks.size,
                    chunksTotal = chunks.size,
                    videoBytes = videoBytes,
                    audioBytes = audioBytes,
                    message = "Saving SRT…"
                )
            )

            val merged = mutableListOf<WhisperSegment>()
            val text = StringBuilder()
            var language = "unknown"
            results.forEachIndexed { index, result ->
                val r = result ?: return@forEachIndexed
                if (index == 0) language = r.language
                text.append(r.text).append('\n')
                val offset = offsets[index]
                merged += r.segments.map { seg ->
                    WhisperSegment(
                        start = (seg.start + offset) * speedFactor,
                        end = (seg.end + offset) * speedFactor,
                        text = seg.text
                    )
                }
            }
            merged.sortBy { it.start }

            val srt = SrtBuilder.fromSegments(merged)
            val base = displayName.substringBeforeLast('.', displayName)
            val outFile = File(UriMediaAccess.outputDir(context), "$base.srt")
            outFile.writeText(srt, Charsets.UTF_8)

            onProgress(
                PipelineProgress(
                    stage = PipelineStage.DONE,
                    overallPercent = 100,
                    chunksDone = chunks.size,
                    chunksTotal = chunks.size,
                    videoBytes = videoBytes,
                    audioBytes = audioBytes,
                    message = "SRT saved"
                )
            )

            PipelineResult(
                srtPath = outFile.absolutePath,
                preview = SrtBuilder.preview(srt),
                language = language,
                durationSeconds = cursor * speedFactor,
                videoBytes = videoBytes,
                audioBytes = audioBytes
            )
        } finally {
            runCatching { pfd.close() }
            temps.forEach { runCatching { if (it.exists()) it.delete() } }
            // Keep work dir cleanup light; remove leftover chunk files
            workDir.listFiles()?.forEach { runCatching { it.delete() } }
        }
    }
}
