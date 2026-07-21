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
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.illyism.transcribe.domain.skills.SkillRunResult
import com.illyism.transcribe.ui.TranscribeViewModel
import com.illyism.transcribe.ui.nav.AppKey
import com.illyism.transcribe.ui.nav.Navigator
import com.illyism.transcribe.ui.nav.TOP_LEVEL_KEYS
import com.illyism.transcribe.ui.nav.rememberNavigationState
import com.illyism.transcribe.ui.nav.toEntries
import com.illyism.transcribe.ui.screens.DoneScreen
import com.illyism.transcribe.ui.screens.HistoryScreen
import com.illyism.transcribe.ui.screens.HomeScreen
import com.illyism.transcribe.ui.screens.ProcessingScreen
import com.illyism.transcribe.ui.screens.SelectedScreen
import com.illyism.transcribe.ui.screens.SettingsScreen
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

                val navState = rememberNavigationState(
                    startRoute = AppKey.Home,
                    topLevelRoutes = TOP_LEVEL_KEYS
                )
                val navigator = remember { Navigator(navState) }

                val picker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri != null) {
                        viewModel.onVideoPicked(uri)
                        if (navState.topLevelRoute != AppKey.Home) {
                            navigator.navigate(AppKey.Home)
                        }
                        // Avoid stacking Selected repeatedly.
                        if (navigator.currentKey() != AppKey.Selected) {
                            navigator.navigate(AppKey.Selected)
                        }
                    }
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

                // Pipeline finished → open TranscriptDetail(id), replacing Processing.
                LaunchedEffect(Unit) {
                    viewModel.finishedTranscriptId.collect { id ->
                        if (navState.topLevelRoute != AppKey.Home) {
                            navigator.navigate(AppKey.Home)
                        }
                        when (navigator.currentKey()) {
                            is AppKey.Processing,
                            is AppKey.Selected,
                            is AppKey.TranscriptDetail ->
                                navigator.replaceTop(AppKey.TranscriptDetail(id))
                            else -> navigator.navigate(AppKey.TranscriptDetail(id))
                        }
                    }
                }

                // Cold-start restore when an active job / selection exists.
                LaunchedEffect(Unit) {
                    viewModel.restoreNav.collect { restore ->
                        when (restore) {
                            TranscribeViewModel.RestoreNav.Selected -> {
                                if (navState.topLevelRoute != AppKey.Home) {
                                    navigator.navigate(AppKey.Home)
                                }
                                if (navigator.currentKey() !is AppKey.Selected &&
                                    navigator.currentKey() !is AppKey.Processing &&
                                    navigator.currentKey() !is AppKey.TranscriptDetail
                                ) {
                                    navigator.navigate(AppKey.Selected)
                                }
                            }
                            TranscribeViewModel.RestoreNav.Processing -> {
                                if (navState.topLevelRoute != AppKey.Home) {
                                    navigator.navigate(AppKey.Home)
                                }
                                when (navigator.currentKey()) {
                                    is AppKey.Processing -> Unit
                                    is AppKey.Selected ->
                                        navigator.replaceTop(AppKey.Processing)
                                    else -> navigator.navigate(AppKey.Processing)
                                }
                            }
                            is TranscribeViewModel.RestoreNav.Transcript -> {
                                if (navState.topLevelRoute != AppKey.Home) {
                                    navigator.navigate(AppKey.Home)
                                }
                                navigator.navigate(AppKey.TranscriptDetail(restore.id))
                            }
                        }
                    }
                }

                // Read SnapshotState fields so the bar recomposes with the stack.
                val topLevel = navState.topLevelRoute
                val currentKey = navState.backStacks[topLevel]?.lastOrNull()
                val showBottomBar = currentKey is AppKey.Home ||
                    currentKey is AppKey.History ||
                    currentKey is AppKey.Skills

                fun openSkillForTranscript(skillId: String) {
                    // Prefer the most recent history entry when opened from Skills tab.
                    val id = state.history.firstOrNull()?.id
                    if (id.isNullOrBlank()) {
                        viewModel.showMessage("Open a transcript from History first")
                        navigator.navigate(AppKey.History)
                        return
                    }
                    skillsViewModel.prepareRun(skillId)
                    navigator.navigate(AppKey.SkillRun(id, skillId))
                }

                @Suppress("UNCHECKED_CAST")
                val appEntryProvider =
                    entryProvider {
                    entry<AppKey.Home> {
                        HomeScreen(
                            onChooseVideo = {
                                picker.launch(arrayOf("video/*", "audio/*"))
                            },
                            onOpenSettings = { navigator.navigate(AppKey.Settings) }
                        )
                    }

                    entry<AppKey.History> {
                        HistoryScreen(
                            entries = state.history,
                            onOpen = { id ->
                                val entry = viewModel.transcript(id)
                                if (entry == null || !File(entry.srtPath).exists()) {
                                    viewModel.showMessage("Transcript file missing")
                                    return@HistoryScreen
                                }
                                navigator.navigate(AppKey.TranscriptDetail(id))
                            },
                            onDelete = viewModel::deleteHistoryEntry
                        )
                    }

                    entry<AppKey.Skills> {
                        SkillsScreen(
                            customSkills = skillsState.customSkills,
                            builtIns = skillsState.builtIns,
                            onNewSkill = {
                                skillsViewModel.startNewSkill()
                                navigator.navigate(AppKey.SkillEditor())
                            },
                            onOpenSkill = ::openSkillForTranscript,
                            onEdit = { id ->
                                skillsViewModel.editSkill(id)
                                navigator.navigate(AppKey.SkillEditor(id))
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
                    }

                    entry<AppKey.Selected> {
                        val selected = state.selected
                        if (selected == null) {
                            LaunchedEffect(Unit) { navigator.goBack() }
                        } else {
                            SelectedScreen(
                                name = selected.displayName,
                                sizeBytes = selected.sizeBytes,
                                durationMs = selected.durationMs,
                                hasApiKey = state.hasApiKey,
                                onStart = {
                                    if (viewModel.startTranscription()) {
                                        navigator.replaceTop(AppKey.Processing)
                                    }
                                },
                                onChooseDifferent = {
                                    picker.launch(arrayOf("video/*", "audio/*"))
                                },
                                onOpenSettings = { navigator.navigate(AppKey.Settings) }
                            )
                        }
                    }

                    entry<AppKey.Processing> {
                        BackHandler {
                            viewModel.cancelActiveJob()
                            navigator.goBack()
                        }
                        ProcessingScreen(
                            stage = state.stage,
                            percent = state.percent,
                            chunksDone = state.chunksDone,
                            chunksTotal = state.chunksTotal,
                            videoBytes = state.videoBytes,
                            audioBytes = state.audioBytes,
                            message = state.message,
                            error = state.error,
                            onRetry = {
                                if (viewModel.retryTranscription()) {
                                    // stay on Processing
                                }
                            },
                            onChooseDifferent = {
                                viewModel.chooseDifferent()
                                navigator.clearToRoot()
                            }
                        )
                    }

                    entry<AppKey.TranscriptDetail> { key ->
                        val entry = viewModel.transcript(key.transcriptId)
                        if (entry == null) {
                            LaunchedEffect(key.transcriptId) { navigator.goBack() }
                        } else {
                            DoneScreen(
                                srtPath = entry.srtPath,
                                preview = entry.preview,
                                language = entry.language.takeIf { it.isNotBlank() },
                                durationSeconds = entry.durationSeconds,
                                saveLocationLabel = viewModel.friendlySaveLocation(entry.srtPath),
                                onExport = { format ->
                                    viewModel.exportAndShare(key.transcriptId, format) { intent ->
                                        startActivity(Intent.createChooser(intent, "Share transcript"))
                                    }
                                },
                                onCopyText = viewModel::copyText,
                                onRename = { name ->
                                    viewModel.renameSrt(key.transcriptId, name)
                                },
                                onCreateSomething = {
                                    skillsViewModel.refresh()
                                    navigator.navigate(AppKey.SkillPicker(key.transcriptId))
                                },
                                onAnother = {
                                    viewModel.transcribeAnother()
                                    navigator.clearToRoot()
                                },
                                onBack = navigator::goBack
                            )
                        }
                    }

                    entry<AppKey.Settings> {
                        SettingsScreen(
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
                            onBack = {
                                viewModel.refreshSettings()
                                navigator.goBack()
                            }
                        )
                    }

                    entry<AppKey.SkillPicker> { key ->
                        val entry = viewModel.transcript(key.transcriptId)
                        SkillPickerScreen(
                            transcriptName = entry?.filename ?: "transcript",
                            customSkills = skillsState.customSkills,
                            builtIns = skillsState.builtIns,
                            onSelect = { skillId ->
                                skillsViewModel.prepareRun(skillId)
                                navigator.navigate(
                                    AppKey.SkillRun(key.transcriptId, skillId)
                                )
                            },
                            onBack = navigator::goBack
                        )
                    }

                    entry<AppKey.SkillEditor> { key ->
                        LaunchedEffect(key.skillId) {
                            if (key.skillId != null && skillsState.editing == null) {
                                skillsViewModel.editSkill(key.skillId)
                            } else if (key.skillId == null && skillsState.editing == null) {
                                skillsViewModel.startNewSkill()
                            }
                        }
                        val editing = skillsState.editing
                        if (editing == null) {
                            LaunchedEffect(Unit) { navigator.goBack() }
                        } else {
                            SkillEditorScreen(
                                skill = editing,
                                onChange = skillsViewModel::updateEditing,
                                onSave = {
                                    if (skillsViewModel.saveEditing()) {
                                        navigator.goBack()
                                    }
                                },
                                onBack = {
                                    skillsViewModel.cancelEditing()
                                    navigator.goBack()
                                }
                            )
                        }
                    }

                    entry<AppKey.SkillRun> { key ->
                        LaunchedEffect(key.skillId) {
                            if (skillsState.activeSkill?.id != key.skillId) {
                                skillsViewModel.prepareRun(key.skillId)
                            }
                        }
                        val skill = skillsState.activeSkill
                        val entry = viewModel.transcript(key.transcriptId)
                        if (skill == null || entry == null || !File(entry.srtPath).exists()) {
                            LaunchedEffect(Unit) { navigator.goBack() }
                        } else {
                            SkillRunScreen(
                                skill = skill,
                                transcriptName = entry.filename.ifBlank {
                                    File(entry.srtPath).name
                                },
                                selectedOutputIds = skillsState.selectedOutputIds,
                                customPrompt = skillsState.customPrompt,
                                error = skillsState.error,
                                skillModelTier = skillsState.activeTier,
                                onSkillModelTier = { tier ->
                                    skillsViewModel.setRunTier(tier)
                                    viewModel.setSkillModelTier(tier)
                                },
                                onToggleOutput = skillsViewModel::toggleOutput,
                                onCustomPrompt = skillsViewModel::setCustomPrompt,
                                onGenerate = {
                                    skillsViewModel.runSkill(
                                        transcriptId = key.transcriptId,
                                        filename = entry.filename.ifBlank {
                                            File(entry.srtPath).name
                                        },
                                        srtPath = entry.srtPath,
                                        language = entry.language,
                                        durationSeconds = entry.durationSeconds,
                                        apiKey = state.apiKey,
                                        onStarted = {
                                            navigator.navigate(
                                                AppKey.SkillResults(
                                                    key.transcriptId,
                                                    key.skillId
                                                )
                                            )
                                        }
                                    )
                                },
                                onBack = navigator::goBack
                            )
                        }
                    }

                    entry<AppKey.SkillResults> { key ->
                        BackHandler(enabled = skillsState.running) {
                            skillsViewModel.cancelRun()
                            navigator.goBack()
                        }
                        val activeSkill = skillsState.activeSkill
                        val cached = remember(key.transcriptId, key.skillId) {
                            (application as TranscribeApp).historyStore
                                .getCachedSkillResult(key.transcriptId, key.skillId)
                        }
                        val displayResult = skillsState.result
                            ?: activeSkill?.let { skill ->
                                SkillRunResult(
                                    skillId = skill.id,
                                    skillName = skill.name,
                                    outputs = skillsState.streamingOutputs,
                                    reasoning = skillsState.streamingReasoning
                                        .takeIf { it.isNotBlank() }
                                )
                            }
                            ?: cached

                        if (displayResult == null && !skillsState.running) {
                            LaunchedEffect(Unit) { navigator.goBack() }
                        } else if (displayResult != null) {
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
                                    navigator.goBack()
                                },
                                onBack = {
                                    if (skillsState.running) skillsViewModel.cancelRun()
                                    navigator.goBack()
                                },
                                onDone = {
                                    if (!navigator.popTo { it is AppKey.TranscriptDetail }) {
                                        navigator.clearToRoot()
                                        navigator.navigate(
                                            AppKey.TranscriptDetail(key.transcriptId)
                                        )
                                    }
                                }
                            )
                        }
                    }
                } as (androidx.navigation3.runtime.NavKey) -> androidx.navigation3.runtime.NavEntry<androidx.navigation3.runtime.NavKey>

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        if (showBottomBar) {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = navState.topLevelRoute == AppKey.Home,
                                    onClick = { navigator.navigate(AppKey.Home) },
                                    icon = { Icon(Icons.Outlined.Home, contentDescription = "Home") },
                                    label = { Text("Home") }
                                )
                                NavigationBarItem(
                                    selected = navState.topLevelRoute == AppKey.History,
                                    onClick = {
                                        viewModel.refreshHistory()
                                        navigator.navigate(AppKey.History)
                                    },
                                    icon = {
                                        Icon(Icons.Outlined.History, contentDescription = "History")
                                    },
                                    label = { Text("History") }
                                )
                                NavigationBarItem(
                                    selected = navState.topLevelRoute == AppKey.Skills,
                                    onClick = {
                                        skillsViewModel.refresh()
                                        navigator.navigate(AppKey.Skills)
                                    },
                                    icon = {
                                        Icon(
                                            Icons.Outlined.AutoAwesome,
                                            contentDescription = "Skills"
                                        )
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
                        NavDisplay(
                            entries = navState.toEntries(appEntryProvider),
                            onBack = {
                                when (val top = navigator.currentKey()) {
                                    is AppKey.Processing -> viewModel.cancelActiveJob()
                                    is AppKey.SkillResults -> {
                                        if (skillsState.running) skillsViewModel.cancelRun()
                                    }
                                    else -> Unit
                                }
                                navigator.goBack()
                            },
                            transitionSpec = {
                                slideInHorizontally(
                                    initialOffsetX = { it },
                                    animationSpec = tween(280)
                                ) togetherWith slideOutHorizontally(
                                    targetOffsetX = { -it / 4 },
                                    animationSpec = tween(280)
                                )
                            },
                            popTransitionSpec = {
                                slideInHorizontally(
                                    initialOffsetX = { -it / 4 },
                                    animationSpec = tween(280)
                                ) togetherWith slideOutHorizontally(
                                    targetOffsetX = { it },
                                    animationSpec = tween(280)
                                )
                            },
                            predictivePopTransitionSpec = {
                                slideInHorizontally(
                                    initialOffsetX = { -it / 4 },
                                    animationSpec = tween(280)
                                ) togetherWith slideOutHorizontally(
                                    targetOffsetX = { it },
                                    animationSpec = tween(280)
                                )
                            }
                        )
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
