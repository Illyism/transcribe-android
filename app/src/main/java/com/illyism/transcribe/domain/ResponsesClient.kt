package com.illyism.transcribe.domain

import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

data class SkillCompletion(
    val text: String,
    val reasoningSummary: String?
)

/**
 * OpenAI Responses API client for Skills (SSE streaming).
 * @see https://developers.openai.com/api/docs/guides/streaming-responses
 */
class ResponsesClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(2, TimeUnit.MINUTES)
        .build()
) {
    private val activeCall = AtomicReference<Call?>(null)

    fun cancel() {
        activeCall.getAndSet(null)?.cancel()
    }

    /**
     * Streams a Responses API call. [onReasoningDelta] receives incremental
     * reasoning-summary text (best-effort; may never fire). [onOutputDelta]
     * receives the full accumulated output text so far on each delta, so callers
     * can progressively parse the (structured) output as it streams.
     */
    fun complete(
        apiKey: String,
        model: String,
        instructions: String,
        input: String,
        reasoningEffort: String? = null,
        jsonSchema: JSONObject? = null,
        schemaName: String = "skill_result",
        onReasoningDelta: ((String) -> Unit)? = null,
        onOutputDelta: ((String) -> Unit)? = null
    ): SkillCompletion {
        val bodyJson = JSONObject()
            .put("model", model)
            .put("instructions", instructions)
            .put("input", input)
            .put("stream", true)

        if (!reasoningEffort.isNullOrBlank()) {
            bodyJson.put(
                "reasoning",
                JSONObject()
                    .put("effort", reasoningEffort)
                    .put("summary", "auto")
            )
            // Note: do not put reasoning.summary_text in `include` — the API rejects it.
            // Summary text still arrives via response.reasoning_summary_text.* SSE events
            // and/or the completed response's reasoning.summary[] when org supports it.
        }

        if (jsonSchema != null) {
            bodyJson.put(
                "text",
                JSONObject().put(
                    "format",
                    JSONObject()
                        .put("type", "json_schema")
                        .put("name", schemaName)
                        .put("strict", true)
                        .put("schema", jsonSchema)
                )
            )
        }

        val request = Request.Builder()
            .url("https://api.openai.com/v1/responses")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val call = client.newCall(request)
        activeCall.set(call)
        try {
            call.execute().use { response ->
                val body = response.body ?: throw IOException("Responses API: empty body")
                if (!response.isSuccessful) {
                    throw IOException("Responses API ${response.code}: ${body.string().take(500)}")
                }
                return readSse(
                    reader = body.charStream().buffered(),
                    onReasoningDelta = onReasoningDelta,
                    onOutputDelta = onOutputDelta
                )
            }
        } catch (e: Exception) {
            if (call.isCanceled() || e is InterruptedIOException) {
                throw SkillCancelledException()
            }
            throw e
        } finally {
            activeCall.compareAndSet(call, null)
        }
    }

    private fun readSse(
        reader: BufferedReader,
        onReasoningDelta: ((String) -> Unit)?,
        onOutputDelta: ((String) -> Unit)?
    ): SkillCompletion {
        val outputText = StringBuilder()
        val reasoningText = StringBuilder()
        val refusalText = StringBuilder()
        var completedResponse: JSONObject? = null
        var failedMessage: String? = null
        var incompleteReason: String? = null

        var line: String?
        val dataLines = mutableListOf<String>()

        fun flushEvent() {
            if (dataLines.isEmpty()) return
            val payload = dataLines.joinToString("\n").trim()
            dataLines.clear()
            if (payload.isEmpty() || payload == "[DONE]") return

            val event = try {
                JSONObject(payload)
            } catch (_: Exception) {
                return
            }

            when (event.optString("type")) {
                "response.reasoning_summary_text.delta" -> {
                    val delta = event.optString("delta")
                    if (delta.isNotEmpty()) {
                        reasoningText.append(delta)
                        onReasoningDelta?.invoke(delta)
                    }
                }
                "response.reasoning_summary_text.done" -> {
                    val text = event.optString("text")
                    if (text.isNotBlank() && reasoningText.isEmpty()) {
                        reasoningText.append(text)
                        onReasoningDelta?.invoke(text)
                    }
                }
                "response.output_text.delta" -> {
                    val delta = event.optString("delta")
                    if (delta.isNotEmpty()) {
                        outputText.append(delta)
                        onOutputDelta?.invoke(outputText.toString())
                    }
                }
                "response.output_text.done" -> {
                    val text = event.optString("text")
                    if (text.isNotBlank() && outputText.isEmpty()) {
                        outputText.append(text)
                        onOutputDelta?.invoke(outputText.toString())
                    }
                }
                "response.refusal.delta" -> {
                    val delta = event.optString("delta")
                    if (delta.isNotEmpty()) refusalText.append(delta)
                }
                "response.refusal.done" -> {
                    val text = event.optString("text")
                    if (text.isNotBlank() && refusalText.isEmpty()) {
                        refusalText.append(text)
                    }
                }
                "response.completed" -> {
                    val resp = event.optJSONObject("response")
                    completedResponse = resp
                    if (resp?.optString("status") == "incomplete") {
                        incompleteReason = resp.optJSONObject("incomplete_details")
                            ?.optString("reason")
                            ?.takeIf { it.isNotBlank() }
                            ?: "incomplete"
                    }
                }
                "response.failed" -> {
                    val resp = event.optJSONObject("response")
                    failedMessage = resp?.optJSONObject("error")?.optString("message")
                        ?: event.optString("message").ifBlank { "Responses API failed" }
                }
                "error" -> {
                    failedMessage = event.optString("message").ifBlank { "Responses API error" }
                }
            }
        }

        while (reader.readLine().also { line = it } != null) {
            val current = line ?: break
            when {
                current.isEmpty() -> flushEvent()
                current.startsWith("data:") -> dataLines.add(current.removePrefix("data:").trimStart())
                // ignore id:/event:/comment lines — type lives in JSON
            }
        }
        flushEvent()

        if (failedMessage != null) {
            throw IOException(failedMessage)
        }

        // Prefer streamed deltas; fall back to the completed response object.
        val fromCompleted = completedResponse?.let { parseResponseObject(it) }
        val text = outputText.toString().ifBlank { fromCompleted?.text.orEmpty() }
        if (text.isBlank()) {
            val refusal = refusalText.toString().ifBlank { null }
                ?: fromCompleted?.refusal
            if (!refusal.isNullOrBlank()) {
                throw IOException("The model declined: $refusal")
            }
            if (incompleteReason != null) {
                throw IOException("Response was cut off ($incompleteReason)")
            }
            throw IOException("Responses API: empty text output")
        }
        val reasoning = reasoningText.toString().ifBlank { null }
            ?: fromCompleted?.reasoningSummary

        return SkillCompletion(text = text, reasoningSummary = reasoning?.trim()?.takeIf { it.isNotBlank() })
    }

    private data class ParsedResponse(
        val text: String,
        val reasoningSummary: String?,
        val refusal: String?
    )

    private fun parseResponseObject(json: JSONObject): ParsedResponse {
        val directText = json.optString("output_text").takeIf { it.isNotBlank() }
        val textParts = mutableListOf<String>()
        val reasoningParts = mutableListOf<String>()
        val refusalParts = mutableListOf<String>()

        val output = json.optJSONArray("output") ?: JSONArray()
        for (i in 0 until output.length()) {
            val item = output.optJSONObject(i) ?: continue
            when (item.optString("type")) {
                "message" -> {
                    val content = item.optJSONArray("content") ?: continue
                    for (j in 0 until content.length()) {
                        val part = content.optJSONObject(j) ?: continue
                        when (part.optString("type")) {
                            "output_text" -> {
                                val t = part.optString("text")
                                if (t.isNotBlank()) textParts.add(t)
                            }
                            "refusal" -> {
                                val t = part.optString("refusal")
                                    .ifBlank { part.optString("text") }
                                if (t.isNotBlank()) refusalParts.add(t)
                            }
                        }
                    }
                }
                "reasoning" -> {
                    val summary = item.optJSONArray("summary") ?: continue
                    for (j in 0 until summary.length()) {
                        val part = summary.optJSONObject(j) ?: continue
                        if (part.optString("type") == "summary_text") {
                            val t = part.optString("text")
                            if (t.isNotBlank()) reasoningParts.add(t)
                        }
                    }
                }
            }
        }

        return ParsedResponse(
            text = directText ?: textParts.joinToString("\n"),
            reasoningSummary = reasoningParts.joinToString("\n\n").ifBlank { null },
            refusal = refusalParts.joinToString("\n").ifBlank { null }
        )
    }

    class SkillCancelledException : IOException("Skill cancelled")
}
