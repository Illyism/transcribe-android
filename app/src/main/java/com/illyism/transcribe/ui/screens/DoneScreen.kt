package com.illyism.transcribe.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.ui.graphics.vector.ImageVector
import com.illyism.transcribe.data.CachedSkillRun
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import com.illyism.transcribe.domain.ExportFormat
import com.illyism.transcribe.domain.PipelineStage
import com.illyism.transcribe.domain.SrtBuilder
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.illyism.transcribe.domain.skills.BuiltInSkills
import com.illyism.transcribe.domain.skills.Skill
import com.illyism.transcribe.domain.skills.SkillIcons
import androidx.compose.runtime.Composable
import com.illyism.transcribe.ui.components.LocalThumbnail
import com.illyism.transcribe.ui.components.PrimaryButton
import com.illyism.transcribe.ui.components.ProgressBar
import com.illyism.transcribe.ui.components.SecondaryButton
import com.illyism.transcribe.ui.components.SourceVideoPlayer
import com.illyism.transcribe.ui.components.StepRow
import com.illyism.transcribe.ui.components.formatBytes
import com.illyism.transcribe.ui.components.formatDuration
import com.illyism.transcribe.ui.TranscribeViewModel.TranscriptDetailPhase
import com.illyism.transcribe.ui.components.InfoBanner
import java.io.File
import java.util.Locale

