package com.illyism.transcribe.domain.skills

import com.illyism.transcribe.domain.ResponsesClient
import com.illyism.transcribe.domain.SrtBuilder
import org.json.JSONObject
import java.io.File

data class SkillRunContext(
    val transcriptId: String,
    val filename: String,
    val srtPath: String,
    val language: String = "",
    val durationSeconds: Double = 0.0,
    val customPrompt: String = ""
)

class SkillRunner(
    val responsesClient: ResponsesClient = ResponsesClient()
) {
    fun cancel() = responsesClient.cancel()

    fun run(
        skill: Skill,
        context: SkillRunContext,
        apiKey: String,
        model: String,
        reasoningEffort: String? = null,
        selectedOutputIds: List<String>,
        onReasoningDelta: ((String) -> Unit)? = null,
        onPartial: ((List<SkillOutputResult>) -> Unit)? = null
    ): SkillRunResult {
        val selected = skill.outputs.filter { it.id in selectedOutputIds }.ifEmpty { skill.outputs }
        val srt = File(context.srtPath).takeIf { it.exists() }?.readText().orEmpty()
        val plain = SrtBuilder.plainText(srt)
        val instructions = buildInstructions(skill, context, selected)
        val input = buildInput(skill, context, srt, plain)
        val schema = buildJsonSchema(selected)
        val outputDelta: ((String) -> Unit)? = onPartial?.let { emit ->
            { accumulated -> emit(partialOutputs(selected, accumulated)) }
        }
        val completion = responsesClient.complete(
            apiKey = apiKey,
            model = model,
            instructions = instructions,
            input = input,
            reasoningEffort = reasoningEffort,
            jsonSchema = schema,
            onReasoningDelta = onReasoningDelta,
            onOutputDelta = outputDelta
        )
        return parseResult(skill, selected, completion.text, completion.reasoningSummary)
    }

    /**
     * Build partial result cards from an in-flight (possibly unterminated) JSON
     * object string, extracting each selected output's growing string value.
     */
    private fun partialOutputs(
        selected: List<SkillOutput>,
        accumulated: String
    ): List<SkillOutputResult> {
        val values = extractPartialStrings(accumulated, selected.map { it.id })
        return selected.mapNotNull { out ->
            val content = values[out.id]?.trim().orEmpty()
            if (content.isBlank()) {
                null
            } else {
                SkillOutputResult(
                    outputId = out.id,
                    label = out.label,
                    type = out.type,
                    content = content
                )
            }
        }
    }

    private fun buildInstructions(
        skill: Skill,
        context: SkillRunContext,
        selected: List<SkillOutput>
    ): String {
        val outputSchema = selected.joinToString("\n") { out ->
            "- \"${out.id}\": ${out.label} (${out.type.name.lowercase()})" +
                if (out.hint.isNotBlank()) " — ${out.hint}" else ""
        }
        return buildString {
            appendLine("You are a transcript skills assistant.")
            appendLine("Return a single JSON object matching the schema.")
            appendLine("Each value must be a string (markdown or plain text as appropriate).")
            appendLine()
            appendLine("Skill instructions:")
            appendLine(skill.prompt)
            appendLine()
            appendLine("Requested outputs:")
            appendLine(outputSchema)
            if (skill.id == BuiltInSkills.askAi.id && context.customPrompt.isNotBlank()) {
                appendLine()
                appendLine("User question:")
                appendLine(context.customPrompt)
            }
        }.trim()
    }

    private fun buildInput(
        skill: Skill,
        context: SkillRunContext,
        srt: String,
        plain: String
    ): String = buildString {
        appendLine("Filename: ${context.filename}")
        if (context.language.isNotBlank()) appendLine("Language: ${context.language}")
        if (context.durationSeconds > 0) {
            appendLine("Duration: ${SrtBuilder.formatClock(context.durationSeconds)}")
        }
        if (SkillInput.TRANSCRIPT in skill.inputs) {
            appendLine()
            appendLine("=== TRANSCRIPT (plain) ===")
            appendLine(plain.take(120_000))
        }
        if (SkillInput.SRT in skill.inputs && srt.isNotBlank()) {
            appendLine()
            appendLine("=== TRANSCRIPT (SRT) ===")
            appendLine(srt.take(80_000))
        }
    }.trim()

    private fun buildJsonSchema(selected: List<SkillOutput>): JSONObject {
        val properties = JSONObject()
        val required = org.json.JSONArray()
        selected.forEach { out ->
            properties.put(
                out.id,
                JSONObject()
                    .put("type", "string")
                    .put("description", out.hint.ifBlank { out.label })
            )
            required.put(out.id)
        }
        return JSONObject()
            .put("type", "object")
            .put("properties", properties)
            .put("required", required)
            .put("additionalProperties", false)
    }

    private fun parseResult(
        skill: Skill,
        selected: List<SkillOutput>,
        raw: String,
        reasoningSummary: String?
    ): SkillRunResult {
        val json = try {
            JSONObject(raw)
        } catch (_: Exception) {
            val cleaned = raw
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            JSONObject(cleaned)
        }

        val outputs = selected.map { out ->
            val value = when {
                json.has(out.id) -> json.opt(out.id)
                else -> null
            }
            val content = when (value) {
                null -> ""
                is String -> value
                else -> value.toString()
            }
            SkillOutputResult(
                outputId = out.id,
                label = out.label,
                type = out.type,
                content = content.trim()
            )
        }.filter { it.content.isNotBlank() }.ifEmpty {
            listOf(
                SkillOutputResult(
                    outputId = selected.firstOrNull()?.id ?: "result",
                    label = selected.firstOrNull()?.label ?: "Result",
                    type = selected.firstOrNull()?.type ?: SkillOutputType.MARKDOWN,
                    content = raw.trim()
                )
            )
        }

        return SkillRunResult(
            skillId = skill.id,
            skillName = skill.name,
            outputs = outputs,
            reasoning = reasoningSummary?.trim()?.takeIf { it.isNotBlank() }
        )
    }
}

