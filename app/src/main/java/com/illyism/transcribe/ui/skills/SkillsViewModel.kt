package com.illyism.transcribe.ui.skills

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.illyism.transcribe.TranscribeApp
import com.illyism.transcribe.data.SkillModelTier
import com.illyism.transcribe.domain.ResponsesClient
import com.illyism.transcribe.domain.skills.BuiltInSkills
import com.illyism.transcribe.domain.skills.Skill
import com.illyism.transcribe.domain.skills.SkillOutputResult
import com.illyism.transcribe.domain.skills.SkillRunContext
import com.illyism.transcribe.domain.skills.SkillRunResult
import com.illyism.transcribe.domain.skills.SkillRunner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class SkillsUiState(
    val customSkills: List<Skill> = emptyList(),
    val builtIns: List<Skill> = BuiltInSkills.all,
    val editing: Skill? = null,
    val activeSkill: Skill? = null,
    val activeTier: SkillModelTier = SkillModelTier.TERRA_LIGHT,
    val selectedOutputIds: Set<String> = emptySet(),
    val customPrompt: String = "",
    val running: Boolean = false,
    /** Live reasoning summary while a skill is streaming. */
    val streamingReasoning: String = "",
    /** Partial output cards parsed from the in-flight JSON while streaming. */
    val streamingOutputs: List<SkillOutputResult> = emptyList(),
    val result: SkillRunResult? = null,
    val error: String? = null,
    val snackbar: String? = null
)

class SkillsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as TranscribeApp
    private val runner = SkillRunner()
    private var runJob: Job? = null

    private val _state = MutableStateFlow(SkillsUiState())
    val state: StateFlow<SkillsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.update {
            it.copy(
                customSkills = app.skillRepository.customSkills(),
                builtIns = app.skillRepository.builtIns()
            )
        }
    }

    fun startNewSkill() {
        _state.update { it.copy(editing = app.skillRepository.newBlank(), error = null) }
    }

    fun editSkill(id: String) {
        val skill = app.skillRepository.get(id) ?: return
        _state.update {
            it.copy(
                editing = if (skill.builtIn) {
                    skill.copy(
                        id = "",
                        name = "${skill.name} (custom)",
                        builtIn = false
                    )
                } else {
                    skill
                },
                error = null
            )
        }
    }

    fun updateEditing(transform: (Skill) -> Skill) {
        _state.update { state ->
            val current = state.editing ?: return@update state
            state.copy(editing = transform(current))
        }
    }

    fun saveEditing(): Boolean {
        val draft = _state.value.editing ?: return false
        if (draft.name.isBlank()) {
            _state.update { it.copy(snackbar = "Name is required") }
            return false
        }
        if (draft.prompt.isBlank()) {
            _state.update { it.copy(snackbar = "Prompt is required") }
            return false
        }
        if (draft.outputs.isEmpty()) {
            _state.update { it.copy(snackbar = "Add at least one output") }
            return false
        }
        app.skillRepository.saveCustom(draft)
        refresh()
        _state.update { it.copy(editing = null, snackbar = "Skill saved") }
        return true
    }

    fun cancelEditing() {
        _state.update { it.copy(editing = null) }
    }

    fun duplicate(id: String) {
        app.skillRepository.duplicate(id)
        refresh()
        _state.update { it.copy(snackbar = "Skill duplicated") }
    }

    fun delete(id: String) {
        if (app.skillRepository.delete(id)) {
            refresh()
            _state.update { it.copy(snackbar = "Skill deleted") }
        }
    }

    fun exportFilename(id: String): String {
        val name = app.skillRepository.get(id)?.name ?: "skill"
        return "${sanitizeFilename(name)}.json"
    }

    fun cachedSkillResult(transcriptId: String, skillId: String): SkillRunResult? =
        app.historyStore.getCachedSkillResult(transcriptId, skillId)

    fun exportSkill(id: String, uri: Uri) {
        val ok = app.skillRepository.exportSkill(id, uri)
        _state.update {
            it.copy(snackbar = if (ok) "Skill exported" else "Export failed")
        }
    }

    fun importSkill(uri: Uri) {
        val skill = app.skillRepository.importSkill(uri)
        refresh()
        _state.update {
            it.copy(snackbar = if (skill != null) "Imported ${skill.name}" else "Import failed")
        }
    }

    fun prepareRun(skillId: String) {
        val skill = app.skillRepository.get(skillId) ?: return
        val tier = skill.defaultTier?.let(SkillModelTier::fromStorage)
            ?: app.settings.skillModelTier
        _state.update {
            it.copy(
                activeSkill = skill,
                activeTier = tier,
                selectedOutputIds = skill.outputs.map { o -> o.id }.toSet(),
                customPrompt = "",
                result = null,
                error = null,
                running = false,
                streamingReasoning = "",
                streamingOutputs = emptyList()
            )
        }
    }

    /**
     * One-tap path: prepare defaults, optionally set Ask AI prompt, then run.
     * [onStarted] is skipped when the API key is missing (snackbar instead).
     */
    fun quickRun(
        skillId: String,
        transcriptId: String,
        filename: String,
        srtPath: String,
        language: String,
        durationSeconds: Double,
        apiKey: String,
        prompt: String? = null,
        onStarted: () -> Unit
    ) {
        if (apiKey.isBlank()) {
            _state.update { it.copy(snackbar = "Add an API key in Settings") }
            return
        }
        prepareRun(skillId)
        if (prompt != null) {
            setCustomPrompt(prompt)
        }
        runSkill(
            transcriptId = transcriptId,
            filename = filename,
            srtPath = srtPath,
            language = language,
            durationSeconds = durationSeconds,
            apiKey = apiKey,
            onStarted = onStarted
        )
    }

    /**
     * Re-run the active skill with the current tier/outputs, staying on results.
     * Prepares the skill first when reopening a cached result.
     */
    fun regenerate(
        skillId: String,
        transcriptId: String,
        filename: String,
        srtPath: String,
        language: String,
        durationSeconds: Double,
        apiKey: String
    ) {
        if (apiKey.isBlank()) {
            _state.update { it.copy(snackbar = "Add an API key in Settings") }
            return
        }
        if (_state.value.activeSkill?.id != skillId) {
            prepareRun(skillId)
        }
        if (_state.value.activeSkill?.id == BuiltInSkills.askAi.id &&
            _state.value.customPrompt.isBlank()
        ) {
            _state.update {
                it.copy(snackbar = "Ask a new question from the transcript")
            }
            return
        }
        runSkill(
            transcriptId = transcriptId,
            filename = filename,
            srtPath = srtPath,
            language = language,
            durationSeconds = durationSeconds,
            apiKey = apiKey,
            onStarted = {}
        )
    }

    /** Hydrate in-memory result from HistoryStore so reopen supports copy/export. */
    fun loadCachedResult(transcriptId: String, skillId: String): Boolean {
        val current = _state.value
        if (current.running && current.activeSkill?.id == skillId) return true
        if (current.result?.skillId == skillId) return true
        val cached = app.historyStore.getCachedSkillResult(transcriptId, skillId) ?: return false
        val skill = app.skillRepository.get(skillId)
        _state.update {
            it.copy(
                result = cached,
                activeSkill = skill ?: it.activeSkill,
                selectedOutputIds = skill?.outputs?.map { o -> o.id }?.toSet()
                    ?: it.selectedOutputIds,
                activeTier = skill?.defaultTier?.let(SkillModelTier::fromStorage)
                    ?: it.activeTier,
                running = false,
                error = null,
                streamingReasoning = "",
                streamingOutputs = emptyList()
            )
        }
        return true
    }

    fun setRunTier(tier: SkillModelTier) {
        app.settings.skillModelTier = tier
        _state.update { it.copy(activeTier = tier) }
    }

    fun toggleOutput(id: String) {
        _state.update { state ->
            val next = state.selectedOutputIds.toMutableSet()
            if (id in next) next.remove(id) else next.add(id)
            state.copy(selectedOutputIds = next)
        }
    }

    fun setCustomPrompt(value: String) {
        _state.update { it.copy(customPrompt = value) }
    }

    /**
     * Kicks off a streaming skill run. [onStarted] fires immediately (before any
     * network work) so the UI can navigate to the results screen and watch the
     * reasoning + output cards stream in progressively.
     */
    fun runSkill(
        transcriptId: String,
        filename: String,
        srtPath: String,
        language: String,
        durationSeconds: Double,
        apiKey: String,
        onStarted: () -> Unit
    ) {
        val skill = _state.value.activeSkill ?: return
        if (apiKey.isBlank()) {
            _state.update { it.copy(snackbar = "Add an API key in Settings") }
            return
        }
        if (!File(srtPath).exists()) {
            _state.update { it.copy(error = "Transcript file not found") }
            return
        }
        val selected = _state.value.selectedOutputIds
        if (skill.id != BuiltInSkills.askAi.id && selected.isEmpty()) {
            _state.update { it.copy(error = "Select at least one output") }
            return
        }
        if (skill.id == BuiltInSkills.askAi.id && _state.value.customPrompt.isBlank()) {
            _state.update { it.copy(error = "Enter a question") }
            return
        }

        val tier = _state.value.activeTier
        runJob?.cancel()
        runner.cancel()
        _state.update {
            it.copy(
                running = true,
                error = null,
                streamingReasoning = "",
                streamingOutputs = emptyList(),
                result = null
            )
        }
        onStarted()
        runJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    runner.run(
                        skill = skill,
                        context = SkillRunContext(
                            transcriptId = transcriptId,
                            filename = filename,
                            srtPath = srtPath,
                            language = language,
                            durationSeconds = durationSeconds,
                            customPrompt = _state.value.customPrompt
                        ),
                        apiKey = apiKey,
                        model = tier.modelId,
                        reasoningEffort = tier.reasoningEffort,
                        selectedOutputIds = selected.toList(),
                        onReasoningDelta = { delta ->
                            _state.update { state ->
                                state.copy(streamingReasoning = state.streamingReasoning + delta)
                            }
                        },
                        onPartial = { partial ->
                            _state.update { state ->
                                state.copy(streamingOutputs = partial)
                            }
                        }
                    )
                }
            }
            result.fold(
                onSuccess = { runResult ->
                    if (transcriptId.isNotBlank()) {
                        app.historyStore.cacheSkillResult(transcriptId, runResult)
                    }
                    _state.update {
                        it.copy(
                            running = false,
                            result = runResult,
                            error = null,
                            streamingReasoning = runResult.reasoning.orEmpty(),
                            streamingOutputs = emptyList()
                        )
                    }
                },
                onFailure = { e ->
                    if (e is ResponsesClient.SkillCancelledException || e is CancellationException) {
                        _state.update {
                            it.copy(running = false, error = null, streamingOutputs = emptyList())
                        }
                        return@fold
                    }
                    _state.update {
                        it.copy(
                            running = false,
                            error = e.message ?: "Skill failed",
                            streamingOutputs = emptyList()
                        )
                    }
                }
            )
        }
    }

    fun cancelRun() {
        runner.cancel()
        runJob?.cancel()
        runJob = null
        _state.update {
            it.copy(
                running = false,
                streamingReasoning = "",
                streamingOutputs = emptyList(),
                error = null
            )
        }
    }

    fun copyOutput(text: String) {
        if (text.isBlank()) return
        val clipboard = getApplication<Application>()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("skill_output", text))
        _state.update { it.copy(snackbar = "Copied") }
    }

    fun shareText(text: String, title: String = "Share"): Intent? {
        if (text.isBlank()) return null
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, title)
        }
    }

    fun shareAll(): Intent? {
        val result = _state.value.result ?: return null
        val body = result.outputs.joinToString("\n\n---\n\n") { out ->
            "# ${out.label}\n\n${out.content}"
        }
        return shareText(body, result.skillName)
    }

    fun exportAllMarkdown(): Intent? {
        val result = _state.value.result ?: return null
        val body = result.outputs.joinToString("\n\n") { out ->
            "## ${out.label}\n\n${out.content}"
        }
        val dir = File(getApplication<Application>().cacheDir, "skill_exports").also { it.mkdirs() }
        val file = File(dir, "${sanitizeFilename(result.skillName)}.md")
        file.writeText(body)
        val uri = FileProvider.getUriForFile(
            getApplication(),
            "${getApplication<Application>().packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/markdown"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, result.skillName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun consumeSnackbar() {
        _state.update { it.copy(snackbar = null) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private fun sanitizeFilename(name: String): String =
        name.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "skill" }
}
