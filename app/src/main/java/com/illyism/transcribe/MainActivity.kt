package com.illyism.transcribe

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.illyism.transcribe.ui.AppRoute
import com.illyism.transcribe.ui.TranscribeViewModel
import com.illyism.transcribe.ui.screens.DoneScreen
import com.illyism.transcribe.ui.screens.HistoryScreen
import com.illyism.transcribe.ui.screens.HomeScreen
import com.illyism.transcribe.ui.screens.ProcessingScreen
import com.illyism.transcribe.ui.screens.SelectedScreen
import com.illyism.transcribe.ui.screens.SettingsScreen
import com.illyism.transcribe.domain.skills.SkillRunResult
import com.illyism.transcribe.ui.skills.SkillEditorScreen
import com.illyism.transcribe.ui.skills.SkillPickerScreen
import com.illyism.transcribe.ui.skills.SkillResultsScreen
import com.illyism.transcribe.ui.skills.SkillRunScreen
import com.illyism.transcribe.ui.skills.SkillsScreen
import com.illyism.transcribe.ui.skills.SkillsViewModel
import com.illyism.transcribe.ui.theme.TranscribeTheme
import java.io.File

class MainActivity : ComponentActivity() {
    private val viewModel: TranscribeViewModel by viewModels()
    private val skillsViewModel: SkillsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        maybeRequestNotificationPermission()

