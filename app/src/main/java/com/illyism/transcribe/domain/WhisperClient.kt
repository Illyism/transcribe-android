package com.illyism.transcribe.domain

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.min

class WhisperClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)
        .writeTimeout(10, TimeUnit.MINUTES)
        .build()
) {
    fun transcribe(file: File, apiKey: String, model: String): WhisperResult {
        var attempt = 0
        while (true) {
            try {
                return transcribeOnce(file, apiKey, model)
            } catch (error: TranscribeException) {
                val retryable = error.kind in setOf(
                    TranscribeErrorKind.NETWORK,
                    TranscribeErrorKind.RATE_LIMIT,
                    TranscribeErrorKind.SERVER
                )
                if (!retryable || attempt >= MAX_RETRIES) throw error
                Thread.sleep(min(30_000L, 1_000L shl attempt))
                attempt++
            }
        }
    }

    private fun transcribeOnce(file: File, apiKey: String, model: String): WhisperResult {
        val mime = mimeFor(file)
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, file.asRequestBody(mime.toMediaType()))
            .addFormDataPart("model", model)
            .addFormDataPart("response_format", "verbose_json")
            .addFormDataPart("timestamp_granularities[]", "segment")
            .build()

        val request = Request.Builder()
            .url("https://api.openai.com/v1/audio/transcriptions")
            .header("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw apiError(response.code, raw)
                }
                return parse(raw)
            }
        } catch (error: TranscribeException) {
            throw error
        } catch (error: IOException) {
            throw TranscribeException(
                kind = TranscribeErrorKind.NETWORK,
                scope = ErrorScope.JOB,
                message = "Network unavailable. Retrying may help.",
                cause = error
            )
        }
    }

    private fun apiError(code: Int, raw: String): TranscribeException {
        val errorType = runCatching {
            JSONObject(raw).optJSONObject("error")?.optString("type").orEmpty()
        }.getOrDefault("")
        val kind = when {
            code == 401 || code == 403 -> TranscribeErrorKind.AUTH
            code == 429 && errorType == "insufficient_quota" -> TranscribeErrorKind.QUOTA
            code == 429 -> TranscribeErrorKind.RATE_LIMIT
            code >= 500 -> TranscribeErrorKind.SERVER
            code == 413 -> TranscribeErrorKind.TOO_LARGE
            else -> TranscribeErrorKind.GENERIC
        }
        val scope = if (kind in setOf(TranscribeErrorKind.AUTH, TranscribeErrorKind.QUOTA)) {
            ErrorScope.QUEUE
        } else {
            ErrorScope.JOB
        }
        val message = when (kind) {
            TranscribeErrorKind.AUTH -> "OpenAI rejected the API key. Update it in Settings."
            TranscribeErrorKind.QUOTA -> "OpenAI quota reached. Review your billing."
            TranscribeErrorKind.RATE_LIMIT -> "OpenAI rate limit reached."
            TranscribeErrorKind.SERVER -> "OpenAI is temporarily unavailable."
            TranscribeErrorKind.TOO_LARGE -> "An audio chunk exceeded OpenAI's upload limit."
            else -> "OpenAI transcription failed (HTTP $code)."
        }
        return TranscribeException(kind, scope, message)
    }

    private fun parse(raw: String): WhisperResult {
        val json = JSONObject(raw)
        val text = json.optString("text")
        val language = json.optString("language", "unknown")
        val segmentsJson = json.optJSONArray("segments")
        val segments = buildList {
            if (segmentsJson != null) {
                for (i in 0 until segmentsJson.length()) {
                    val s = segmentsJson.getJSONObject(i)
                    add(
                        WhisperSegment(
                            start = s.optDouble("start"),
                            end = s.optDouble("end"),
                            text = s.optString("text")
                        )
                    )
                }
            }
        }
        return WhisperResult(text = text, language = language, segments = segments)
    }

    private fun mimeFor(file: File): String = when (file.extension.lowercase()) {
        "mp3" -> "audio/mpeg"
        "mp4", "m4a" -> "audio/mp4"
        "wav" -> "audio/wav"
        "ogg" -> "audio/ogg"
        "webm" -> "audio/webm"
        "flac" -> "audio/flac"
        else -> "application/octet-stream"
    }

    private companion object {
        const val MAX_RETRIES = 3
    }
}
