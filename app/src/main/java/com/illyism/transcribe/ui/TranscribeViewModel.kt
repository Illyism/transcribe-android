package com.illyism.transcribe.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import com.illyism.transcribe.domain.PipelineStage
import com.illyism.transcribe.domain.UriMediaAccess
import com.illyism.transcribe.work.TranscribeWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
                _state.update {
                    it.copy(
                        route = AppRoute.Done,
                        selected = selected,
                        srtPath = srt,
                        preview = preview,
                        stage = PipelineStage.DONE,
                        percent = 100
                    )
                }
            }
            progress != null && progress.stage !in listOf(PipelineStage.IDLE.name, PipelineStage.DONE.name) -> {
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
                    val info = infos.firstOrNull() ?: return@collect
                    when (info.state) {
                        WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED -> {
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
                            if (srt != null) {
                                _state.update {
                                    it.copy(
                                        route = AppRoute.Done,
                                        stage = PipelineStage.DONE,
                                        percent = 100,
                                        srtPath = srt,
                                        preview = preview,
                                        error = null
                                    )
                                }
                            }
                        }
                        WorkInfo.State.FAILED -> {
                            val err = info.outputData.getString(TranscribeWorker.KEY_ERROR)
                                ?: app.sessionStore.error()
                                ?: "Transcription failed"
                            _state.update {
                                it.copy(
                                    route = AppRoute.Processing,
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
        val selected = _state.value.selected ?: return
        if (!app.settings.hasApiKey()) {
            _state.update { it.copy(snackbar = "Add an API key first") }
            return
        }
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
        app.sessionStore.clear()
        _state.update {
            it.copy(
                route = AppRoute.Home,
                selected = null,
                stage = PipelineStage.IDLE,
                percent = 0,
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

    fun copyPreview() {
        val preview = _state.value.preview ?: return
        val clipboard = getApplication<Application>()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("transcript", preview))
        _state.update { it.copy(snackbar = "Preview copied") }
    }

    fun consumeSnackbar() {
        _state.update { it.copy(snackbar = null) }
    }
}
