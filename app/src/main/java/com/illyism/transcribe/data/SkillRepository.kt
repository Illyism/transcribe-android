package com.illyism.transcribe.data

import android.content.Context
import android.net.Uri
import com.illyism.transcribe.domain.skills.BuiltInSkills
import com.illyism.transcribe.domain.skills.Skill
import com.illyism.transcribe.domain.skills.SkillCategory
import com.illyism.transcribe.domain.skills.SkillJson
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class SkillRepository(context: Context) {
    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, "skills.json")
    private val lock = Any()

    /** User-visible built-ins (Catalog is system-only). */
    fun builtIns(): List<Skill> = BuiltInSkills.all

    fun customSkills(): List<Skill> = synchronized(lock) { loadCustom() }

    fun allSkills(): List<Skill> = builtIns() + customSkills()

    fun get(id: String): Skill? =
        BuiltInSkills.byId(id) ?: customSkills().find { it.id == id }

    fun saveCustom(skill: Skill): Skill {
        val toSave = skill.copy(
            builtIn = false,
            category = if (skill.category == SkillCategory.CUSTOM || skill.builtIn) {
                SkillCategory.CUSTOM
            } else {
                skill.category
            },
            id = skill.id.ifBlank { "custom_${UUID.randomUUID()}" }
        )
        synchronized(lock) {
            val list = loadCustom().toMutableList()
            val idx = list.indexOfFirst { it.id == toSave.id }
            if (idx >= 0) list[idx] = toSave else list.add(toSave)
            persist(list)
        }
        return toSave
    }

    fun duplicate(id: String): Skill? {
        val source = get(id) ?: return null
        val copy = source.copy(
            id = "custom_${UUID.randomUUID()}",
            name = "${source.name} (copy)",
            builtIn = false,
            category = SkillCategory.CUSTOM
        )
        return saveCustom(copy)
    }

    fun delete(id: String): Boolean {
        if (BuiltInSkills.byId(id) != null) return false
        synchronized(lock) {
            val list = loadCustom().toMutableList()
            val removed = list.removeAll { it.id == id }
            if (removed) persist(list)
            return removed
        }
    }

    fun exportSkill(id: String, destUri: Uri): Boolean {
        val skill = get(id) ?: return false
        return try {
            appContext.contentResolver.openOutputStream(destUri)?.use { out ->
                out.write(SkillJson.toPrettyString(skill).toByteArray(Charsets.UTF_8))
            } != null
        } catch (_: Exception) {
            false
        }
    }

    fun importSkill(sourceUri: Uri): Skill? {
        return try {
            val text = appContext.contentResolver.openInputStream(sourceUri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            } ?: return null
            val json = JSONObject(text)
            val imported = SkillJson.fromJson(json).copy(
                id = "custom_${UUID.randomUUID()}",
                builtIn = false,
                category = SkillCategory.CUSTOM
            )
            saveCustom(imported)
        } catch (_: Exception) {
            null
        }
    }

    fun newBlank(): Skill = Skill(
        id = "custom_${UUID.randomUUID()}",
        name = "New skill",
        description = "",
        icon = "sparkles",
        color = "#E8A838",
        prompt = "Transform this transcript into useful output.",
        outputs = listOf(
            com.illyism.transcribe.domain.skills.SkillOutput(
                id = "result",
                label = "Result",
                type = com.illyism.transcribe.domain.skills.SkillOutputType.MARKDOWN
            )
        ),
        builtIn = false,
        category = SkillCategory.CUSTOM
    )

    private fun loadCustom(): List<Skill> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            SkillJson.listFromJson(arr).filterNot { it.builtIn }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun persist(skills: List<Skill>) {
        file.writeText(SkillJson.listToJson(skills).toString(2))
    }
}
