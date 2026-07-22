package com.illyism.transcribe.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.illyism.transcribe.TranscribeApp
import com.illyism.transcribe.data.CachedSkillRun
import com.illyism.transcribe.data.HistoryEntry
import com.illyism.transcribe.data.SkillModelTier
import com.illyism.transcribe.data.TranscribeSessionStore
import com.illyism.transcribe.domain.ExportFormat
import com.illyism.transcribe.domain.PipelineStage
import com.illyism.transcribe.domain.SrtBuilder
import com.illyism.transcribe.domain.UriMediaAccess
import com.illyism.transcribe.domain.VideoThumbnailExtractor
import com.illyism.transcribe.domain.skills.CatalogEnricher
import com.illyism.transcribe.domain.skills.SkillRunContext
import com.illyism.transcribe.domain.skills.SkillRunner
import com.illyism.transcribe.work.TranscribeWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Active-job + settings + history list. Navigation lives in Composition (Nav3);
 * finished transcripts are loaded by id from [com.illyism.transcribe.data.HistoryStore].
 */
data class UiState(
    val selected: TranscribeSessionStore.SelectedVideo? = null,
    val activeTranscriptId: String? = null,
    val hasApiKey: Boolean = false,
    val apiKey: String = "",
    val chunkMinutes: Int = 20,
    val maxParallel: Int = 4,
    val model: String = "whisper-1",
    val rawMode: Boolean = false,
    val skillModelTier: SkillModelTier = SkillModelTier.TERRA_LIGHT,
    val stage: PipelineStage = PipelineStage.IDLE,
    val percent: Int = 0,
    val chunksDone: Int = 0,
    val chunksTotal: Int = 0,
    val videoBytes: Long = 0,
    val audioBytes: Long = 0,
    val message: String = "",
    val error: String? = null,
    val history: List<HistoryEntry> = emptyList(),
    val snackbar: String? = null
)

class TranscribeViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as TranscribeApp
    private val workManager = WorkManager.getInstance(application)
    private val skillRunner = SkillRunner()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** One-shot restore hint when Nav3 has not yet restored a stack for the active job. */
    private val _restoreNav = MutableSharedFlow<RestoreNav>(extraBufferCapacity = 1)
    val restoreNav: SharedFlow<RestoreNav> = _restoreNav.asSharedFlow()

    sealed interface RestoreNav {
        data class TranscriptDetail(val transcriptId: String) : RestoreNav
    }

    enum class TranscriptDetailPhase {
        Ready, Working, Failed, Complete
    }

    init {
        refreshSettings()
        refreshHistory()
        restoreSession()
        observeWork()
    }

    fun transcript(id: String): HistoryEntry? = app.historyStore.get(id)

    fun isActiveJob(transcriptId: String): Boolean {
        val activeId = _state.value.activeTranscriptId ?: app.sessionStore.activeTranscriptId()
        return activeId == transcriptId && (
            _state.value.selected != null || app.sessionStore.selectedVideo() != null
            )
    }

    fun detailPhase(transcriptId: String): TranscriptDetailPhase {
        val entry = transcript(transcriptId) ?: return TranscriptDetailPhase.Complete
        if (!isActiveJob(transcriptId)) {
            return if (app.historyStore.isDraft(entry)) {
                TranscriptDetailPhase.Ready
            } else {
                TranscriptDetailPhase.Complete
            }
        }
        return when (_state.value.stage) {
            PipelineStage.IDLE -> TranscriptDetailPhase.Ready
            PipelineStage.FAILED -> TranscriptDetailPhase.Failed
            PipelineStage.DONE -> TranscriptDetailPhase.Complete
            else -> TranscriptDetailPhase.Working
        }
    }

    fun listCachedSkillRuns(transcriptId: String): List<CachedSkillRun> =
        app.historyStore.listCachedSkillRuns(transcriptId)

    fun refreshSettings() {
        _state.update {
            it.copy(
                hasApiKey = app.settings.hasApiKey(),
                apiKey = app.settings.apiKey,
                chunkMinutes = app.settings.chunkMinutes,
                maxParallel = app.settings.maxParallelUploads,
                model = app.settings.model,
                rawMode = app.settings.rawMode,
                skillModelTier = app.settings.skillModelTier
            )
        }
    }

    fun refreshHistory() {
        _state.update {
            it.copy(
                history = app.historyStore.list().filter { entry -> !app.historyStore.isDraft(entry) }
            )
        }
    }

    private fun restoreSession() {
        val selected = app.sessionStore.selectedVideo()
        val error = app.sessionStore.error()
        val progress = app.sessionStore.progress()
        val activeId = app.sessionStore.activeTranscriptId()

        when {
            progress != null &&
                progress.stage !in listOf(PipelineStage.IDLE.name, PipelineStage.DONE.name) &&
                selected != null -> {
                _state.update {
                    it.copy(
                        selected = selected,
                        activeTranscriptId = activeId,
                        stage = runCatching { PipelineStage.valueOf(progress.stage) }
                            .getOrDefault(PipelineStage.EXTRACTING),
                        percent = progress.overallPercent,
                        chunksDone = progress.chunksDone,
                        chunksTotal = progress.chunksTotal,
                        videoBytes = progress.videoBytes,
                        audioBytes = progress.audioBytes,
                        message = progress.message,
                        error = error
                    )
                }
                activeId?.let { _restoreNav.tryEmit(RestoreNav.TranscriptDetail(it)) }
            }
            progress != null && selected == null -> {
                app.sessionStore.clear()
            }
            selected != null -> {
                _state.update {
                    it.copy(
                        selected = selected,
                        activeTranscriptId = activeId,
                        error = error
                    )
                }
                activeId?.let { _restoreNav.tryEmit(RestoreNav.TranscriptDetail(it)) }
            }
        }
    }

    private fun observeWork() {
        viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow(TranscribeWorker.UNIQUE_WORK)
                .collect { infos ->
                    // Prefer active work — REPLACE can leave a stale FAILED entry that would
                    // immediately overwrite a Retry with the previous error.
                    val info = infos.firstOrNull {
                        it.state == WorkInfo.State.RUNNING ||
                            it.state == WorkInfo.State.ENQUEUED ||
                            it.state == WorkInfo.State.BLOCKED
                    } ?: infos.firstOrNull {
                        it.state == WorkInfo.State.SUCCEEDED
                    } ?: infos.firstOrNull {
                        it.state == WorkInfo.State.FAILED
                    } ?: return@collect

                    when (info.state) {
                        WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                            val percent = info.progress.getInt(
                                TranscribeWorker.KEY_PERCENT,
                                _state.value.percent
                            )
                            val stageName = info.progress.getString(TranscribeWorker.KEY_STAGE)
                            val stage = stageName?.let {
                                runCatching { PipelineStage.valueOf(it) }.getOrNull()
                            } ?: _state.value.stage
                            _state.update {
                                it.copy(
                                    stage = stage,
                                    percent = percent,
                                    chunksDone = info.progress.getInt(
                                        TranscribeWorker.KEY_CHUNKS_DONE,
                                        it.chunksDone
                                    ),
                                    chunksTotal = info.progress.getInt(
                                        TranscribeWorker.KEY_CHUNKS_TOTAL,
                                        it.chunksTotal
                                    ),
                                    videoBytes = info.progress.getLong(
                                        TranscribeWorker.KEY_VIDEO_BYTES,
                                        it.videoBytes
                                    ),
                                    audioBytes = info.progress.getLong(
                                        TranscribeWorker.KEY_AUDIO_BYTES,
                                        it.audioBytes
                                    ),
                                    message = info.progress.getString(TranscribeWorker.KEY_MESSAGE)
                                        ?: it.message,
                                    error = null
                                )
                            }
                        }
                        WorkInfo.State.SUCCEEDED -> {
                            val srt = info.outputData.getString(TranscribeWorker.KEY_SRT)
                            val preview = info.outputData.getString(TranscribeWorker.KEY_PREVIEW)
                            val language = info.outputData.getString(TranscribeWorker.KEY_LANGUAGE)
                            val duration = info.outputData.getFloat(
                                TranscribeWorker.KEY_DURATION_SEC,
                                0f
                            ).toDouble()
                            if (srt != null) {
                                val selected = _state.value.selected
                                    ?: app.sessionStore.selectedVideo()
                                val filename = selected?.displayName
                                    ?: File(srt).name
                                val activeId = _state.value.activeTranscriptId
                                    ?: app.sessionStore.activeTranscriptId()
                                val entry = finalizeHistory(
                                    transcriptId = activeId,
                                    filename = filename,
                                    srtPath = srt,
                                    preview = preview.orEmpty(),
                                    language = language.orEmpty(),
                                    durationSeconds = duration,
                                    sourceUri = selected?.uri?.toString().orEmpty()
                                )
                                app.sessionStore.clearProgressAndError()
                                app.sessionStore.clearActiveTranscriptId()
                                _state.update {
                                    it.copy(
                                        activeTranscriptId = null,
                                        stage = PipelineStage.DONE,
                                        percent = 100,
                                        error = null
                                    )
                                }
                                refreshHistory()
                                enrichHistoryEntry(entry, selected?.uri)
                            }
                        }
                        WorkInfo.State.FAILED -> {
                            val selected = _state.value.selected ?: app.sessionStore.selectedVideo()
                            if (selected == null) {
                                app.sessionStore.clear()
                                _state.update {
                                    it.copy(
                                        selected = null,
                                        stage = PipelineStage.IDLE,
                                        percent = 0,
                                        error = null,
                                        message = ""
                                    )
                                }
                                return@collect
                            }
                            val err = info.outputData.getString(TranscribeWorker.KEY_ERROR)
                                ?: app.sessionStore.error()
                                ?: "Transcription failed"
                            _state.update {
                                it.copy(
                                    selected = selected,
                                    stage = PipelineStage.FAILED,
                                    error = err,
                                    message = err
                                )
                            }
                        }
                        else -> Unit
                    }
                }
        }
    }

    fun onVideoPicked(uri: Uri, onReady: (String) -> Unit = {}) {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val meta = UriMediaAccess.readMeta(ctx, uri)
            clearActiveJobInternal(deleteDraft = true)

            val entry = app.historyStore.createDraft(
                filename = meta.displayName,
                sourceUri = uri.toString(),
                durationSeconds = meta.durationMs / 1000.0
            )
            val selected = TranscribeSessionStore.SelectedVideo(
                uri, meta.displayName, meta.sizeBytes, meta.durationMs
            )
            app.sessionStore.saveSelected(
                uri,
                meta.displayName,
                meta.sizeBytes,
                meta.durationMs
            )
            app.sessionStore.saveActiveTranscriptId(entry.id)
            _state.update {
                it.copy(
                    selected = selected,
                    activeTranscriptId = entry.id,
                    error = null,
                    stage = PipelineStage.IDLE,
                    percent = 0,
                    chunksDone = 0,
                    chunksTotal = 0,
                    videoBytes = meta.sizeBytes,
                    audioBytes = 0,
                    message = ""
                )
            }
            refreshHistory()
            onReady(entry.id)
            if (app.settings.hasApiKey()) {
                startTranscription()
            }
        }
    }

    fun startTranscription(): Boolean {
        val selected = _state.value.selected ?: app.sessionStore.selectedVideo()
        val activeId = _state.value.activeTranscriptId ?: app.sessionStore.activeTranscriptId()
        if (selected == null || activeId.isNullOrBlank()) {
            _state.update {
                it.copy(
                    stage = PipelineStage.IDLE,
                    error = null,
                    snackbar = "Choose a video to transcribe"
                )
            }
            return false
        }
        if (!app.settings.hasApiKey()) {
            _state.update { it.copy(snackbar = "Add an API key first") }
            return false
        }
        app.sessionStore.clearProgressAndError()
        app.sessionStore.saveSelected(
            selected.uri,
            selected.displayName,
            selected.sizeBytes,
            selected.durationMs
        )
        app.sessionStore.saveActiveTranscriptId(activeId)
        val request = OneTimeWorkRequestBuilder<TranscribeWorker>()
            .setInputData(
                workDataOf(
                    TranscribeWorker.KEY_URI to selected.uri.toString(),
                    TranscribeWorker.KEY_NAME to selected.displayName,
                    TranscribeWorker.KEY_SIZE to selected.sizeBytes
                )
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        workManager.enqueueUniqueWork(
            TranscribeWorker.UNIQUE_WORK,
            ExistingWorkPolicy.REPLACE,
            request
        )

        _state.update {
            it.copy(
                selected = selected,
                activeTranscriptId = activeId,
                stage = PipelineStage.EXTRACTING,
                percent = 1,
                message = "Starting…",
                error = null,
                videoBytes = selected.sizeBytes,
                audioBytes = 0,
                chunksDone = 0,
                chunksTotal = 0
            )
        }
        return true
    }

    /** Cancel in-flight work when leaving the active detail during transcription. */
    fun cancelActiveJob() {
        workManager.cancelUniqueWork(TranscribeWorker.UNIQUE_WORK)
        app.sessionStore.clearProgressAndError()
        _state.update {
            it.copy(
                stage = PipelineStage.IDLE,
                percent = 0,
                message = "",
                error = null
            )
        }
    }

    /** Remove draft + session when backing out before start or choosing a different file. */
    fun dismissActiveDraft() {
        clearActiveJobInternal(deleteDraft = true)
        refreshHistory()
    }

    private fun clearActiveJobInternal(deleteDraft: Boolean) {
        workManager.cancelUniqueWork(TranscribeWorker.UNIQUE_WORK)
        workManager.pruneWork()
        val activeId = _state.value.activeTranscriptId ?: app.sessionStore.activeTranscriptId()
        if (deleteDraft && !activeId.isNullOrBlank()) {
            app.historyStore.delete(activeId)
        }
        app.sessionStore.clear()
        _state.update {
            it.copy(
                selected = null,
                activeTranscriptId = null,
                stage = PipelineStage.IDLE,
                percent = 0,
                message = "",
                chunksDone = 0,
                chunksTotal = 0,
                error = null
            )
        }
    }

    fun deleteHistoryEntry(id: String) {
        app.historyStore.delete(id)
        refreshHistory()
        _state.update { it.copy(snackbar = "Removed from history") }
    }

    private fun finalizeHistory(
        transcriptId: String?,
        filename: String,
        srtPath: String,
        preview: String,
        language: String,
        durationSeconds: Double,
        sourceUri: String = ""
    ): HistoryEntry {
        val cleanName = filename.removeSuffix(".srt").removeSuffix(".SRT")
            .ifBlank { File(srtPath).nameWithoutExtension }
        if (!transcriptId.isNullOrBlank()) {
            val updated = app.historyStore.update(transcriptId) { draft ->
                draft.copy(
                    filename = cleanName,
                    srtPath = srtPath,
                    preview = preview.take(400),
                    language = language,
                    durationSeconds = durationSeconds,
                    sourceUri = sourceUri.ifBlank { draft.sourceUri }
                )
            }
            if (updated != null) return updated
        }
        return app.historyStore.append(
            filename = cleanName,
            srtPath = srtPath,
            preview = preview,
            language = language,
            durationSeconds = durationSeconds,
            sourceUri = sourceUri,
            id = transcriptId ?: UUID.randomUUID().toString()
        )
    }

    /**
     * Best-effort thumbnail + Catalog title/summary after DONE.
     * Failures leave filename + SRT preview as History fallbacks.
     */
    private fun enrichHistoryEntry(entry: HistoryEntry, videoUri: Uri?) {
        viewModelScope.launch {
            val thumbPath = withContext(Dispatchers.IO) {
                if (videoUri == null) return@withContext ""
                val dest = app.historyStore.thumbnailFile(entry.id)
                if (VideoThumbnailExtractor.extract(getApplication(), videoUri, dest)) {
                    dest.absolutePath
                } else {
                    ""
                }
            }
            if (thumbPath.isNotBlank()) {
                app.historyStore.update(entry.id) { it.copy(thumbnailPath = thumbPath) }
                refreshHistory()
            }

            val apiKey = app.settings.apiKey
            if (apiKey.isBlank() || !File(entry.srtPath).exists()) return@launch

            val catalog = withContext(Dispatchers.IO) {
                runCatching {
                    CatalogEnricher.run(
                        runner = skillRunner,
                        context = SkillRunContext(
                            transcriptId = entry.id,
                            filename = entry.filename,
                            srtPath = entry.srtPath,
                            language = entry.language,
                            durationSeconds = entry.durationSeconds
                        ),
                        apiKey = apiKey,
                        tier = SkillModelTier.TERRA_LIGHT
                    )
                }.getOrNull()
            } ?: return@launch

            val (title, summary) = catalog
            app.historyStore.update(entry.id) {
                it.copy(
                    title = title,
                    summary = summary,
                    thumbnailPath = it.thumbnailPath.ifBlank { thumbPath }
                )
            }
            refreshHistory()
        }
    }

    fun chooseDifferent() {
        dismissActiveDraft()
    }

    fun saveApiKey(value: String) {
        app.settings.apiKey = value
        refreshSettings()
        _state.update { it.copy(snackbar = "API key saved") }
    }

    fun clearApiKey() {
        app.settings.clearApiKey()
        refreshSettings()
    }

    fun setChunkMinutes(v: Int) {
        app.settings.chunkMinutes = v
        refreshSettings()
    }

    fun setMaxParallel(v: Int) {
        app.settings.maxParallelUploads = v
        refreshSettings()
    }

    fun setRawMode(v: Boolean) {
        app.settings.rawMode = v
        refreshSettings()
    }

    fun setSkillModelTier(tier: SkillModelTier) {
        app.settings.skillModelTier = tier
        refreshSettings()
    }

    fun copyText(text: String, snackbar: String = "Copied") {
        if (text.isBlank()) return
        val clipboard = getApplication<Application>()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("transcript", text))
        _state.update { it.copy(snackbar = snackbar) }
    }

    /**
     * Saves the transcript in [format] to Downloads/Transcribe, then builds a share
     * Intent from a FileProvider cache copy so the system share sheet opens next.
     */
    fun exportAndShare(transcriptId: String, format: ExportFormat, onReady: (Intent) -> Unit) {
        viewModelScope.launch {
            val entry = transcript(transcriptId)
            if (entry == null) {
                showMessage("Nothing to export")
                return@launch
            }
            val path = entry.srtPath
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val srtFile = File(path)
                    val srt = if (srtFile.exists()) srtFile.readText() else entry.preview
                    val base = srtFile.nameWithoutExtension.ifBlank { "transcript" }
                    val body = when (format) {
                        ExportFormat.SRT -> srt
                        ExportFormat.TXT -> SrtBuilder.plainText(srt).ifBlank { srt }
                        ExportFormat.MD -> SrtBuilder.toMarkdown(srt, base)
                    }
                    val bytes = body.toByteArray(Charsets.UTF_8)
                    val fileName = "$base.${format.extension}"
                    saveToDownloads(fileName, format.mimeType, bytes)

                    val cacheDir = File(getApplication<Application>().cacheDir, "exports").also {
                        it.mkdirs()
                    }
                    val cacheFile = File(cacheDir, fileName)
                    cacheFile.writeBytes(bytes)
                    val uri = FileProvider.getUriForFile(
                        getApplication(),
                        "${getApplication<Application>().packageName}.fileprovider",
                        cacheFile
                    )
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = format.mimeType
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, fileName)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    fileName to shareIntent
                }
            }
            result.fold(
                onSuccess = { (name, intent) ->
                    showMessage("Saved $name to Downloads/Transcribe")
                    onReady(intent)
                },
                onFailure = {
                    showMessage(it.message ?: "Export failed")
                }
            )
        }
    }

    private fun saveToDownloads(fileName: String, mimeType: String, bytes: ByteArray): Uri {
        val context = getApplication<Application>()
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Transcribe")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Could not create download file")
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: throw IllegalStateException("Could not write download file")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        }

        @Suppress("DEPRECATION")
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "Transcribe"
        )
        if (!dir.exists() && !dir.mkdirs()) {
            throw IllegalStateException("Could not create Downloads/Transcribe")
        }
        val outFile = File(dir, fileName)
        outFile.writeBytes(bytes)
        return Uri.fromFile(outFile)
    }

    fun updateTitle(transcriptId: String, newTitle: String) {
        val trimmed = newTitle.trim().take(120)
        if (trimmed.isBlank()) {
            _state.update { it.copy(snackbar = "Enter a title") }
            return
        }
        val updated = app.historyStore.update(transcriptId) { e ->
            e.copy(title = trimmed)
        }
        if (updated == null) {
            _state.update { it.copy(snackbar = "Transcript not found") }
            return
        }
        refreshHistory()
        _state.update { it.copy(snackbar = "Title updated") }
    }

    fun showMessage(message: String) {
        _state.update { it.copy(snackbar = message) }
    }

    fun consumeSnackbar() {
        _state.update { it.copy(snackbar = null) }
    }
}
