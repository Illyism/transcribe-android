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

class WhisperClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)
        .writeTimeout(10, TimeUnit.MINUTES)
        .build()
) {
    fun transcribe(file: File, apiKey: String, model: String): WhisperResult {
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

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("Whisper API ${response.code}: ${raw.take(500)}")
            }
            return parse(raw)
        }
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
}
