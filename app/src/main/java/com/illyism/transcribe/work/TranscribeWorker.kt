package com.illyism.transcribe.work

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.illyism.transcribe.R
import com.illyism.transcribe.MainActivity
import com.illyism.transcribe.TranscribeApp
import com.illyism.transcribe.domain.ErrorScope
import com.illyism.transcribe.domain.JobCancelledException
import com.illyism.transcribe.domain.JobStage
import com.illyism.transcribe.domain.JobState
import com.illyism.transcribe.domain.PipelineStage
import com.illyism.transcribe.domain.TranscribeException
import com.illyism.transcribe.domain.TranscribePipeline
import com.illyism.transcribe.domain.VideoThumbnailExtractor

class TranscribeWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as TranscribeApp
        app.historyStore.recoverInterruptedJobs()
        setForeground(createForegroundInfo("Preparing queue…", 0, null))

        while (!isStopped) {
            val job = app.historyStore.claimNext(app.settings.hasApiKey()) ?: break
            app.sessionStore.saveActiveTranscriptId(job.id)
            val total = app.historyStore.queuedCount() + 1
            val pipeline = TranscribePipeline(applicationContext)

            try {
                val outcome = pipeline.run(
                    jobId = job.id,
                    videoUri = Uri.parse(job.sourceUri),
                    displayName = job.filename,
                    videoBytes = job.sourceFileSizeBytes,
                    apiKey = app.settings.apiKey.takeIf { it.isNotBlank() },
                    model = app.settings.model,
                    chunkMinutes = app.settings.chunkMinutes,
                    maxParallel = app.settings.maxParallelUploads,
                    optimize = !app.settings.rawMode,
                    preparedAudioPath = job.tempAudioPath.takeIf { it.isNotBlank() },
                    isCancellationRequested = {
                        app.historyStore.get(job.id)?.jobState == JobState.CANCELLING
                    }
                ) { progress ->
                    val stage = progress.stage.toJobStage()
                    app.historyStore.update(job.id) {
                        it.copy(
                            jobState = JobState.RUNNING,
                            jobStage = stage,
                            percent = progress.overallPercent,
                            chunksDone = progress.chunksDone,
                            chunksTotal = progress.chunksTotal,
                            uploadedAudioBytes = progress.audioBytes.takeIf { bytes -> bytes > 0 }
                                ?: it.uploadedAudioBytes,
                            stageMessage = progress.message
                        )
                    }
                    setProgressAsync(
                        workDataOf(
                            KEY_ACTIVE_ID to job.id,
                            KEY_PERCENT to progress.overallPercent,
                            KEY_STAGE to stage.name,
                            KEY_CHUNKS_DONE to progress.chunksDone,
                            KEY_CHUNKS_TOTAL to progress.chunksTotal,
                            KEY_MESSAGE to progress.message
                        )
                    )
                    setForegroundAsync(
                        createForegroundInfo(
                            "${job.filename} · ${progress.message}",
                            progress.overallPercent,
                            job.id,
                            total
                        )
                    )
                }

                when (outcome) {
                    is TranscribePipeline.Outcome.WaitingForKey -> {
                        app.historyStore.update(job.id) {
                            it.copy(
                                jobState = JobState.WAITING_FOR_KEY,
                                jobStage = null,
                                percent = 30,
                                uploadedAudioBytes = outcome.audioBytes,
                                tempAudioPath = outcome.preparedAudioPath,
                                stageMessage = "Add an OpenAI API key to continue"
                            )
                        }
                        app.sessionStore.clearActiveTranscriptId()
                        return Result.success()
                    }
                    is TranscribePipeline.Outcome.Completed -> {
                        val result = outcome.result
                        val current = app.historyStore.update(job.id) {
                            it.copy(
                                srtPath = result.srtPath,
                                preview = result.preview,
                                language = result.language,
                                durationSeconds = result.durationSeconds,
                                sourceDurationMs = (result.durationSeconds * 1000).toLong(),
                                uploadedAudioBytes = result.audioBytes,
                                tempAudioPath = "",
                                jobState = JobState.COMPLETED,
                                jobStage = null,
                                percent = 100,
                                chunksDone = it.chunksTotal,
                                stageMessage = "SRT ready",
                                errorKind = null,
                                errorScope = null,
                                errorMessage = ""
                            )
                        }
                        app.settings.recordCompletedJob(job.sourceFileSizeBytes, result.audioBytes)
                        current?.let { completed ->
                            val destination = app.historyStore.thumbnailFile(completed.id)
                            if (VideoThumbnailExtractor.extract(
                                    applicationContext,
                                    Uri.parse(completed.sourceUri),
                                    destination
                                )
                            ) {
                                app.historyStore.update(completed.id) {
                                    it.copy(thumbnailPath = destination.absolutePath)
                                }
                            }
                        }
                    }
                }
            } catch (_: JobCancelledException) {
                app.historyStore.update(job.id) {
                    it.copy(
                        jobState = JobState.CANCELLED,
                        jobStage = null,
                        percent = 0,
                        tempAudioPath = "",
                        stageMessage = "No transcript created"
                    )
                }
            } catch (error: TranscribeException) {
                val state = if (error.scope == ErrorScope.QUEUE) {
                    JobState.NEEDS_ATTENTION
                } else {
                    JobState.FAILED
                }
                app.historyStore.update(job.id) {
                    it.copy(
                        jobState = state,
                        jobStage = null,
                        errorKind = error.kind,
                        errorScope = error.scope,
                        errorMessage = error.message,
                        stageMessage = error.message
                    )
                }
                if (error.scope == ErrorScope.QUEUE) {
                    app.sessionStore.clearActiveTranscriptId()
                    return Result.success()
                }
            } catch (error: Throwable) {
                app.historyStore.update(job.id) {
                    it.copy(
                        jobState = JobState.FAILED,
                        jobStage = null,
                        errorMessage = error.message ?: "Transcription failed",
                        stageMessage = error.message ?: "Transcription failed"
                    )
                }
            } finally {
                app.sessionStore.clearActiveTranscriptId()
            }
        }
        return Result.success()
    }

    private fun createForegroundInfo(
        text: String,
        percent: Int,
        activeId: String?,
        total: Int = 1
    ): ForegroundInfo {
        val openIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(
            applicationContext,
            TranscribeApp.NOTIFICATION_CHANNEL_ID
        )
            .setContentTitle(if (total > 1) "Transcribing 1 of $total files" else "Transcribing")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent.coerceIn(0, 100), percent <= 0)
            .setContentIntent(openIntent)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .apply {
                if (activeId != null) {
                    addAction(
                        0,
                        "Cancel",
                        CancelTranscriptionReceiver.pendingIntent(applicationContext, activeId)
                    )
                }
            }
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val UNIQUE_WORK = "transcribe_job"
        const val KEY_URI = "uri"
        const val KEY_NAME = "name"
        const val KEY_SIZE = "size"
        const val KEY_PERCENT = "percent"
        const val KEY_STAGE = "stage"
        const val KEY_CHUNKS_DONE = "chunks_done"
        const val KEY_CHUNKS_TOTAL = "chunks_total"
        const val KEY_VIDEO_BYTES = "video_bytes"
        const val KEY_AUDIO_BYTES = "audio_bytes"
        const val KEY_MESSAGE = "message"
        const val KEY_SRT = "srt"
        const val KEY_PREVIEW = "preview"
        const val KEY_LANGUAGE = "language"
        const val KEY_DURATION_SEC = "duration_sec"
        const val KEY_ERROR = "error"
        const val KEY_ACTIVE_ID = "active_id"
        private const val NOTIFICATION_ID = 42
    }
}

private fun PipelineStage.toJobStage(): JobStage = when (this) {
    PipelineStage.EXTRACTING -> JobStage.EXTRACTING_AUDIO
    PipelineStage.OPTIMIZING -> JobStage.OPTIMIZING_AUDIO
    PipelineStage.CHUNKING -> JobStage.PREPARING_CHUNKS
    PipelineStage.TRANSCRIBING -> JobStage.UPLOADING_CHUNKS
    PipelineStage.SAVING, PipelineStage.DONE -> JobStage.SAVING
    PipelineStage.IDLE, PipelineStage.FAILED -> JobStage.EXTRACTING_AUDIO
}
