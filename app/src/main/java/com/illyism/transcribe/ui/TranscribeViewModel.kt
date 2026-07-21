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
import com.illyism.transcribe.data.TranscribeSessionStore
import com.illyism.transcribe.domain.ExportFormat
import com.illyism.transcribe.domain.PipelineStage
import com.illyism.transcribe.domain.SrtBuilder
import com.illyism.transcribe.domain.UriMediaAccess
import com.illyism.transcribe.work.TranscribeWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class UiState(
    val route: AppRoute = AppRoute.Home,
    val selected: TranscribeSessionStore.SelectedVideo? = null,
    val hasApiKey: Boolean = false,
    val apiKey: String = "",
    val chunkMinutes: Int = 20,
    val maxParallel: Int = 4,
    val model: String = "whisper-1",
    val rawMode: Boolean = false,
    val stage: PipelineStage = PipelineStage.IDLE,
    val percent: Int = 0,
    val chunksDone: Int = 0,
    val chunksTotal: Int = 0,
    val videoBytes: Long = 0,
    val audioBytes: Long = 0,
    val message: String = "",
    val error: String? = null,
    val srtPath: String? = null,
    val preview: String? = null,
    val language: String? = null,
    val durationSeconds: Double = 0.0,
    val snackbar: String? = null
)

enum class AppRoute {
    Home, Selected, Processing, Done, Settings
}

class TranscribeViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as TranscribeApp
    private val workManager = WorkManager.getInstance(application)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refreshSettings()
        restoreSession()
        observeWork()
    }

    fun refreshSettings() {
        _state.update {
            it.copy(
                hasApiKey = app.settings.hasApiKey(),
                apiKey = app.settings.apiKey,
                chunkMinutes = app.settings.chunkMinutes,
                maxParallel = app.settings.maxParallelUploads,
                model = app.settings.model,
                rawMode = app.settings.rawMode
            )
        }
    }

    private fun restoreSession() {
        val selected = app.sessionStore.selectedVideo()
        val srt = app.sessionStore.resultSrtPath()
        val preview = app.sessionStore.resultPreview()
        val error = app.sessionStore.error()
        val progress = app.sessionStore.progress()

        when {
            srt != null -> {
                val srtBody = runCatching { File(srt).readText() }.getOrDefault(preview.orEmpty())
                _state.update {
                    it.copy(
                        route = AppRoute.Done,
                        selected = selected,
                        srtPath = srt,
                        preview = preview ?: SrtBuilder.preview(srtBody),
                        language = app.sessionStore.resultLanguage(),
                        durationSeconds = app.sessionStore.resultDurationSeconds()
                            .takeIf { d -> d > 0 }
                            ?: SrtBuilder.durationSeconds(srtBody),
                        stage = PipelineStage.DONE,
                        percent = 100
                    )
                }
            }
            progress != null &&
                progress.stage !in listOf(PipelineStage.IDLE.name, PipelineStage.DONE.name) &&
                selected != null -> {
                _state.update {
                    it.copy(
                        route = AppRoute.Processing,
                        selected = selected,
                        stage = runCatching { PipelineStage.valueOf(progress.stage) }.getOrDefault(PipelineStage.EXTRACTING),
                        percent = progress.overallPercent,
                        chunksDone = progress.chunksDone,
                        chunksTotal = progress.chunksTotal,
                        videoBytes = progress.videoBytes,
                        audioBytes = progress.audioBytes,
                        message = progress.message,
                        error = error
                    )
                }
            }
            progress != null && selected == null -> {
                // Orphaned progress / error with no video — clear and start fresh.
                app.sessionStore.clear()
            }
            selected != null -> {
                _state.update {
                    it.copy(route = AppRoute.Selected, selected = selected)
                }
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
                            val percent = info.progress.getInt(TranscribeWorker.KEY_PERCENT, _state.value.percent)
                            val stageName = info.progress.getString(TranscribeWorker.KEY_STAGE)
                            val stage = stageName?.let {
                                runCatching { PipelineStage.valueOf(it) }.getOrNull()
                            } ?: _state.value.stage
                            _state.update {
                                it.copy(
                                    route = AppRoute.Processing,
                                    stage = stage,
                                    percent = percent,
                                    chunksDone = info.progress.getInt(TranscribeWorker.KEY_CHUNKS_DONE, it.chunksDone),
                                    chunksTotal = info.progress.getInt(TranscribeWorker.KEY_CHUNKS_TOTAL, it.chunksTotal),
                                    videoBytes = info.progress.getLong(TranscribeWorker.KEY_VIDEO_BYTES, it.videoBytes),
                                    audioBytes = info.progress.getLong(TranscribeWorker.KEY_AUDIO_BYTES, it.audioBytes),
                                    message = info.progress.getString(TranscribeWorker.KEY_MESSAGE) ?: it.message,
                                    error = null
                                )
                            }
                        }
                        WorkInfo.State.SUCCEEDED -> {
                            val srt = info.outputData.getString(TranscribeWorker.KEY_SRT)
                                ?: app.sessionStore.resultSrtPath()
                            val preview = info.outputData.getString(TranscribeWorker.KEY_PREVIEW)
                                ?: app.sessionStore.resultPreview()
                            val language = info.outputData.getString(TranscribeWorker.KEY_LANGUAGE)
                                ?: app.sessionStore.resultLanguage()
                            val duration = info.outputData.getFloat(
                                TranscribeWorker.KEY_DURATION_SEC,
                                app.sessionStore.resultDurationSeconds().toFloat()
                            ).toDouble()
                            if (srt != null) {
                                _state.update {
                                    it.copy(
                                        route = AppRoute.Done,
                                        stage = PipelineStage.DONE,
                                        percent = 100,
                                        srtPath = srt,
                                        preview = preview,
                                        language = language,
                                        durationSeconds = duration,
                                        error = null
                                    )
                                }
                            }
                        }
                        WorkInfo.State.FAILED -> {
                            val selected = _state.value.selected ?: app.sessionStore.selectedVideo()
                            if (selected == null) {
                                // Stale failed WorkManager entry with nothing to retry —
                                // don't pin the UI on a dead error screen.
                                app.sessionStore.clear()
                                _state.update {
                                    it.copy(
                                        route = AppRoute.Home,
                                        selected = null,
                                        stage = PipelineStage.IDLE,
                                        percent = 0,
                                        error = null,
                                        message = "",
                                        srtPath = null,
                                        preview = null
                                    )
                                }
                                return@collect
                            }
                            val err = info.outputData.getString(TranscribeWorker.KEY_ERROR)
                                ?: app.sessionStore.error()
                                ?: "Transcription failed"
                            _state.update {
                                it.copy(
                                    route = AppRoute.Processing,
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

    fun onVideoPicked(uri: Uri) {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val meta = UriMediaAccess.readMeta(ctx, uri)
            app.sessionStore.saveSelected(uri, meta.displayName, meta.sizeBytes, meta.durationMs)
            _state.update {
                it.copy(
                    route = AppRoute.Selected,
                    selected = TranscribeSessionStore.SelectedVideo(
                        uri, meta.displayName, meta.sizeBytes, meta.durationMs
                    ),
                    error = null,
                    srtPath = null,
                    preview = null,
                    stage = PipelineStage.IDLE,
                    percent = 0
                )
            }
        }
    }

    fun startTranscription() {
        val selected = _state.value.selected ?: app.sessionStore.selectedVideo()
        if (selected == null) {
            _state.update {
                it.copy(
                    route = AppRoute.Home,
                    stage = PipelineStage.IDLE,
                    error = null,
                    snackbar = "Choose a video to transcribe"
                )
            }
            return
        }
        if (!app.settings.hasApiKey()) {
            _state.update { it.copy(snackbar = "Add an API key first") }
            return
        }
        // Drop stale failed progress so a restart doesn't restore the old error screen.
        app.sessionStore.clearProgressAndError()
        app.sessionStore.saveSelected(
            selected.uri,
            selected.displayName,
            selected.sizeBytes,
            selected.durationMs
        )
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
                route = AppRoute.Processing,
                selected = selected,
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
    }

    fun retryTranscription() = startTranscription()

    fun openSettings() = _state.update { it.copy(route = AppRoute.Settings) }

    fun backFromSettings() {
        refreshSettings()
        _state.update {
            it.copy(
                route = when {
                    it.srtPath != null -> AppRoute.Done
                    it.stage != PipelineStage.IDLE && it.stage != PipelineStage.DONE -> AppRoute.Processing
                    it.selected != null -> AppRoute.Selected
                    else -> AppRoute.Home
                }
            )
        }
    }

    fun chooseDifferent() {
        workManager.cancelUniqueWork(TranscribeWorker.UNIQUE_WORK)
        workManager.pruneWork()
        app.sessionStore.clear()
        _state.update {
            it.copy(
                route = AppRoute.Home,
                selected = null,
                stage = PipelineStage.IDLE,
                percent = 0,
                message = "",
                chunksDone = 0,
                chunksTotal = 0,
                srtPath = null,
                preview = null,
                error = null
            )
        }
    }

    fun transcribeAnother() = chooseDifferent()

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

    fun copyText(text: String) {
        if (text.isBlank()) return
        val clipboard = getApplication<Application>()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("transcript", text))
        _state.update { it.copy(snackbar = "Copied") }
    }

    fun shareSrt(): Intent? {
        val path = _state.value.srtPath ?: return null
        val file = File(path)
        if (!file.exists()) return null
        val uri = FileProvider.getUriForFile(
            getApplication(),
            "${getApplication<Application>().packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/x-subrip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun downloadExport(format: ExportFormat) {
        viewModelScope.launch {
            val path = _state.value.srtPath
            if (path == null) {
                showMessage("Nothing to download")
                return@launch
            }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val srtFile = File(path)
                    val srt = if (srtFile.exists()) srtFile.readText() else _state.value.preview.orEmpty()
                    val base = srtFile.nameWithoutExtension.ifBlank { "transcript" }
                    val body = when (format) {
                        ExportFormat.SRT -> srt
                        ExportFormat.TXT -> SrtBuilder.plainText(srt).ifBlank { srt }
                        ExportFormat.MD -> SrtBuilder.toMarkdown(srt, base)
                    }
                    val fileName = "$base.${format.extension}"
                    saveToDownloads(fileName, format.mimeType, body.toByteArray(Charsets.UTF_8))
                    fileName
                }
            }
            result.fold(
                onSuccess = { name ->
                    showMessage("Saved $name to Downloads/Transcribe")
                },
                onFailure = {
                    showMessage(it.message ?: "Download failed")
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

    fun renameSrt(newName: String) {
        val path = _state.value.srtPath ?: return
        val file = File(path)
        if (!file.exists()) {
            _state.update { it.copy(snackbar = "File not found") }
            return
        }
        val trimmed = newName.trim().removeSuffix(".srt").removeSuffix(".SRT")
        if (trimmed.isBlank()) {
            _state.update { it.copy(snackbar = "Enter a file name") }
            return
        }
        val target = File(file.parentFile, "$trimmed.srt")
        if (target.absolutePath == file.absolutePath) return
        if (target.exists()) {
            _state.update { it.copy(snackbar = "A file with that name already exists") }
            return
        }
        if (!file.renameTo(target)) {
            _state.update { it.copy(snackbar = "Could not rename file") }
            return
        }
        val preview = _state.value.preview.orEmpty()
        app.sessionStore.saveResult(
            target.absolutePath,
            preview,
            _state.value.language.orEmpty(),
            _state.value.durationSeconds
        )
        _state.update {
            it.copy(srtPath = target.absolutePath, snackbar = "Renamed")
        }
    }

    fun friendlySaveLocation(): String {
        val path = _state.value.srtPath ?: return "App files"
        return when {
            path.contains("/files/transcripts") -> "App files / transcripts"
            path.contains("/Download") || path.contains("/Downloads") -> "Downloads/Transcribe"
            else -> File(path).parentFile?.name ?: "App files"
        }
    }

    fun showMessage(message: String) {
        _state.update { it.copy(snackbar = message) }
    }

    fun consumeSnackbar() {
        _state.update { it.copy(snackbar = null) }
    }
}
