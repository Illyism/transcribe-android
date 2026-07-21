package com.illyism.transcribe.domain.skills

import org.json.JSONArray
import org.json.JSONObject

object SkillJson {
    fun toJson(skill: Skill): JSONObject = JSONObject().apply {
        put("id", skill.id)
        put("name", skill.name)
        put("description", skill.description)
        put("icon", skill.icon)
        put("color", skill.color)
        put("prompt", skill.prompt)
        put("inputs", JSONArray(skill.inputs.map { it.name }))
        put("outputs", JSONArray().apply {
            skill.outputs.forEach { out ->
                put(
                    JSONObject()
                        .put("id", out.id)
                        .put("label", out.label)
                        .put("type", out.type.name)
                        .put("hint", out.hint)
                )
            }
        })
        put("exports", JSONArray(skill.exports.map { it.name }))
        put("category", skill.category.name)
        put("builtIn", skill.builtIn)
        put("estimatedRuntime", skill.estimatedRuntime)
        if (skill.defaultTier != null) put("defaultTier", skill.defaultTier)
    }

    fun fromJson(json: JSONObject): Skill {
        val outputsArr = json.optJSONArray("outputs") ?: JSONArray()
        val outputs = buildList {
            for (i in 0 until outputsArr.length()) {
                val o = outputsArr.getJSONObject(i)
                val type = runCatching {
                    SkillOutputType.valueOf(o.optString("type", SkillOutputType.MARKDOWN.name))
                }.getOrDefault(SkillOutputType.MARKDOWN)
                add(
                    SkillOutput(
                        id = o.optString("id").ifBlank { "output_$i" },
                        label = o.optString("label", "Output"),
                        type = type,
                        hint = o.optString("hint")
                    )
                )
            }
        }

        val inputsArr = json.optJSONArray("inputs")
        val inputs = if (inputsArr == null || inputsArr.length() == 0) {
            listOf(SkillInput.TRANSCRIPT, SkillInput.SRT, SkillInput.METADATA)
        } else {
            buildList {
                for (i in 0 until inputsArr.length()) {
                    runCatching { SkillInput.valueOf(inputsArr.getString(i)) }
                        .getOrNull()
                        ?.let { add(it) }
                }
            }.ifEmpty { listOf(SkillInput.TRANSCRIPT) }
        }

        val exportsArr = json.optJSONArray("exports")
        val exports = if (exportsArr == null || exportsArr.length() == 0) {
            listOf(ExportTarget.COPY, ExportTarget.SHARE)
        } else {
            buildList {
                for (i in 0 until exportsArr.length()) {
                    runCatching { ExportTarget.valueOf(exportsArr.getString(i)) }
                        .getOrNull()
                        ?.let { add(it) }
                }
            }.ifEmpty { listOf(ExportTarget.COPY) }
        }

        val category = runCatching {
            SkillCategory.valueOf(json.optString("category", SkillCategory.CUSTOM.name))
        }.getOrDefault(SkillCategory.CUSTOM)

        return Skill(
            id = json.optString("id").ifBlank { "skill_${System.currentTimeMillis()}" },
            name = json.optString("name", "Untitled skill"),
            description = json.optString("description"),
            icon = json.optString("icon", "sparkles"),
            color = json.optString("color", "#E8A838"),
            prompt = json.optString("prompt"),
            inputs = inputs,
            outputs = outputs.ifEmpty {
                listOf(SkillOutput("result", "Result", SkillOutputType.MARKDOWN))
            },
            exports = exports,
            category = category,
            builtIn = json.optBoolean("builtIn", false),
            estimatedRuntime = json.optString("estimatedRuntime", "~30s"),
            defaultTier = json.optString("defaultTier").takeIf { it.isNotBlank() }
        )
    }

    fun toPrettyString(skill: Skill): String = toJson(skill).toString(2)

    fun listToJson(skills: List<Skill>): JSONArray = JSONArray().apply {
        skills.forEach { put(toJson(it)) }
    }

    fun listFromJson(array: JSONArray): List<Skill> = buildList {
        for (i in 0 until array.length()) {
            add(fromJson(array.getJSONObject(i)))
        }
    }
}