        setContent {
            TranscribeTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val skillsState by skillsViewModel.state.collectAsStateWithLifecycle()
                val snackbarHostState = remember { SnackbarHostState() }
                var pendingExportSkillId by remember { mutableStateOf<String?>(null) }

                val picker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri != null) viewModel.onVideoPicked(uri)
                }

                val importSkill = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri != null) skillsViewModel.importSkill(uri)
                }

                val exportSkill = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/json")
                ) { uri ->
                    val id = pendingExportSkillId
                    pendingExportSkillId = null
                    if (uri != null && id != null) skillsViewModel.exportSkill(id, uri)
                }

                LaunchedEffect(state.snackbar) {
                    val msg = state.snackbar ?: return@LaunchedEffect
                    snackbarHostState.showSnackbar(msg)
                    viewModel.consumeSnackbar()
                }
                LaunchedEffect(skillsState.snackbar) {
                    val msg = skillsState.snackbar ?: return@LaunchedEffect
                    snackbarHostState.showSnackbar(msg)
                    skillsViewModel.consumeSnackbar()
                }

                val showBottomBar = state.route in listOf(
                    AppRoute.Home,
                    AppRoute.History,
                    AppRoute.Skills
                )

                fun openSkillForTranscript(skillId: String) {
                    if (state.srtPath.isNullOrBlank()) {
                        viewModel.showMessage("Open a transcript from History first")
                        viewModel.navigateTab(AppRoute.History)
                        return
                    }
                    skillsViewModel.prepareRun(skillId)
                    viewModel.openSkillRun()
                }

                // System / gesture back pops our explicit stack (docs: custom back via BackHandler).
                BackHandler(enabled = state.canNavigateUp) {
                    if (state.route == AppRoute.SkillResults && skillsState.running) {
                        skillsViewModel.cancelRun()
                    }
                    viewModel.navigateUp()
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        if (showBottomBar) {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = state.route == AppRoute.Home,
                                    onClick = { viewModel.navigateTab(AppRoute.Home) },
                                    icon = { Icon(Icons.Outlined.Home, contentDescription = "Home") },
                                    label = { Text("Home") }
                                )
                                NavigationBarItem(
                                    selected = state.route == AppRoute.History,
                                    onClick = { viewModel.navigateTab(AppRoute.History) },
                                    icon = { Icon(Icons.Outlined.History, contentDescription = "History") },
                                    label = { Text("History") }
                                )
                                NavigationBarItem(
                                    selected = state.route == AppRoute.Skills,
                                    onClick = {
                                        skillsViewModel.refresh()
                                        viewModel.navigateTab(AppRoute.Skills)
                                    },
                                    icon = {
                                        Icon(Icons.Outlined.AutoAwesome, contentDescription = "Skills")
                                    },
                                    label = { Text("Skills") }
                                )
                            }
                        }
                    }
                ) { padding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) {
                        when (state.route) {
                            AppRoute.Home -> HomeScreen(
                                onChooseVideo = {
                                    picker.launch(arrayOf("video/*", "audio/*"))
                                },
                                onOpenSettings = viewModel::openSettings
                            )

                            AppRoute.History -> HistoryScreen(
                                entries = state.history,
                                onOpen = viewModel::openHistoryEntry,
                                onDelete = viewModel::deleteHistoryEntry
                            )

                            AppRoute.Skills -> SkillsScreen(
                                customSkills = skillsState.customSkills,
                                builtIns = skillsState.builtIns,
                                onNewSkill = {
                                    skillsViewModel.startNewSkill()
                                    viewModel.openSkillEditor()
                                },
                                onOpenSkill = ::openSkillForTranscript,
                                onEdit = { id ->
                                    skillsViewModel.editSkill(id)
                                    viewModel.openSkillEditor()
                                },
                                onDuplicate = skillsViewModel::duplicate,
                                onDelete = skillsViewModel::delete,
                                onExport = { id ->
                                    pendingExportSkillId = id
                                    val name = skillsViewModel.state.value.builtIns
                                        .plus(skillsViewModel.state.value.customSkills)
                                        .find { it.id == id }
                                        ?.name
                                        ?.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                                        ?: "skill"
                                    exportSkill.launch("$name.json")
                                },
                                onImport = {
                                    importSkill.launch(arrayOf("application/json", "text/*", "*/*"))
                                }
                            )

                            AppRoute.Selected -> {
                                val selected = state.selected
                                if (selected == null) {
                                    HomeScreen(
                                        onChooseVideo = {
                                            picker.launch(arrayOf("video/*", "audio/*"))
                                        },
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
                                onExport = { format ->
                                    viewModel.exportAndShare(format) { intent ->
                                        startActivity(Intent.createChooser(intent, "Share transcript"))
                                    }
                                },
                                onCopyText = viewModel::copyText,
                                onRename = viewModel::renameSrt,
                                onCreateSomething = {
                                    skillsViewModel.refresh()
                                    viewModel.openCreateSomething()
                                },
                                onAnother = viewModel::transcribeAnother,
                                onBack = viewModel::navigateUp
                            )

                            AppRoute.Settings -> SettingsScreen(
                                apiKey = state.apiKey,
                                chunkMinutes = state.chunkMinutes,
                                maxParallel = state.maxParallel,
                                model = state.model,
                                rawMode = state.rawMode,
                                skillModelTier = state.skillModelTier,
                                onSaveApiKey = viewModel::saveApiKey,
                                onClearApiKey = viewModel::clearApiKey,
                                onChunkMinutes = viewModel::setChunkMinutes,
                                onMaxParallel = viewModel::setMaxParallel,
                                onRawMode = viewModel::setRawMode,
                                onSkillModelTier = viewModel::setSkillModelTier,
                                onBack = viewModel::backFromSettings
                            )

                            AppRoute.SkillPicker -> SkillPickerScreen(
                                transcriptName = state.srtPath?.let { File(it).name }
                                    ?: "transcript",
                                customSkills = skillsState.customSkills,
                                builtIns = skillsState.builtIns,
                                onSelect = { id ->
                                    skillsViewModel.prepareRun(id)
                                    viewModel.openSkillRun()
                                },
                                onBack = viewModel::navigateUp
                            )

                            AppRoute.SkillEditor -> {
                                val editing = skillsState.editing
                                if (editing == null) {
                                    LaunchedEffect(Unit) {
                                        if (!viewModel.navigateUp()) {
                                            viewModel.navigateTab(AppRoute.Skills)
                                        }
                                    }
                                } else {
                                    SkillEditorScreen(
                                        skill = editing,
                                        onChange = skillsViewModel::updateEditing,
                                        onSave = {
                                            if (skillsViewModel.saveEditing()) {
                                                viewModel.navigateUp()
                                                if (viewModel.state.value.route != AppRoute.Skills) {
                                                    viewModel.navigateTab(AppRoute.Skills)
                                                }
                                            }
                                        },
                                        onBack = {
                                            skillsViewModel.cancelEditing()
                                            viewModel.navigateUp()
                                        }
                                    )
                                }
                            }

                            AppRoute.SkillRun -> {
                                val skill = skillsState.activeSkill
                                if (skill == null || state.srtPath.isNullOrBlank()) {
                                    LaunchedEffect(Unit) { viewModel.navigateUp() }
                                } else {
                                    SkillRunScreen(
                                        skill = skill,
                                        transcriptName = File(state.srtPath!!).name,
                                        selectedOutputIds = skillsState.selectedOutputIds,
                                        customPrompt = skillsState.customPrompt,
                                        error = skillsState.error,
                                        skillModelTier = skillsState.activeTier,
                                        onSkillModelTier = { tier ->
                                            skillsViewModel.setRunTier(tier)
                                            // Keep Settings / global UiState in sync with last-used.
                                            viewModel.setSkillModelTier(tier)
                                        },
                                        onToggleOutput = skillsViewModel::toggleOutput,
                                        onCustomPrompt = skillsViewModel::setCustomPrompt,
                                        onGenerate = {
                                            skillsViewModel.runSkill(
                                                transcriptId = state.historyId.orEmpty(),
                                                filename = File(state.srtPath!!).name,
                                                srtPath = state.srtPath!!,
                                                language = state.language.orEmpty(),
                                                durationSeconds = state.durationSeconds,
                                                apiKey = state.apiKey,
                                                // Land on the results screen immediately and
                                                // stream reasoning + output cards in there.
                                                onStarted = viewModel::openSkillResults
                                            )
                                        },
                                        onBack = viewModel::navigateUp
                                    )
                                }
                            }

                            AppRoute.SkillResults -> {
                                val activeSkill = skillsState.activeSkill
                                // Use the final result once available; otherwise render the
                                // in-flight stream (partial output cards + live reasoning).
                                val displayResult = skillsState.result ?: activeSkill?.let { skill ->
                                    SkillRunResult(
                                        skillId = skill.id,
                                        skillName = skill.name,
                                        outputs = skillsState.streamingOutputs,
                                        reasoning = skillsState.streamingReasoning
                                            .takeIf { it.isNotBlank() }
                                    )
                                }
                                if (displayResult == null) {
                                    LaunchedEffect(Unit) { viewModel.navigateUp() }
                                } else {
                                    SkillResultsScreen(
                                        result = displayResult,
                                        running = skillsState.running,
                                        error = skillsState.error,
                                        onCopy = skillsViewModel::copyOutput,
                                        onShare = { text, title ->
                                            skillsViewModel.shareText(text, title)?.let {
                                                startActivity(Intent.createChooser(it, title))
                                            }
                                        },
                                        onExportAll = {
                                            skillsViewModel.exportAllMarkdown()?.let {
                                                startActivity(Intent.createChooser(it, "Export"))
                                            }
                                        },
                                        onShareAll = {
                                            skillsViewModel.shareAll()?.let {
                                                startActivity(Intent.createChooser(it, "Share all"))
                                            }
                                        },
                                        onCancel = {
                                            skillsViewModel.cancelRun()
                                            viewModel.navigateUp()
                                        },
                                        onBack = {
                                            if (skillsState.running) skillsViewModel.cancelRun()
                                            viewModel.navigateUp()
                                        },
                                        onDone = viewModel::finishSkillResults
                                    )
                                }
                            }
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
