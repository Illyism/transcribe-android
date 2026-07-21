package com.illyism.transcribe.domain.skills

import com.illyism.transcribe.domain.ChatClient
import com.illyism.transcribe.domain.ChatMessage
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
    private val chatClient: ChatClient = ChatClient()
) {
    fun run(
        skill: Skill,
        context: SkillRunContext,
        apiKey: String,
        model: String,
        selectedOutputIds: List<String>
    ): SkillRunResult {
        val selected = skill.outputs.filter { it.id in selectedOutputIds }.ifEmpty { skill.outputs }
        val srt = File(context.srtPath).takeIf { it.exists() }?.readText().orEmpty()
        val plain = SrtBuilder.plainText(srt)
        val messages = buildMessages(skill, context, srt, plain, selected)
        val raw = chatClient.complete(apiKey = apiKey, model = model, messages = messages)
        return parseResult(skill, selected, raw)
    }

    private fun buildMessages(
        skill: Skill,
        context: SkillRunContext,
        srt: String,
        plain: String,
        selected: List<SkillOutput>
    ): List<ChatMessage> {
        val outputSchema = selected.joinToString("\n") { out ->
            "- \"${out.id}\": ${out.label} (${out.type.name.lowercase()})" +
                if (out.hint.isNotBlank()) " — ${out.hint}" else ""
        }
        val system = buildString {
            appendLine("You are a transcript skills assistant.")
            appendLine("Return a single JSON object. Keys must be exactly the requested output ids.")
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
        }

        val user = buildString {
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
        }

        return listOf(
            ChatMessage("system", system.trim()),
            ChatMessage("user", user.trim())
        )
    }

    private fun parseResult(
        skill: Skill,
        selected: List<SkillOutput>,
        raw: String
    ): SkillRunResult {
        val json = try {
            JSONObject(raw)
        } catch (_: Exception) {
            // Model sometimes wraps JSON in markdown fences
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
            outputs = outputs
        )
    }
}