private enum class PreviewMode { Text, Srt }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DoneScreen(
    srtPath: String,
    preview: String,
    language: String?,
    durationSeconds: Double,
    title: String = "",
    summary: String = "",
    sourceName: String = "",
    thumbnailPath: String = "",
    sourceUri: String = "",
    quickSkills: List<Skill> = emptyList(),
    skillRuns: List<CachedSkillRun> = emptyList(),
    onExport: (ExportFormat) -> Unit,
    onCopyLink: () -> Unit,
    onCopyText: (String) -> Unit,
    onEditTitle: (String) -> Unit,
    onRunSkill: (String) -> Unit = {},
    onAskAi: (String) -> Unit = {},
    onManageSkills: () -> Unit = {},
    onOpenSkillResult: (String) -> Unit = {},
    onBack: () -> Unit,
    /** False in Files list-detail (tablet) where the list remains visible. */
    showBack: Boolean = true,
    phase: TranscriptDetailPhase = TranscriptDetailPhase.Complete,
    sizeBytes: Long = 0,
    durationMs: Long = 0,
    hasApiKey: Boolean = true,
    stage: PipelineStage = PipelineStage.DONE,
    percent: Int = 100,
    chunksDone: Int = 0,
    chunksTotal: Int = 0,
    videoBytes: Long = 0,
    audioBytes: Long = 0,
    message: String = "",
    error: String? = null,
    onStart: () -> Unit = {},
    onRetry: () -> Unit = {},
    onChooseDifferent: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val isComplete = phase == TranscriptDetailPhase.Complete
    val file = File(srtPath)
    val srtBody = remember(srtPath, preview) {
        if (!isComplete) preview else runCatching { file.readText() }.getOrDefault(preview)
    }
    val plain = remember(srtBody) { SrtBuilder.plainText(srtBody) }
    val duration = when {
        durationMs > 0 -> durationMs / 1000.0
        durationSeconds > 0 -> durationSeconds
        isComplete -> SrtBuilder.durationSeconds(srtBody)
        else -> 0.0
    }
    val langLabel = language
        ?.takeIf { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) }
        ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        ?: "Unknown"
    val previewText = srtBody.ifBlank { preview.ifBlank { "—" } }
    val headerTitle = when (phase) {
        TranscriptDetailPhase.Ready -> "Ready to transcribe"
        TranscriptDetailPhase.Working -> "Working…"
        TranscriptDetailPhase.Failed -> "Failed"
        TranscriptDetailPhase.Complete -> title.trim().ifBlank { "Transcript ready" }
    }
    val headerSubtitle = when (phase) {
        TranscriptDetailPhase.Ready -> "Start when you're ready — full video stays on your phone."
        TranscriptDetailPhase.Working -> message.ifBlank { "Preparing…" }
        TranscriptDetailPhase.Failed -> error.orEmpty()
        TranscriptDetailPhase.Complete -> summary.trim().ifBlank { "Your subtitles have been saved." }
    }
    val videoLabel = sourceName.ifBlank { file.nameWithoutExtension.ifBlank { "Video" } }
    val metaLine = when (phase) {
        TranscriptDetailPhase.Complete -> listOf(
            SrtBuilder.formatClock(duration),
            langLabel,
            formatBytes(if (file.exists()) file.length() else 0L)
        ).joinToString(" · ")
        else -> "${formatBytes(sizeBytes)} · ${formatDuration(durationMs)}"
    }
    val canPlay = sourceUri.isNotBlank()

    var showEditTitle by remember { mutableStateOf(false) }
    var showFull by remember { mutableStateOf(false) }
    var selectAllOnOpen by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var showAskAi by remember { mutableStateOf(false) }
    var showPlayer by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showBack) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                } else {
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Text(
                    "Transcribe",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                if (isComplete) {
                    IconButton(onClick = onCopyLink) {
                        Icon(Icons.Outlined.Link, contentDescription = "Copy link")
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 88.dp)
            ) {
                TranscriptMediaCard(
                    title = headerTitle,
                    filename = videoLabel,
                    meta = metaLine,
                    summary = if (isComplete && title.isNotBlank()) headerSubtitle else "",
                    thumbnailPath = thumbnailPath,
                    durationLabel = SrtBuilder.formatClock(duration),
                    playable = canPlay,
                    onPlay = { showPlayer = true },
                    onEditTitle = if (isComplete) ({ showEditTitle = true }) else null
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    headerSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (phase == TranscriptDetailPhase.Failed) {
                        scheme.error
                    } else {
                        scheme.onSurfaceVariant
                    }
                )

                when (phase) {
                    TranscriptDetailPhase.Ready -> {
                        ReadyPhaseContent(
                            hasApiKey = hasApiKey,
                            onStart = onStart,
                            onChooseDifferent = onChooseDifferent,
                            onOpenSettings = onOpenSettings
                        )
                    }
                    TranscriptDetailPhase.Working,
                    TranscriptDetailPhase.Failed -> {
                        JobProgressContent(
                            stage = stage,
                            percent = percent,
                            chunksDone = chunksDone,
                            chunksTotal = chunksTotal,
                            videoBytes = videoBytes,
                            audioBytes = audioBytes,
                            message = message,
                            error = error,
                            onRetry = onRetry,
                            onChooseDifferent = onChooseDifferent
                        )
                    }
                    TranscriptDetailPhase.Complete -> {
                        CompletePhaseContent(
                            quickSkills = quickSkills,
                            skillRuns = skillRuns,
                            previewText = previewText,
                            srtBody = srtBody,
                            onRunSkill = onRunSkill,
                            onAskAi = { showAskAi = true },
                            onManageSkills = onManageSkills,
                            onOpenSkillResult = onOpenSkillResult,
                            onCopyText = onCopyText,
                            onOpenPreview = {
                                selectAllOnOpen = false
                                showFull = true
                            },
                            onSelectAllPreview = {
                                selectAllOnOpen = true
                                showFull = true
                            }
                        )
                    }
                }
            }
        }

        if (isComplete) {
            FloatingActionButton(
                onClick = { showExport = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
                containerColor = scheme.primaryContainer,
                contentColor = scheme.onPrimaryContainer
            ) {
                Icon(Icons.Outlined.IosShare, contentDescription = "Export")
            }
        }
    }

    if (showPlayer) {
        VideoPlayerDialog(
            sourceUri = sourceUri,
            thumbnailPath = thumbnailPath,
            title = headerTitle,
            onDismiss = { showPlayer = false }
        )
    }

    if (showAskAi) {
        AskAiSheet(
            onDismiss = { showAskAi = false },
            onGenerate = { prompt ->
                showAskAi = false
                onAskAi(prompt)
            }
        )
    }

    if (showEditTitle) {
        EditTitleDialog(
            currentTitle = title.trim().ifBlank { videoLabel },
            onDismiss = { showEditTitle = false },
            onConfirm = {
                onEditTitle(it)
                showEditTitle = false
            }
        )
    }

    if (showExport) {
        ExportFormatSheet(
            onDismiss = { showExport = false },
            onCopyText = {
                showExport = false
                onCopyText(plain.ifBlank { srtBody })
            },
            onCopySubtitles = {
                showExport = false
                onCopyText(srtBody)
            },
            onSelect = { format ->
                showExport = false
                onExport(format)
            }
        )
    }

    if (showFull) {
        FullTranscriptSheet(
            srtBody = srtBody,
            plainText = plain,
            selectAllOnOpen = selectAllOnOpen,
            onDismiss = {
                showFull = false
                selectAllOnOpen = false
            },
            onCopy = onCopyText
        )
    }
}