/**
 * Extract the (possibly unterminated) string value for each [keys] entry from a
 * partial JSON object [raw]. Tolerant of incomplete input mid-stream: an open
 * string is returned up to the current buffer end, dangling escapes are dropped.
 * Only used for live preview — the final result uses a strict JSON parse.
 */
internal fun extractPartialStrings(raw: String, keys: List<String>): Map<String, String> {
    val result = LinkedHashMap<String, String>()
    for (key in keys) {
        extractStringForKey(raw, key)?.let { result[key] = it }
    }
    return result
}

private fun extractStringForKey(raw: String, key: String): String? {
    val marker = "\"$key\""
    var idx = raw.indexOf(marker)
    while (idx >= 0) {
        var i = idx + marker.length
        while (i < raw.length && raw[i].isWhitespace()) i++
        if (i < raw.length && raw[i] == ':') {
            i++
            while (i < raw.length && raw[i].isWhitespace()) i++
            if (i < raw.length && raw[i] == '"') {
                return decodeJsonStringFrom(raw, i + 1)
            }
        }
        idx = raw.indexOf(marker, idx + 1)
    }
    return null
}

private fun decodeJsonStringFrom(raw: String, start: Int): String {
    val sb = StringBuilder()
    var i = start
    while (i < raw.length) {
        when (val c = raw[i]) {
            '"' -> return sb.toString()
            '\\' -> {
                if (i + 1 >= raw.length) return sb.toString() // dangling escape at buffer end
                when (val n = raw[i + 1]) {
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    '/' -> sb.append('/')
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    'r' -> sb.append('\r')
                    'b' -> sb.append('\b')
                    'f' -> sb.append('\u000C')
                    'u' -> {
                        if (i + 5 < raw.length) {
                            raw.substring(i + 2, i + 6).toIntOrNull(16)?.let {
                                sb.append(it.toChar())
                            }
                            i += 4
                        } else {
                            return sb.toString() // incomplete \uXXXX at buffer end
                        }
                    }
                    else -> sb.append(n)
                }
                i += 2
                continue
            }
            else -> sb.append(c)
        }
        i++
    }
    return sb.toString() // unterminated string — partial value
}
