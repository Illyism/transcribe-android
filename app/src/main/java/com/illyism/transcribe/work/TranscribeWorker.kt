package com.illyism.transcribe.work

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.illyism.transcribe.R
import com.illyism.transcribe.TranscribeApp
import com.illyism.transcribe.domain.PipelineStage
import com.illyism.transcribe.domain.TranscribePipeline
import org.json.JSONObject

class TranscribeWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as TranscribeApp
        val uriString = inputData.getString(KEY_URI) ?: return Result.failure()
        val displayName = inputData.getString(KEY_NAME) ?: "video"
        val videoBytes = inputData.getLong(KEY_SIZE, 0L)
        val apiKey = app.settings.apiKey
        if (apiKey.isBlank()) {
            app.sessionStore.saveError("API key required")
            return Result.failure()
        }

        setForeground(createForegroundInfo("Starting transcription…", 0))

        val pipeline = TranscribePipeline(applicationContext)
        return try {
            val result = pipeline.run(
                videoUri = Uri.parse(uriString),
                displayName = displayName,
                videoBytes = videoBytes,
                apiKey = apiKey,
                model = app.settings.model,
                chunkMinutes = app.settings.chunkMinutes,
                maxParallel = app.settings.maxParallelUploads,
                optimize = !app.settings.rawMode
            ) { progress ->
                val json = JSONObject()
                    .put("stage", progress.stage.name)
                    .put("overallPercent", progress.overallPercent)
                    .put("chunksDone", progress.chunksDone)
                    .put("chunksTotal", progress.chunksTotal)
                    .put("videoBytes", progress.videoBytes)
                    .put("audioBytes", progress.audioBytes)
                    .put("message", progress.message)
                    .toString()
                app.sessionStore.saveProgressJson(json)
                setProgressAsync(
                    workDataOf(
                        KEY_PERCENT to progress.overallPercent,
                        KEY_STAGE to progress.stage.name,
                        KEY_CHUNKS_DONE to progress.chunksDone,
                        KEY_CHUNKS_TOTAL to progress.chunksTotal,
                        KEY_VIDEO_BYTES to progress.videoBytes,
                        KEY_AUDIO_BYTES to progress.audioBytes,
                        KEY_MESSAGE to progress.message
                    )
                )
                setForegroundAsync(createForegroundInfo(progress.message, progress.overallPercent))
            }

            // Clear in-flight progress; finished transcripts live in HistoryStore.
            app.sessionStore.clearProgressAndError()
            Result.success(
                workDataOf(
                    KEY_SRT to result.srtPath,
                    KEY_PREVIEW to result.preview,
                    KEY_LANGUAGE to result.language,
                    KEY_DURATION_SEC to result.durationSeconds.toFloat()
                )
            )
        } catch (t: Throwable) {
            val message = t.message ?: "Transcription failed"
            app.sessionStore.saveError(message)
            app.sessionStore.saveProgressJson(
                JSONObject()
                    .put("stage", PipelineStage.FAILED.name)
                    .put("overallPercent", 0)
                    .put("chunksDone", 0)
                    .put("chunksTotal", 0)
                    .put("videoBytes", videoBytes)
                    .put("audioBytes", 0)
                    .put("message", message)
                    .toString()
            )
            Result.failure(workDataOf(KEY_ERROR to message))
        }
    }

    private fun createForegroundInfo(text: String, percent: Int): ForegroundInfo {
        val notification: Notification = NotificationCompat.Builder(
            applicationContext,
            TranscribeApp.NOTIFICATION_CHANNEL_ID
        )
            .setContentTitle("Transcribe")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent.coerceIn(0, 100), percent <= 0)
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
        private const val NOTIFICATION_ID = 42
    }
}