@Composable
private fun ReadyPhaseContent(
    hasApiKey: Boolean,
    onStart: () -> Unit,
    onChooseDifferent: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Spacer(modifier = Modifier.height(20.dp))
    Text("What happens next", style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(12.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surfaceVariant)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        ReadyStepIcon(Icons.Outlined.GraphicEq, "Extract\nlocally")
        ReadyStepIcon(Icons.Outlined.Tune, "Optimize\nchunks")
        ReadyStepIcon(Icons.Outlined.Cloud, "Transcribe\nin parallel")
    }
    Spacer(modifier = Modifier.height(16.dp))
    InfoBanner(
        text = "Full video stays on your phone. Only optimized audio chunks are uploaded.",
        icon = Icons.Outlined.Shield,
        tint = scheme.primary
    )
    if (!hasApiKey) {
        Spacer(modifier = Modifier.height(12.dp))
        InfoBanner(
            text = "API key required before starting. Add it in Settings.",
            icon = Icons.Outlined.WarningAmber
        )
    }
    Spacer(modifier = Modifier.height(28.dp))
    PrimaryButton(
        text = "Start transcription",
        onClick = onStart,
        enabled = hasApiKey
    )
    Spacer(modifier = Modifier.height(10.dp))
    SecondaryButton("Choose different video", onClick = onChooseDifferent)
    if (!hasApiKey) {
        Spacer(modifier = Modifier.height(8.dp))
        SecondaryButton("Add API key", onClick = onOpenSettings)
    }
}

@Composable
private fun ReadyStepIcon(icon: ImageVector, label: String) {
    val scheme = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = scheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant
        )
    }
}

