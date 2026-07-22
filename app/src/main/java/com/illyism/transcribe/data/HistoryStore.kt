package com.illyism.transcribe.data

import android.content.Context
import com.illyism.transcribe.domain.ErrorScope
import com.illyism.transcribe.domain.JobStage
import com.illyism.transcribe.domain.JobState
import com.illyism.transcribe.domain.JobStateMachine
import com.illyism.transcribe.domain.TranscribeErrorKind
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
    val createdAt: Long = System.currentTimeMillis(),
    /** Human title from Catalog enrich (≤60 chars). */
    val title: String = "",
    /** Two-line summary from Catalog enrich. */
    val summary: String = "",
    /** Absolute path to a local JPEG under filesDir/thumbnails/. */
    val thumbnailPath: String = "",
    /** Persistable content Uri of the source video/audio (empty if unavailable). */
    val sourceUri: String = "",
    val mimeType: String = "",
    val sourceFileSizeBytes: Long = 0L,
    val uploadedAudioBytes: Long = 0L,
    val sourceDurationMs: Long = 0L,
    val persistablePermissionGranted: Boolean = false,
    val jobState: JobState = JobState.COMPLETED,
    val jobStage: JobStage? = null,
    val percent: Int = 0,
    val chunksDone: Int = 0,
    val chunksTotal: Int = 0,
    val stageMessage: String = "",
    val errorKind: TranscribeErrorKind? = null,
    val errorScope: ErrorScope? = null,
    val errorMessage: String = "",
    val tempAudioPath: String = "",
    val queueOrder: Long = createdAt
)

/** Lightweight index row for a cached skill run on a transcript. */
data class CachedSkillRun(
    val skillId: String,
    val skillName: String,
    val modifiedAtMillis: Long
)

