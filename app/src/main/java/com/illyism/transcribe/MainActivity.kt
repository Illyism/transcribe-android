package com.illyism.transcribe

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import com.illyism.transcribe.domain.skills.BuiltInSkills
import com.illyism.transcribe.domain.skills.SkillRunResult
import com.illyism.transcribe.ui.TranscribeViewModel
import com.illyism.transcribe.ui.adaptive.HistoryDetailPlaceholder
import com.illyism.transcribe.ui.nav.AppKey
import com.illyism.transcribe.ui.nav.DeepLinks
import com.illyism.transcribe.ui.nav.Navigator
import com.illyism.transcribe.ui.nav.RequireOrBack
import com.illyism.transcribe.ui.nav.TOP_LEVEL_KEYS
import com.illyism.transcribe.ui.nav.rememberNavigationState
import com.illyism.transcribe.ui.nav.toEntries
import com.illyism.transcribe.ui.screens.DoneScreen
import com.illyism.transcribe.ui.screens.HistoryScreen
import com.illyism.transcribe.ui.screens.SettingsScreen
import com.illyism.transcribe.ui.skills.SkillEditorScreen
import com.illyism.transcribe.ui.skills.SkillResultsScreen
import com.illyism.transcribe.ui.skills.SkillsScreen
import com.illyism.transcribe.ui.skills.SkillsViewModel
import com.illyism.transcribe.ui.theme.TranscribeTheme
import java.io.File

class MainActivity : ComponentActivity() {
    private val viewModel: TranscribeViewModel by viewModels()
    private val skillsViewModel: SkillsViewModel by viewModels()
    /** Cold-start / onNewIntent payload (deep link or shared media). */
    private val pendingIncomingIntent = mutableStateOf<Intent?>(null)

    @OptIn(ExperimentalMaterial3AdaptiveApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        maybeRequestNotificationPermission()
        // Skip restore after rotation — intent is still the launch Intent.
        if (savedInstanceState == null) {
            pendingIncomingIntent.value = intent
        }

        setContent {
            TranscribeTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val skillsState by skillsViewModel.state.collectAsStateWithLifecycle()
                val snackbarHostState = remember { SnackbarHostState() }
                var pendingExportSkillId by remember { mutableStateOf<String?>(null) }

                val navState = rememberNavigationState(
                    startRoute = AppKey.History,
                    topLevelRoutes = TOP_LEVEL_KEYS
                )
                val navigator = remember { Navigator(navState) }

                val incomingIntent by pendingIncomingIntent
                LaunchedEffect(incomingIntent) {
                    val launchIntent = incomingIntent ?: return@LaunchedEffect
                    pendingIncomingIntent.value = null
                    handleIncomingIntent(launchIntent, navigator)
                }

                // History list-detail on medium/expanded width (Material Nav3 scene).
                val windowAdaptiveInfo = currentWindowAdaptiveInfo()
                val paneDirective = remember(windowAdaptiveInfo) {
                    calculatePaneScaffoldDirective(windowAdaptiveInfo)
                        .copy(horizontalPartitionSpacerSize = 0.dp)
                }
                val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>(
                    directive = paneDirective
                )
                val isWideWidth = windowAdaptiveInfo.windowSizeClass
                    .isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)

                fun share(intent: Intent, title: String) {
                    startActivity(Intent.createChooser(intent, title))
                }

                val picker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenMultipleDocuments()
                ) { uris ->
                    if (uris.isNotEmpty()) viewModel.prepareFiles(uris)
                }
                var locateJobId by remember { mutableStateOf<String?>(null) }
                val locatePicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    val id = locateJobId
                    locateJobId = null
                    if (id != null && uri != null) viewModel.locateSource(id, uri)
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

                LaunchedEffect(Unit) {
                    viewModel.restoreNav.collect { restore ->
                        when (restore) {
                            is TranscribeViewModel.RestoreNav.TranscriptDetail ->
                                navigator.openTranscriptDetail(restore.transcriptId)
                        }
                    }
                }

                val topLevel = navState.topLevelRoute

