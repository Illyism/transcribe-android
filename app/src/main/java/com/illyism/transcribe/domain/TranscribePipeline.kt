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
    sealed interface Outcome {
        data class WaitingForKey(
            val preparedAudioPath: String,
            val audioBytes: Long
        ) : Outcome

        data class Completed(val result: PipelineResult) : Outcome
    }

    suspend fun run(
        jobId: String,
        videoUri: Uri,
        displayName: String,
        videoBytes: Long,
        apiKey: String?,
        model: String,
        chunkMinutes: Int,
        maxParallel: Int,
        optimize: Boolean,
        preparedAudioPath: String? = null,
        isCancellationRequested: () -> Boolean = { false },
        onProgress: (PipelineProgress) -> Unit
    ): Outcome = withContext(Dispatchers.IO) {
        val workDir = File(UriMediaAccess.workDir(context), jobId)
        workDir.mkdirs()

        val temps = mutableListOf<File>()
        var keepPrepared = false

        try {
            fun checkCancelled() {
                if (isCancellationRequested()) throw JobCancelledException()
            }

            var speedFactor = if (optimize) FfmpegProcessor.SPEED_FACTOR else 1.0
            var audioFile = preparedAudioPath
                ?.let(::File)
                ?.takeIf { it.exists() }

            if (audioFile == null) {
                checkCancelled()
                val inputPath = try {
                    UriMediaAccess.safReadPath(context, videoUri)
                } catch (error: Exception) {
                    throw TranscribeException(
                        TranscribeErrorKind.SOURCE_UNAVAILABLE,
                        ErrorScope.JOB,
                        "Source file unavailable. Locate it again or remove this job.",
                        error
                    )
                }
                onProgress(
                    PipelineProgress(
                        stage = PipelineStage.EXTRACTING,
                        overallPercent = 5,
                        videoBytes = videoBytes,
                        message = "Extracting audio…"
                    )
                )

                val extracted = File(workDir, "extracted.mp3")
                temps += extracted
                try {
                    ffmpeg.extractAudio(inputPath, extracted)
                } catch (error: Exception) {
                    throw TranscribeException(
                        TranscribeErrorKind.UNSUPPORTED_MEDIA,
                        ErrorScope.JOB,
                        "Could not extract audio from this source.",
                        error
                    )
                }
                checkCancelled()

                audioFile = extracted
                speedFactor = 1.0

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
                    val optimized = File(workDir, "prepared.mp3")
                    temps += optimized
                    ffmpeg.optimizeSpeed(extracted, optimized)
                    audioFile = optimized
                    speedFactor = FfmpegProcessor.SPEED_FACTOR

                    if (audioFile.length() > FfmpegProcessor.MAX_UPLOAD_BYTES) {
                        val duration = ffmpeg.getDurationSeconds(audioFile.absolutePath)
                        val bitrate = FfmpegProcessor.targetBitrateForSize(duration)
                        val compressed = File(workDir, "prepared.ogg")
                        temps += compressed
                        ffmpeg.compressToOgg(audioFile, compressed, bitrate)
                        audioFile = compressed
                    }
                }
            }

            val preparedAudio = checkNotNull(audioFile)
            val audioBytes = preparedAudio.length()
            if (apiKey.isNullOrBlank()) {
                keepPrepared = true
                temps.filter { it != preparedAudio }.forEach { runCatching { it.delete() } }
                return@withContext Outcome.WaitingForKey(preparedAudio.absolutePath, audioBytes)
            }

            checkCancelled()
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
            val chunks = ffmpeg.splitIntoChunks(preparedAudio, chunkSecondsOptimized, workDir, prefix)
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
                            checkCancelled()
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

            Outcome.Completed(
                PipelineResult(
                    srtPath = outFile.absolutePath,
                    preview = SrtBuilder.preview(srt),
                    language = language,
                    durationSeconds = cursor * speedFactor,
                    videoBytes = videoBytes,
                    audioBytes = audioBytes
                )
            )
        } finally {
            if (!keepPrepared) {
                workDir.deleteRecursively()
            }
        }
    }
}