class HistoryStore(context: Context) {
    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, "history.json")
    private val resultsDir = File(appContext.filesDir, "skill_results").also { it.mkdirs() }
    private val thumbnailsDir = File(appContext.filesDir, "thumbnails").also { it.mkdirs() }
    private val lock = Any()
    private var cachedEntries: List<HistoryEntry>? = null

    fun thumbnailFile(transcriptId: String): File =
        File(thumbnailsDir, "$transcriptId.jpg")

    fun list(): List<HistoryEntry> = synchronized(lock) {
        loadEntries().sortedByDescending { it.createdAt }
    }

    fun get(id: String): HistoryEntry? = synchronized(lock) {
        loadEntries().find { it.id == id }
    }

    /** Provisional row before transcription finishes (empty [HistoryEntry.srtPath]). */
    fun createDraft(
        filename: String,
        sourceUri: String,
        durationSeconds: Double = 0.0,
        mimeType: String = "",
        sourceFileSizeBytes: Long = 0L,
        persistablePermissionGranted: Boolean = false,
        id: String = UUID.randomUUID().toString()
    ): HistoryEntry = synchronized(lock) {
        val list = loadEntries().toMutableList()
        val entry = HistoryEntry(
            id = id,
            filename = filename,
            srtPath = "",
            preview = "",
            durationSeconds = durationSeconds,
            sourceUri = sourceUri,
            mimeType = mimeType,
            sourceFileSizeBytes = sourceFileSizeBytes,
            sourceDurationMs = (durationSeconds * 1000).toLong(),
            persistablePermissionGranted = persistablePermissionGranted,
            jobState = JobState.QUEUED
        )
        list.add(entry)
        persist(list)
        entry
    }

    fun isDraft(entry: HistoryEntry): Boolean =
        entry.jobState != JobState.COMPLETED ||
            entry.srtPath.isBlank() ||
            !File(entry.srtPath).exists()

    fun queuedCount(): Int = synchronized(lock) {
        loadEntries().count { it.jobState == JobState.QUEUED }
    }

    /** Atomically claims one source file. Only the worker dispatcher calls this. */
    fun claimNext(apiKeyAvailable: Boolean): HistoryEntry? = synchronized(lock) {
        val list = loadEntries().toMutableList()
        val candidate = list
            .filter {
                it.jobState == JobState.QUEUED ||
                    (apiKeyAvailable && it.jobState == JobState.WAITING_FOR_KEY)
            }
            .minByOrNull { it.queueOrder }
            ?: return null
        val index = list.indexOfFirst { it.id == candidate.id }
        val nextStage = if (candidate.jobState == JobState.WAITING_FOR_KEY) {
            JobStage.PREPARING_CHUNKS
        } else {
            JobStage.EXTRACTING_AUDIO
        }
        val claimed = candidate.copy(
            jobState = JobState.RUNNING,
            jobStage = nextStage,
            errorKind = null,
            errorScope = null,
            errorMessage = ""
        )
        list[index] = claimed
        persist(list)
        claimed
    }

    fun active(): HistoryEntry? = synchronized(lock) {
        loadEntries().firstOrNull {
            it.jobState in setOf(JobState.RUNNING, JobState.RETRYING, JobState.CANCELLING)
        }
    }

    fun recoverInterruptedJobs() = synchronized(lock) {
        val list = loadEntries().toMutableList()
        var changed = false
        list.indices.forEach { index ->
            val entry = list[index]
            if (entry.jobState in setOf(JobState.RUNNING, JobState.RETRYING, JobState.CANCELLING)) {
                list[index] = entry.copy(
                    jobState = if (entry.jobState == JobState.CANCELLING) {
                        JobState.CANCELLED
                    } else {
                        JobState.QUEUED
                    },
                    jobStage = null,
                    stageMessage = if (entry.jobState == JobState.CANCELLING) {
                        "Cancelled"
                    } else {
                        "Queued after interruption"
                    }
                )
                changed = true
            }
        }
        if (changed) persist(list)
    }

    fun requestCancel(id: String): Boolean =
        update(id) { entry ->
            if (entry.jobState in setOf(JobState.QUEUED, JobState.WAITING_FOR_KEY)) {
                entry.copy(jobState = JobState.CANCELLED, jobStage = null, stageMessage = "Cancelled")
            } else {
                entry.copy(jobState = JobState.CANCELLING, stageMessage = "Cancelling…")
            }
        } != null

    fun append(
        filename: String,
        srtPath: String,
        preview: String,
        language: String = "",
        durationSeconds: Double = 0.0,
        sourceUri: String = "",
        id: String = UUID.randomUUID().toString()
    ): HistoryEntry {
        synchronized(lock) {
            val list = loadEntries().toMutableList()
            val idx = list.indexOfFirst { it.srtPath == srtPath }
            val entryId = if (idx >= 0) list[idx].id else id
            if (idx >= 0) {
                deleteThumbnailFile(list[idx])
            }
            val entry = HistoryEntry(
                id = entryId,
                filename = filename,
                srtPath = srtPath,
                preview = preview.take(400),
                language = language,
                durationSeconds = durationSeconds,
                createdAt = if (idx >= 0) list[idx].createdAt else System.currentTimeMillis(),
                sourceUri = sourceUri.ifBlank {
                    if (idx >= 0) list[idx].sourceUri else ""
                },
                jobState = JobState.COMPLETED
            )
            if (idx >= 0) {
                list[idx] = entry
            } else {
                list.add(entry)
            }
            persist(list)
            return entry
        }
    }

    fun update(id: String, transform: (HistoryEntry) -> HistoryEntry): HistoryEntry? =
        synchronized(lock) {
            val list = loadEntries().toMutableList()
            val idx = list.indexOfFirst { it.id == id }
            if (idx < 0) return null
            val updated = transform(list[idx])
        JobStateMachine.requireTransition(list[idx].jobState, updated.jobState)
            list[idx] = updated
            persist(list)
            updated
        }

    fun delete(id: String): Boolean = synchronized(lock) {
        val list = loadEntries().toMutableList()
        val existing = list.find { it.id == id }
        val removed = list.removeAll { it.id == id }
        if (removed) {
            existing?.let { deleteThumbnailFile(it) }
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

    /** Cached skill runs for [transcriptId], newest first (excludes system Catalog). */
    fun listCachedSkillRuns(transcriptId: String): List<CachedSkillRun> {
        if (transcriptId.isBlank()) return emptyList()
        val prefix = "${transcriptId}_"
        val files = resultsDir.listFiles()
            ?.filter { it.isFile && it.name.startsWith(prefix) && it.name.endsWith(".json") }
            .orEmpty()
        return files.mapNotNull { f ->
            val skillId = f.name.removePrefix(prefix).removeSuffix(".json")
            if (skillId.isBlank() || skillId == "builtin_catalog") return@mapNotNull null
            val skillName = try {
                JSONObject(f.readText()).optString("skillName").ifBlank { skillId }
            } catch (_: Exception) {
                skillId
            }
            CachedSkillRun(
                skillId = skillId,
                skillName = skillName,
                modifiedAtMillis = f.lastModified()
            )
        }.sortedByDescending { it.modifiedAtMillis }
    }

    private fun deleteThumbnailFile(entry: HistoryEntry) {
        if (entry.thumbnailPath.isNotBlank()) {
            File(entry.thumbnailPath).delete()
        }
        thumbnailFile(entry.id).delete()
    }

    private fun resultFile(transcriptId: String, skillId: String): File =
        File(resultsDir, "${transcriptId}_${skillId}.json")

    private fun loadEntries(): List<HistoryEntry> {
        cachedEntries?.let { return it }
        if (!file.exists()) {
            cachedEntries = emptyList()
            return emptyList()
        }
        val loaded: List<HistoryEntry> = try {
            val arr = JSONArray(file.readText())
            buildList<HistoryEntry> {
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
                            createdAt = o.optLong("createdAt", 0L),
                            title = o.optString("title"),
                            summary = o.optString("summary"),
                            thumbnailPath = o.optString("thumbnailPath"),
                            sourceUri = o.optString("sourceUri"),
                            mimeType = o.optString("mimeType"),
                            sourceFileSizeBytes = o.optLong("sourceFileSizeBytes", 0L),
                            uploadedAudioBytes = o.optLong("uploadedAudioBytes", 0L),
                            sourceDurationMs = o.optLong("sourceDurationMs", 0L),
                            persistablePermissionGranted = o.optBoolean(
                                "persistablePermissionGranted",
                                false
                            ),
                            jobState = enumOrDefault(
                                o.optString("jobState"),
                                if (o.optString("srtPath").isNotBlank()) {
                                    JobState.COMPLETED
                                } else {
                                    JobState.QUEUED
                                }
                            ),
                            jobStage = enumOrNull<JobStage>(o.optString("jobStage")),
                            percent = o.optInt("percent", 0),
                            chunksDone = o.optInt("chunksDone", 0),
                            chunksTotal = o.optInt("chunksTotal", 0),
                            stageMessage = o.optString("stageMessage"),
                            errorKind = enumOrNull<TranscribeErrorKind>(
                                o.optString("errorKind")
                            ),
                            errorScope = enumOrNull<ErrorScope>(o.optString("errorScope")),
                            errorMessage = o.optString("errorMessage"),
                            tempAudioPath = o.optString("tempAudioPath"),
                            queueOrder = o.optLong("queueOrder", o.optLong("createdAt", 0L))
                        )
                    )
                }
            }.filter { it.id.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
        cachedEntries = loaded
        return loaded
    }

    private fun persist(entries: List<HistoryEntry>) {
        cachedEntries = entries.toList()
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
                    .put("title", e.title)
                    .put("summary", e.summary)
                    .put("thumbnailPath", e.thumbnailPath)
                    .put("sourceUri", e.sourceUri)
                    .put("mimeType", e.mimeType)
                    .put("sourceFileSizeBytes", e.sourceFileSizeBytes)
                    .put("uploadedAudioBytes", e.uploadedAudioBytes)
                    .put("sourceDurationMs", e.sourceDurationMs)
                    .put("persistablePermissionGranted", e.persistablePermissionGranted)
                    .put("jobState", e.jobState.name)
                    .put("jobStage", e.jobStage?.name ?: "")
                    .put("percent", e.percent)
                    .put("chunksDone", e.chunksDone)
                    .put("chunksTotal", e.chunksTotal)
                    .put("stageMessage", e.stageMessage)
                    .put("errorKind", e.errorKind?.name ?: "")
                    .put("errorScope", e.errorScope?.name ?: "")
                    .put("errorMessage", e.errorMessage)
                    .put("tempAudioPath", e.tempAudioPath)
                    .put("queueOrder", e.queueOrder)
            )
        }
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(arr.toString(2))
        if (!temporary.renameTo(file)) {
            file.writeText(temporary.readText())
            temporary.delete()
        }
    }

    private inline fun <reified T : Enum<T>> enumOrNull(value: String): T? =
        value.takeIf { it.isNotBlank() }?.let {
            runCatching { enumValueOf<T>(it) }.getOrNull()
        }

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String, default: T): T =
        enumOrNull<T>(value) ?: default

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