                fun runSkillOnTranscript(
                    transcriptId: String,
                    skillId: String,
                    prompt: String? = null
                ) {
                    val entry = viewModel.transcript(transcriptId) ?: return
                    skillsViewModel.quickRun(
                        skillId = skillId,
                        transcriptId = transcriptId,
                        filename = entry.filename.ifBlank { File(entry.srtPath).name },
                        srtPath = entry.srtPath,
                        language = entry.language,
                        durationSeconds = entry.durationSeconds,
                        apiKey = state.apiKey,
                        prompt = prompt,
                        onStarted = {
                            navigator.navigate(
                                AppKey.SkillResults(transcriptId, skillId)
                            )
                        }
                    )
                }

                @Suppress("UNCHECKED_CAST")
                val appEntryProvider =
                    entryProvider {
                    entry<AppKey.History>(
                        metadata = ListDetailSceneStrategy.listPane(
                            detailPlaceholder = { HistoryDetailPlaceholder() }
                        )
                    ) {
                        HistoryScreen(
                            entries = state.history,
                            pendingFiles = state.pendingFiles,
                            estimatedBatchCost = state.estimatedBatchCost,
                            maxParallel = state.maxParallel,
                            onAddFiles = {
                                picker.launch(arrayOf("video/*", "audio/*"))
                            },
                            onConfirmFiles = viewModel::confirmPendingFiles,
                            onDismissFiles = viewModel::dismissPendingFiles,
                            onRemovePending = { viewModel.removePendingFile(it.uri) },
                            onOpenSettings = { navigator.navigate(AppKey.Settings) },
                            onOpen = { id ->
                                val entry = viewModel.transcript(id)
                                if (entry == null || !File(entry.srtPath).exists()) {
                                    viewModel.showMessage("Transcript file missing")
                                    return@HistoryScreen
                                }
                                navigator.openHistoryDetail(id)
                            },
                            onCancel = viewModel::cancelJob,
                            onRetry = viewModel::retryJob,
                            onLocate = { id ->
                                locateJobId = id
                                locatePicker.launch(arrayOf("video/*", "audio/*"))
                            },
                            onDelete = viewModel::removeJob,
                            onRefresh = viewModel::refreshHistory
                        )
                    }

                    entry<AppKey.Skills> {
                        SkillsScreen(
                            customSkills = skillsState.customSkills,
                            builtIns = skillsState.builtIns,
                            onNewSkill = { navigator.navigate(AppKey.SkillEditor()) },
                            onOpenSkill = { id -> navigator.navigate(AppKey.SkillEditor(id)) },
                            onEdit = { id -> navigator.navigate(AppKey.SkillEditor(id)) },
                            onDuplicate = skillsViewModel::duplicate,
                            onDelete = skillsViewModel::delete,
                            onExport = { id ->
                                pendingExportSkillId = id
                                exportSkill.launch(skillsViewModel.exportFilename(id))
                            },
                            onImport = {
                                importSkill.launch(arrayOf("application/json", "text/*", "*/*"))
                            }
                        )
                    }

                    entry<AppKey.TranscriptDetail>(
                        metadata = ListDetailSceneStrategy.detailPane()
                    ) { key ->
                        // Hide back in History two-pane; keep it for Home / compact.
                        val showBack = !(isWideWidth && topLevel == AppKey.History)
                        val skillRuns = viewModel.listCachedSkillRuns(key.transcriptId)
                        RequireOrBack(
                            viewModel.transcript(key.transcriptId),
                            navigator::goBack
                        ) { entry ->
                            DoneScreen(
                                srtPath = entry.srtPath,
                                preview = entry.preview,
                                language = entry.language.takeIf { it.isNotBlank() },
                                durationSeconds = entry.durationSeconds,
                                title = entry.title,
                                summary = entry.summary,
                                sourceName = entry.filename,
                                thumbnailPath = entry.thumbnailPath,
                                sourceUri = entry.sourceUri,
                                quickSkills = skillsState.builtIns + skillsState.customSkills,
                                skillRuns = skillRuns,
                                phase = TranscribeViewModel.TranscriptDetailPhase.Complete,
                                sizeBytes = entry.sourceFileSizeBytes,
                                durationMs = entry.sourceDurationMs,
                                hasApiKey = state.hasApiKey,
                                stage = state.stage,
                                percent = state.percent,
                                chunksDone = state.chunksDone,
                                chunksTotal = state.chunksTotal,
                                videoBytes = state.videoBytes,
                                audioBytes = state.audioBytes,
                                message = state.message,
                                error = state.error,
                                onStart = { viewModel.startTranscription() },
                                onRetry = { viewModel.startTranscription() },
                                onChooseDifferent = {
                                    viewModel.chooseDifferent()
                                    picker.launch(arrayOf("video/*", "audio/*"))
                                },
                                onOpenSettings = { navigator.navigate(AppKey.Settings) },
                                onExport = { format ->
                                    viewModel.exportAndShare(key.transcriptId, format) { intent ->
                                        share(intent, "Share transcript")
                                    }
                                },
                                onCopyLink = {
                                    viewModel.copyText(
                                        DeepLinks.transcriptUri(key.transcriptId),
                                        snackbar = "Link copied"
                                    )
                                },
                                onCopyText = viewModel::copyText,
                                onEditTitle = { name ->
                                    viewModel.updateTitle(key.transcriptId, name)
                                },
                                onRunSkill = { skillId ->
                                    runSkillOnTranscript(key.transcriptId, skillId)
                                },
                                onAskAi = { prompt ->
                                    runSkillOnTranscript(
                                        key.transcriptId,
                                        BuiltInSkills.askAi.id,
                                        prompt = prompt
                                    )
                                },
                                onManageSkills = {
                                    skillsViewModel.refresh()
                                    navigator.navigate(AppKey.Skills)
                                },
                                onOpenSkillResult = { skillId ->
                                    skillsViewModel.loadCachedResult(key.transcriptId, skillId)
                                    navigator.navigate(
                                        AppKey.SkillResults(key.transcriptId, skillId)
                                    )
                                },
                                onLocate = {
                                    locateJobId = key.transcriptId
                                    locatePicker.launch(arrayOf("video/*", "audio/*"))
                                },
                                onDelete = {
                                    viewModel.removeJob(key.transcriptId)
                                    navigator.goBack()
                                },
                                onBack = {
                                    navigator.goBack()
                                },
                                showBack = showBack
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
                            totalUploadAvoidedBytes = state.totalUploadAvoidedBytes,
                            totalPreparedAudioBytes = state.totalPreparedAudioBytes,
                            videosProcessedCount = state.videosProcessedCount,
                            onManageSkills = {
                                skillsViewModel.refresh()
                                navigator.navigate(AppKey.Skills)
                            },
                            onBack = {
                                viewModel.refreshSettings()
                                navigator.goBack()
                            }
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
                        RequireOrBack(skillsState.editing, navigator::goBack) { editing ->
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

                    entry<AppKey.SkillResults> { key ->
                        LaunchedEffect(key.transcriptId, key.skillId) {
                            if (!skillsState.running ||
                                skillsState.activeSkill?.id != key.skillId
                            ) {
                                skillsViewModel.loadCachedResult(key.transcriptId, key.skillId)
                            }
                        }
                        val activeSkill = skillsState.activeSkill
                        val entry = viewModel.transcript(key.transcriptId)
                        val cached = remember(
                            key.transcriptId,
                            key.skillId,
                            skillsState.result?.skillId
                        ) {
                            skillsViewModel.cachedSkillResult(key.transcriptId, key.skillId)
                        }
                        val displayResult = when {
                            skillsState.result?.skillId == key.skillId -> skillsState.result
                            skillsState.running && activeSkill?.id == key.skillId ->
                                SkillRunResult(
                                    skillId = activeSkill.id,
                                    skillName = activeSkill.name,
                                    outputs = skillsState.streamingOutputs,
                                    reasoning = skillsState.streamingReasoning
                                        .takeIf { it.isNotBlank() }
                                )
                            else -> cached
                        }
                        // Ask AI: no output toggles; still allow model adjust + regenerate.
                        val sheetOutputs = if (key.skillId == BuiltInSkills.askAi.id) {
                            emptyList()
                        } else {
                            activeSkill?.takeIf { it.id == key.skillId }?.outputs.orEmpty()
                        }

                        RequireOrBack(
                            ok = displayResult != null ||
                                (skillsState.running && activeSkill?.id == key.skillId),
                            goBack = navigator::goBack
                        ) {
                            if (displayResult != null) {
                                SkillResultsScreen(
                                    result = displayResult,
                                    running = skillsState.running,
                                    error = skillsState.error,
                                    outputs = sheetOutputs,
                                    selectedOutputIds = skillsState.selectedOutputIds,
                                    onToggleOutput = skillsViewModel::toggleOutput,
                                    skillModelTier = skillsState.activeTier,
                                    onSkillModelTier = { tier ->
                                        skillsViewModel.setRunTier(tier)
                                        viewModel.setSkillModelTier(tier)
                                    },
                                    onRegenerate = entry?.let { e ->
                                        {
                                            skillsViewModel.regenerate(
                                                skillId = key.skillId,
                                                transcriptId = key.transcriptId,
                                                filename = e.filename.ifBlank {
                                                    File(e.srtPath).name
                                                },
                                                srtPath = e.srtPath,
                                                language = e.language,
                                                durationSeconds = e.durationSeconds,
                                                apiKey = state.apiKey
                                            )
                                        }
                                    },
                                    onCopy = skillsViewModel::copyOutput,
                                    onShare = { text, title ->
                                        skillsViewModel.shareText(text, title)?.let {
                                            share(it, title)
                                        }
                                    },
                                    onExportAll = {
                                        skillsViewModel.exportAllMarkdown()?.let {
                                            share(it, "Export")
                                        }
                                    },
                                    onShareAll = {
                                        skillsViewModel.shareAll()?.let {
                                            share(it, "Share all")
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
                    }
                } as (androidx.navigation3.runtime.NavKey) -> androidx.navigation3.runtime.NavEntry<androidx.navigation3.runtime.NavKey>

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    containerColor = MaterialTheme.colorScheme.background
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
                                    is AppKey.SkillResults -> {
                                        if (skillsState.running) skillsViewModel.cancelRun()
                                    }
                                    else -> Unit
                                }
                                navigator.goBack()
                            },
                            sceneStrategy = listDetailStrategy,
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingIncomingIntent.value = intent
    }

    private fun handleIncomingIntent(intent: Intent, navigator: Navigator) {
        when (val incoming = DeepLinks.parseIncoming(intent)) {
            is DeepLinks.Incoming.DeepLink -> handleDeepLink(incoming.target, navigator)
            is DeepLinks.Incoming.SharedMedia -> openSharedMedia(incoming.uri, navigator)
            null -> Unit
        }
    }

    private fun openSharedMedia(uri: Uri, navigator: Navigator) {
        viewModel.prepareFiles(listOf(uri))
        navigator.navigate(AppKey.History)
    }

    private fun handleDeepLink(target: DeepLinks.Target, navigator: Navigator) {
        when (target) {
            is DeepLinks.Target.Transcript -> {
                if (viewModel.transcript(target.transcriptId) == null) {
                    navigator.navigate(AppKey.History)
                    viewModel.showMessage("Transcript not found")
                    return
                }
                navigator.openFromDeepLink(target.transcriptId)
            }
            is DeepLinks.Target.SkillResult -> {
                if (viewModel.transcript(target.transcriptId) == null) {
                    navigator.navigate(AppKey.History)
                    viewModel.showMessage("Transcript not found")
                    return
                }
                if (!skillsViewModel.loadCachedResult(target.transcriptId, target.skillId)) {
                    navigator.openFromDeepLink(target.transcriptId)
                    viewModel.showMessage("Skill result not found")
                    return
                }
                navigator.openFromDeepLink(target.transcriptId, target.skillId)
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
