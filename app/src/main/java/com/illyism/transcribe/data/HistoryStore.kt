package com.illyism.transcribe.data

import android.content.Context
import com.illyism.transcribe.domain.skills.SkillOutputResult
import com.illyism.transcribe.domain.skills.SkillOutputType
import com.illyism.transcribe.domain.skills.SkillRunResult
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class HistoryEntry(
    val id: String,
    val filename: String,
    val srtPath: String,
    val preview: String,
    val language: String = "",
    val durationSeconds: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis()
)

class HistoryStore(context: Context) {
    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, "history.json")
    private val resultsDir = File(appContext.filesDir, "skill_results").also { it.mkdirs() }
    private val lock = Any()

    fun list(): List<HistoryEntry> = synchronized(lock) {
        loadEntries().sortedByDescending { it.createdAt }
    }

    fun get(id: String): HistoryEntry? = synchronized(lock) {
        loadEntries().find { it.id == id }
    }

    fun append(
        filename: String,
        srtPath: String,
        preview: String,
        language: String = "",
        durationSeconds: Double = 0.0,
        id: String = UUID.randomUUID().toString()
    ): HistoryEntry {
        val entry = HistoryEntry(
            id = id,
            filename = filename,
            srtPath = srtPath,
            preview = preview.take(400),
            language = language,
            durationSeconds = durationSeconds,
            createdAt = System.currentTimeMillis()
        )
        synchronized(lock) {
            val list = loadEntries().toMutableList()
            // Deduplicate by srt path — refresh existing
            val idx = list.indexOfFirst { it.srtPath == srtPath }
            if (idx >= 0) {
                list[idx] = entry.copy(id = list[idx].id, createdAt = list[idx].createdAt)
            } else {
                list.add(entry)
            }
            persist(list)
            return if (idx >= 0) list[idx] else entry
        }
    }

    fun update(id: String, transform: (HistoryEntry) -> HistoryEntry): HistoryEntry? =
        synchronized(lock) {
            val list = loadEntries().toMutableList()
            val idx = list.indexOfFirst { it.id == id }
            if (idx < 0) return null
            val updated = transform(list[idx])
            list[idx] = updated
            persist(list)
            updated
        }

    fun delete(id: String): Boolean = synchronized(lock) {
        val list = loadEntries().toMutableList()
        val removed = list.removeAll { it.id == id }
        if (removed) {
            persist(list)
            resultsDir.listFiles()?.filter { it.name.startsWith("${id}_") }?.forEach { it.delete() }
        }
        removed
    }

    fun cacheSkillResult(transcriptId: String, result: SkillRunResult) {
        val f = resultFile(transcriptId, result.skillId)
        f.writeText(resultToJson(result).toString(2))
    }

    fun getCachedSkillResult(transcriptId: String, skillId: String): SkillRunResult? {
        val f = resultFile(transcriptId, skillId)
        if (!f.exists()) return null
        return try {
            resultFromJson(JSONObject(f.readText()))
        } catch (_: Exception) {
            null
        }
    }

    private fun resultFile(transcriptId: String, skillId: String): File =
        File(resultsDir, "${transcriptId}_${skillId}.json")

    private fun loadEntries(): List<HistoryEntry> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        HistoryEntry(
                            id = o.optString("id"),
                            filename = o.optString("filename"),
                            srtPath = o.optString("srtPath"),
                            preview = o.optString("preview"),
                            language = o.optString("language"),
                            durationSeconds = o.optDouble("durationSeconds", 0.0),
                            createdAt = o.optLong("createdAt", 0L)
                        )
                    )
                }
            }.filter { it.id.isNotBlank() && it.srtPath.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun persist(entries: List<HistoryEntry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(
                JSONObject()
                    .put("id", e.id)
                    .put("filename", e.filename)
                    .put("srtPath", e.srtPath)
                    .put("preview", e.preview)
                    .put("language", e.language)
                    .put("durationSeconds", e.durationSeconds)
                    .put("createdAt", e.createdAt)
            )
        }
        file.writeText(arr.toString(2))
    }

    private fun resultToJson(result: SkillRunResult): JSONObject = JSONObject().apply {
        put("skillId", result.skillId)
        put("skillName", result.skillName)
        if (!result.reasoning.isNullOrBlank()) put("reasoning", result.reasoning)
        put("outputs", JSONArray().apply {
            result.outputs.forEach { out ->
                put(
                    JSONObject()
                        .put("outputId", out.outputId)
                        .put("label", out.label)
                        .put("type", out.type.name)
                        .put("content", out.content)
                )
            }
        })
    }

    private fun resultFromJson(json: JSONObject): SkillRunResult {
        val arr = json.optJSONArray("outputs") ?: JSONArray()
        val outputs = buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val type = runCatching {
                    SkillOutputType.valueOf(o.optString("type", SkillOutputType.MARKDOWN.name))
                }.getOrDefault(SkillOutputType.MARKDOWN)
                add(
                    SkillOutputResult(
                        outputId = o.optString("outputId"),
                        label = o.optString("label"),
                        type = type,
                        content = o.optString("content")
                    )
                )
            }
        }
        return SkillRunResult(
            skillId = json.optString("skillId"),
            skillName = json.optString("skillName"),
            outputs = outputs,
            reasoning = json.optString("reasoning").takeIf { it.isNotBlank() }
        )
    }
}
