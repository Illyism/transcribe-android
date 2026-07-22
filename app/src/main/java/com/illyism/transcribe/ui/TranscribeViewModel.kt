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
import com.illyism.transcribe.domain.CostEstimator
import com.illyism.transcribe.domain.JobState
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
    val pendingFiles: List<PendingFile> = emptyList(),
    val estimatedBatchCost: Double = 0.0,
    val totalUploadAvoidedBytes: Long = 0L,
    val totalPreparedAudioBytes: Long = 0L,
    val videosProcessedCount: Int = 0,
    val snackbar: String? = null
)

data class PendingFile(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val persistablePermissionGranted: Boolean
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
                skillModelTier = app.settings.skillModelTier,
                totalUploadAvoidedBytes = app.settings.totalUploadAvoidedBytes,
                totalPreparedAudioBytes = app.settings.totalPreparedAudioBytes,
                videosProcessedCount = app.settings.videosProcessedCount
            )
        }
    }

    fun refreshHistory() {
        _state.update {
            it.copy(
                history = app.historyStore.list()
            )
        }
    }

    private fun restoreSession() {
        app.historyStore.recoverInterruptedJobs()
        enqueueDispatcher()
    }

    private fun observeWork() {
        viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow(TranscribeWorker.UNIQUE_WORK)
                .collect {
                    refreshHistory()
                    refreshSettings()
                }
        }
    }

    fun prepareFiles(uris: List<Uri>) {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val existingUris = app.historyStore.list().map { it.sourceUri }.toSet()
            val pending = withContext(Dispatchers.IO) {
                uris.distinct().mapNotNull { uri ->
                    if (uri.toString() in existingUris) return@mapNotNull null
                    val permission = runCatching {
                        ctx.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }.isSuccess
                    val meta = UriMediaAccess.readMeta(ctx, uri)
                    PendingFile(
                        uri = uri,
                        displayName = meta.displayName,
                        mimeType = ctx.contentResolver.getType(uri).orEmpty(),
                        sizeBytes = meta.sizeBytes,
                        durationMs = meta.durationMs,
                        persistablePermissionGranted = permission
                    )
                }
            }
            if (pending.isEmpty()) {
                showMessage("Those files are already in Transcripts")
                return@launch
            }
            val estimate = CostEstimator.estimateAll(
                pending.map { it.durationMs },
                app.settings.whisperUsdPerMinute
            )
            _state.update {
                it.copy(pendingFiles = pending, estimatedBatchCost = estimate)
            }
            val needsConfirmation = pending.size > 1 ||
                pending.any { it.durationMs <= 0L } ||
                estimate >= 0.25 ||
                !app.settings.hasApiKey()
            if (!needsConfirmation) confirmPendingFiles()
        }
    }

    fun removePendingFile(uri: Uri) {
        _state.update { current ->
            val files = current.pendingFiles.filterNot { it.uri == uri }
            current.copy(
                pendingFiles = files,
                estimatedBatchCost = CostEstimator.estimateAll(
                    files.map { it.durationMs },
                    app.settings.whisperUsdPerMinute
                )
            )
        }
    }

    fun dismissPendingFiles() {
        _state.update { it.copy(pendingFiles = emptyList(), estimatedBatchCost = 0.0) }
    }

    fun confirmPendingFiles() {
        val files = _state.value.pendingFiles
        if (files.isEmpty()) return
        files.forEach { pending ->
            app.historyStore.createDraft(
                filename = pending.displayName,
                sourceUri = pending.uri.toString(),
                durationSeconds = pending.durationMs / 1000.0,
                mimeType = pending.mimeType,
                sourceFileSizeBytes = pending.sizeBytes,
                persistablePermissionGranted = pending.persistablePermissionGranted
            )
        }
        dismissPendingFiles()
        refreshHistory()
        enqueueDispatcher()
    }

    fun onVideoPicked(uri: Uri, onReady: (String) -> Unit = {}) {
        viewModelScope.launch {
            val before = app.historyStore.list().map { it.id }.toSet()
            prepareFiles(listOf(uri))
            // Compatibility path for incoming shares: confirm immediately after metadata settles.
            kotlinx.coroutines.delay(100)
            if (_state.value.pendingFiles.size == 1 && app.settings.hasApiKey()) {
                confirmPendingFiles()
            }
            app.historyStore.list().firstOrNull { it.id !in before }?.let { onReady(it.id) }
        }
    }

    private fun enqueueDispatcher() {
        val request = OneTimeWorkRequestBuilder<TranscribeWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        workManager.enqueueUniqueWork(
            TranscribeWorker.UNIQUE_WORK,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun cancelJob(id: String) {
        app.historyStore.requestCancel(id)
        refreshHistory()
    }

    fun retryJob(id: String) {
        app.historyStore.update(id) {
            it.copy(
                jobState = JobState.QUEUED,
                errorKind = null,
                errorScope = null,
                errorMessage = "",
                stageMessage = "Queued"
            )
        }
        refreshHistory()
        enqueueDispatcher()
    }

    fun removeJob(id: String) {
        app.historyStore.delete(id)
        refreshHistory()
    }

    fun locateSource(id: String, uri: Uri) {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val granted = runCatching {
                ctx.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }.isSuccess
            app.historyStore.update(id) {
                it.copy(
                    sourceUri = uri.toString(),
                    persistablePermissionGranted = granted,
                    jobState = JobState.QUEUED,
                    errorKind = null,
                    errorMessage = ""
                )
            }
            refreshHistory()
            enqueueDispatcher()
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
        enqueueDispatcher()

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
        app.historyStore.list()
            .filter {
                it.jobState == JobState.NEEDS_ATTENTION &&
                    it.errorScope == com.illyism.transcribe.domain.ErrorScope.QUEUE
            }
            .forEach { entry ->
                app.historyStore.update(entry.id) {
                    it.copy(
                        jobState = JobState.QUEUED,
                        errorKind = null,
                        errorScope = null,
                        errorMessage = "",
                        stageMessage = "Queued"
                    )
                }
            }
        enqueueDispatcher()
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