@Composable
private fun JobProgressContent(
    stage: PipelineStage,
    percent: Int,
    chunksDone: Int,
    chunksTotal: Int,
    videoBytes: Long,
    audioBytes: Long,
    message: String,
    error: String?,
    onRetry: () -> Unit,
    onChooseDifferent: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val failed = stage == PipelineStage.FAILED || error != null
    val progressStage = if (stage == PipelineStage.FAILED) PipelineStage.EXTRACTING else stage

    Spacer(modifier = Modifier.height(20.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surfaceVariant)
            .padding(16.dp)
    ) {
        val extractDone = progressStage.ordinal > PipelineStage.EXTRACTING.ordinal ||
            progressStage == PipelineStage.DONE
        val optimizeDone = progressStage.ordinal > PipelineStage.OPTIMIZING.ordinal ||
            progressStage == PipelineStage.DONE
        val transcribingActive = progressStage == PipelineStage.TRANSCRIBING ||
            progressStage == PipelineStage.CHUNKING
        val transcribeDone = progressStage == PipelineStage.SAVING ||
            progressStage == PipelineStage.DONE

        StepRow(
            title = "Extracting audio",
            subtitle = when {
                failed && !extractDone -> "Failed"
                extractDone -> "Completed"
                else -> "In progress"
            },
            done = extractDone && progressStage != PipelineStage.EXTRACTING,
            active = !failed && progressStage == PipelineStage.EXTRACTING
        )
        Spacer(modifier = Modifier.height(16.dp))
        StepRow(
            title = "Optimizing",
            subtitle = when {
                failed && progressStage.ordinal < PipelineStage.OPTIMIZING.ordinal -> "Waiting"
                failed && progressStage == PipelineStage.OPTIMIZING -> "Failed"
                progressStage.ordinal < PipelineStage.OPTIMIZING.ordinal -> "Waiting"
                optimizeDone && progressStage != PipelineStage.OPTIMIZING -> "Completed"
                else -> "In progress"
            },
            done = optimizeDone && progressStage != PipelineStage.OPTIMIZING,
            active = !failed && progressStage == PipelineStage.OPTIMIZING
        )
        Spacer(modifier = Modifier.height(16.dp))
        val chunkLabel = if (chunksTotal > 0) {
            "Transcribing chunks ($chunksDone/$chunksTotal)"
        } else {
            "Transcribing chunks"
        }
        StepRow(
            title = chunkLabel,
            subtitle = when {
                failed && (transcribingActive || progressStage.ordinal >= PipelineStage.CHUNKING.ordinal) ->
                    "Failed"
                failed -> "Waiting"
                transcribeDone -> "Completed"
                transcribingActive -> "In progress"
                else -> "Waiting"
            },
            done = transcribeDone,
            active = !failed && transcribingActive
        )
    }

    if (videoBytes > 0 && audioBytes > 0) {
        Spacer(modifier = Modifier.height(16.dp))
        val reduction = ((1.0 - audioBytes.toDouble() / videoBytes.toDouble()) * 100).coerceIn(0.0, 99.9)
        InfoBanner(
            text = "${formatBytes(videoBytes)} video → ${formatBytes(audioBytes)} audio " +
                "(${String.format(Locale.getDefault(), "%.1f", reduction)}% smaller before upload).",
            tint = scheme.primary
        )
    }

    Spacer(modifier = Modifier.height(24.dp))
    if (failed) {
        PrimaryButton("Retry", onClick = onRetry)
        Spacer(modifier = Modifier.height(12.dp))
        SecondaryButton("Choose different video", onClick = onChooseDifferent)
    } else {
        Text("Overall progress", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(10.dp))
        ProgressBar(percent = percent)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            message.ifBlank { "You can leave the app. We'll keep working in the background." },
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompletePhaseContent(
    quickSkills: List<Skill>,
    skillRuns: List<CachedSkillRun>,
    previewText: String,
    srtBody: String,
    onRunSkill: (String) -> Unit,
    onAskAi: () -> Unit,
    onManageSkills: () -> Unit,
    onOpenSkillResult: (String) -> Unit,
    onCopyText: (String) -> Unit,
    onOpenPreview: () -> Unit,
    onSelectAllPreview: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    if (quickSkills.isNotEmpty()) {
        Spacer(modifier = Modifier.height(20.dp))
        Text("Create something", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            quickSkills.forEach { skill ->
                val iconColor = SkillIcons.color(skill.color)
                AssistChip(
                    onClick = {
                        if (skill.id == BuiltInSkills.askAi.id) {
                            onAskAi()
                        } else {
                            onRunSkill(skill.id)
                        }
                    },
                    label = { Text(skill.name) },
                    leadingIcon = {
                        Icon(
                            SkillIcons.vector(skill.icon),
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(AssistChipDefaults.IconSize)
                        )
                    }
                )
            }
            AssistChip(
                onClick = onManageSkills,
                label = { Text("Manage skills") },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(AssistChipDefaults.IconSize)
                    )
                }
            )
        }
    }

    Spacer(modifier = Modifier.height(20.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Preview",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = { onCopyText(srtBody) }) {
            Icon(
                Icons.Outlined.ContentCopy,
                contentDescription = "Copy SRT",
                tint = scheme.primary
            )
        }
        IconButton(onClick = onSelectAllPreview) {
            Icon(
                Icons.Outlined.SelectAll,
                contentDescription = "Select all",
                tint = scheme.primary
            )
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp, max = 320.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surfaceVariant)
            .clickable(onClick = onOpenPreview)
            .padding(16.dp)
    ) {
        Text(
            text = previewText,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurface,
            maxLines = 18,
            overflow = TextOverflow.Ellipsis
        )
    }

    if (skillRuns.isNotEmpty()) {
        Spacer(modifier = Modifier.height(20.dp))
        Text("Creations", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(scheme.surfaceVariant),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            skillRuns.forEachIndexed { index, run ->
                SkillRunRow(
                    run = run,
                    showDivider = index < skillRuns.lastIndex,
                    onClick = { onOpenSkillResult(run.skillId) }
                )
            }
        }
    }
}

