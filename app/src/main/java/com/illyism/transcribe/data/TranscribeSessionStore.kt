package com.illyism.transcribe.data

import android.content.Context
import android.net.Uri
import org.json.JSONObject

/**
 * Lightweight persistence so Processing/Done screens survive process death
 * while WorkManager runs the job.
 */
class TranscribeSessionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("transcribe_session", Context.MODE_PRIVATE)

    fun saveSelected(
        uri: Uri,
        displayName: String,
        sizeBytes: Long,
        durationMs: Long
    ) {
        prefs.edit()
            .putString(KEY_URI, uri.toString())
            .putString(KEY_NAME, displayName)
            .putLong(KEY_SIZE, sizeBytes)
            .putLong(KEY_DURATION, durationMs)
            .remove(KEY_RESULT_SRT)
            .remove(KEY_RESULT_PREVIEW)
            .remove(KEY_ERROR)
            .apply()
    }

    fun saveResult(srtPath: String, preview: String) {
        prefs.edit()
            .putString(KEY_RESULT_SRT, srtPath)
            .putString(KEY_RESULT_PREVIEW, preview)
            .remove(KEY_ERROR)
            .apply()
    }

    fun saveError(message: String) {
        prefs.edit().putString(KEY_ERROR, message).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun selectedVideo(): SelectedVideo? {
        val uri = prefs.getString(KEY_URI, null) ?: return null
        return SelectedVideo(
            uri = Uri.parse(uri),
            displayName = prefs.getString(KEY_NAME, "video") ?: "video",
            sizeBytes = prefs.getLong(KEY_SIZE, 0L),
            durationMs = prefs.getLong(KEY_DURATION, 0L)
        )
    }

    fun resultSrtPath(): String? = prefs.getString(KEY_RESULT_SRT, null)
    fun resultPreview(): String? = prefs.getString(KEY_RESULT_PREVIEW, null)
    fun error(): String? = prefs.getString(KEY_ERROR, null)

    fun saveProgressJson(json: String) {
        prefs.edit().putString(KEY_PROGRESS, json).apply()
    }

    fun progress(): ProgressSnapshot? {
        val raw = prefs.getString(KEY_PROGRESS, null) ?: return null
        return try {
            val o = JSONObject(raw)
            ProgressSnapshot(
                stage = o.optString("stage"),
                overallPercent = o.optInt("overallPercent"),
                chunksDone = o.optInt("chunksDone"),
                chunksTotal = o.optInt("chunksTotal"),
                videoBytes = o.optLong("videoBytes"),
                audioBytes = o.optLong("audioBytes"),
                message = o.optString("message")
            )
        } catch (_: Exception) {
            null
        }
    }

    data class SelectedVideo(
        val uri: Uri,
        val displayName: String,
        val sizeBytes: Long,
        val durationMs: Long
    )

    data class ProgressSnapshot(
        val stage: String,
        val overallPercent: Int,
        val chunksDone: Int,
        val chunksTotal: Int,
        val videoBytes: Long,
        val audioBytes: Long,
        val message: String
    )

    companion object {
        private const val KEY_URI = "uri"
        private const val KEY_NAME = "name"
        private const val KEY_SIZE = "size"
        private const val KEY_DURATION = "duration"
        private const val KEY_RESULT_SRT = "srt"
        private const val KEY_RESULT_PREVIEW = "preview"
        private const val KEY_ERROR = "error"
        private const val KEY_PROGRESS = "progress"
    }
}
