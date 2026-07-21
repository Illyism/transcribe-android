package com.illyism.transcribe

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.illyism.transcribe.ui.AppRoute
import com.illyism.transcribe.ui.TranscribeViewModel
import com.illyism.transcribe.ui.screens.DoneScreen
import com.illyism.transcribe.ui.screens.HomeScreen
import com.illyism.transcribe.ui.screens.ProcessingScreen
import com.illyism.transcribe.ui.screens.SelectedScreen
import com.illyism.transcribe.ui.screens.SettingsScreen
import com.illyism.transcribe.ui.theme.TranscribeTheme

class MainActivity : ComponentActivity() {
    private val viewModel: TranscribeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        maybeRequestNotificationPermission()

        setContent {
            TranscribeTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val snackbarHostState = remember { SnackbarHostState() }

                val picker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri != null) viewModel.onVideoPicked(uri)
                }

                LaunchedEffect(state.snackbar) {
                    val msg = state.snackbar ?: return@LaunchedEffect
                    snackbarHostState.showSnackbar(msg)
                    viewModel.consumeSnackbar()
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    containerColor = MaterialTheme.colorScheme.background
                ) { padding ->
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) {
                        when (state.route) {
                            AppRoute.Home -> HomeScreen(
                                onChooseVideo = {
                                    picker.launch(
                                        arrayOf(
                                            "video/*",
                                            "audio/*"
                                        )
                                    )
                                },
                                onOpenSettings = viewModel::openSettings
                            )

                            AppRoute.Selected -> {
                                val selected = state.selected
                                if (selected == null) {
                                    HomeScreen(
                                        onChooseVideo = { picker.launch(arrayOf("video/*", "audio/*")) },
                                        onOpenSettings = viewModel::openSettings
                                    )
                                } else {
                                    SelectedScreen(
                                        name = selected.displayName,
                                        sizeBytes = selected.sizeBytes,
                                        durationMs = selected.durationMs,
                                        hasApiKey = state.hasApiKey,
                                        onStart = viewModel::startTranscription,
                                        onChooseDifferent = {
                                            picker.launch(arrayOf("video/*", "audio/*"))
                                        },
                                        onOpenSettings = viewModel::openSettings
                                    )
                                }
                            }

                            AppRoute.Processing -> ProcessingScreen(
                                stage = state.stage,
                                percent = state.percent,
                                chunksDone = state.chunksDone,
                                chunksTotal = state.chunksTotal,
                                videoBytes = state.videoBytes,
                                audioBytes = state.audioBytes,
                                message = state.message,
                                error = state.error,
                                onRetry = viewModel::retryTranscription,
                                onChooseDifferent = viewModel::chooseDifferent
                            )

                            AppRoute.Done -> DoneScreen(
                                srtPath = state.srtPath.orEmpty(),
                                preview = state.preview.orEmpty(),
                                language = state.language,
                                durationSeconds = state.durationSeconds,
                                saveLocationLabel = viewModel.friendlySaveLocation(),
                                onDownload = viewModel::downloadExport,
                                onCopyText = viewModel::copyText,
                                onRename = viewModel::renameSrt,
                                onAnother = viewModel::transcribeAnother,
                                onBack = viewModel::transcribeAnother
                            )

                            AppRoute.Settings -> SettingsScreen(
                                apiKey = state.apiKey,
                                chunkMinutes = state.chunkMinutes,
                                maxParallel = state.maxParallel,
                                model = state.model,
                                rawMode = state.rawMode,
                                onSaveApiKey = viewModel::saveApiKey,
                                onClearApiKey = viewModel::clearApiKey,
                                onChunkMinutes = viewModel::setChunkMinutes,
                                onMaxParallel = viewModel::setMaxParallel,
                                onRawMode = viewModel::setRawMode,
                                onBack = viewModel::backFromSettings
                            )
                        }
                    }
                }
            }
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) return
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
    }
}