@Composable
private fun TranscriptMediaCard(
    title: String,
    filename: String,
    meta: String,
    summary: String,
    thumbnailPath: String,
    durationLabel: String,
    playable: Boolean,
    onPlay: () -> Unit,
    onEditTitle: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surfaceVariant)
            .then(
                if (playable) {
                    Modifier.clickable(onClick = onPlay)
                } else {
                    Modifier
                }
            )
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .width(112.dp)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
        ) {
            LocalThumbnail(
                path = thumbnailPath,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 12.dp
            )
            if (playable) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.Black.copy(alpha = 0.72f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        Icons.Outlined.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        durationLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                if (onEditTitle != null) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(scheme.surface)
                            .clickable(onClick = onEditTitle),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = "Edit title",
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                filename,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                meta,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (summary.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun VideoPlayerDialog(
    sourceUri: String,
    thumbnailPath: String,
    title: String,
    onDismiss: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(20.dp),
            color = scheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close")
                    }
                }
                SourceVideoPlayer(
                    sourceUri = sourceUri,
                    thumbnailPath = thumbnailPath,
                    autoPlay = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .padding(bottom = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun SkillRunRow(
    run: CachedSkillRun,
    showDivider: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                "${run.skillName} · ${formatRelativeTime(run.modifiedAtMillis)}",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = scheme.onSurfaceVariant
            )
        }
        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 46.dp)
                    .height(1.dp)
                    .background(scheme.outlineVariant.copy(alpha = 0.4f))
            )
        }
    }
}

