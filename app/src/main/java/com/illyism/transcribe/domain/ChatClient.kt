package com.illyism.transcribe.domain

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class ChatMessage(
    val role: String,
    val content: String
)

class ChatClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(2, TimeUnit.MINUTES)
        .build()
) {
    fun complete(
        apiKey: String,
        model: String,
        messages: List<ChatMessage>,
        jsonObject: Boolean = true
    ): String {
        val messagesJson = JSONArray().apply {
            messages.forEach { msg ->
                put(
                    JSONObject()
                        .put("role", msg.role)
                        .put("content", msg.content)
                )
            }
        }
        val bodyJson = JSONObject()
            .put("model", model)
            .put("messages", messagesJson)
        if (jsonObject) {
            bodyJson.put(
                "response_format",
                JSONObject().put("type", "json_object")
            )
        }

        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("Chat API ${response.code}: ${raw.take(500)}")
            }
            val json = JSONObject(raw)
            val choices = json.optJSONArray("choices")
                ?: throw IOException("Chat API: missing choices")
            if (choices.length() == 0) throw IOException("Chat API: empty choices")
            val message = choices.getJSONObject(0).optJSONObject("message")
                ?: throw IOException("Chat API: missing message")
            return message.optString("content").ifBlank {
                throw IOException("Chat API: empty content")
            }
        }
    }
}