private fun formatRelativeTime(millis: Long, now: Long = System.currentTimeMillis()): String {
    val diff = (now - millis).coerceAtLeast(0L)
    val minutes = diff / 60_000L
    val hours = diff / 3_600_000L
    val days = diff / 86_400_000L
    return when {
        minutes < 1L -> "just now"
        minutes < 60L -> "${minutes}m ago"
        hours < 24L -> "${hours}h ago"
        days < 30L -> "${days}d ago"
        else -> java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
            .format(java.util.Date(millis))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportFormatSheet(
    onDismiss: () -> Unit,
    onCopyText: () -> Unit,
    onCopySubtitles: () -> Unit,
    onSelect: (ExportFormat) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = scheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                "Export",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Text(
                "Copy",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = scheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ExportGridTile(
                    label = "Text",
                    icon = Icons.Outlined.ContentCopy,
                    onClick = onCopyText,
                    modifier = Modifier.weight(1f)
                )
                ExportGridTile(
                    label = "Subtitles",
                    icon = Icons.Outlined.Subtitles,
                    onClick = onCopySubtitles,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                "Save & share",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = scheme.onSurfaceVariant
            )
            Text(
                "Downloads/Transcribe, then Share",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ExportGridTile(
                    label = "TXT",
                    icon = Icons.Outlined.Description,
                    onClick = { onSelect(ExportFormat.TXT) },
                    modifier = Modifier.weight(1f)
                )
                ExportGridTile(
                    label = "MD",
                    icon = Icons.Outlined.Code,
                    onClick = { onSelect(ExportFormat.MD) },
                    modifier = Modifier.weight(1f)
                )
                ExportGridTile(
                    label = "SRT",
                    icon = Icons.Outlined.ClosedCaption,
                    onClick = { onSelect(ExportFormat.SRT) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ExportGridTile(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(vertical = 18.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = scheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PreviewToggle(mode: PreviewMode, onModeChange: (PreviewMode) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(scheme.surfaceVariant)
            .padding(3.dp)
    ) {
        PreviewToggleChip("Text", mode == PreviewMode.Text) { onModeChange(PreviewMode.Text) }
        PreviewToggleChip("SRT", mode == PreviewMode.Srt) { onModeChange(PreviewMode.Srt) }
    }
}

@Composable
private fun PreviewToggleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) scheme.primary else scheme.surfaceVariant.copy(alpha = 0f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AskAiSheet(
    onDismiss: () -> Unit,
    onGenerate: (String) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    var prompt by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
        ) {
            Text(
                "Ask AI",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Ask a question about this transcript.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .focusRequester(focusRequester),
                placeholder = { Text("What were the main decisions?") }
            )
            Spacer(modifier = Modifier.height(16.dp))
            PrimaryButton(
                text = "Generate",
                onClick = { onGenerate(prompt.trim()) },
                enabled = prompt.isNotBlank(),
                icon = Icons.Outlined.AutoAwesome
            )
        }
    }
}

@Composable
private fun EditTitleDialog(
    currentTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    var draft by remember {
        mutableStateOf(
            TextFieldValue(
                text = currentTitle,
                selection = TextRange(0, currentTitle.length)
            )
        )
    }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit title") },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                modifier = Modifier.focusRequester(focusRequester)
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(draft.text) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullTranscriptSheet(
    srtBody: String,
    plainText: String,
    selectAllOnOpen: Boolean,
    onDismiss: () -> Unit,
    onCopy: (String) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var mode by remember { mutableStateOf(PreviewMode.Srt) }
    val body = when (mode) {
        PreviewMode.Text -> plainText.ifBlank { srtBody }
        PreviewMode.Srt -> srtBody
    }
    val focusRequester = remember { FocusRequester() }
    var fieldValue by remember(body, selectAllOnOpen) {
        mutableStateOf(
            TextFieldValue(
                text = body,
                selection = if (selectAllOnOpen && body.isNotEmpty()) {
                    TextRange(0, body.length)
                } else {
                    TextRange.Zero
                }
            )
        )
    }
    LaunchedEffect(selectAllOnOpen, body) {
        if (selectAllOnOpen && body.isNotEmpty()) {
            fieldValue = TextFieldValue(text = body, selection = TextRange(0, body.length))
            focusRequester.requestFocus()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = scheme.surface,
        contentColor = scheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Transcript",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                PreviewToggle(
                    mode = mode,
                    onModeChange = { next ->
                        mode = next
                        val nextBody = when (next) {
                            PreviewMode.Text -> plainText.ifBlank { srtBody }
                            PreviewMode.Srt -> srtBody
                        }
                        fieldValue = TextFieldValue(text = nextBody)
                    }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (mode == PreviewMode.Text) "Plain text · long-press to select" else "SRT · long-press to select",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            TextField(
                value = fieldValue,
                onValueChange = { fieldValue = it },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .focusRequester(focusRequester),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = scheme.surfaceVariant,
                    unfocusedContainerColor = scheme.surfaceVariant,
                    disabledContainerColor = scheme.surfaceVariant,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                )
            )

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SecondaryButton(
                    text = if (mode == PreviewMode.Text) "Copy text" else "Copy SRT",
                    onClick = { onCopy(body) },
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.ContentCopy
                )
                PrimaryButton(
                    text = "Done",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
