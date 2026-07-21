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
import androidx.compose.runtime.Composable
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
import com.illyism.transcribe.domain.SrtBuilder
import com.illyism.transcribe.domain.skills.BuiltInSkills
import com.illyism.transcribe.domain.skills.Skill
import com.illyism.transcribe.domain.skills.SkillIcons
import com.illyism.transcribe.ui.components.LocalThumbnail
import com.illyism.transcribe.ui.components.PrimaryButton
import com.illyism.transcribe.ui.components.SecondaryButton
import com.illyism.transcribe.ui.components.SourceVideoPlayer
import com.illyism.transcribe.ui.components.formatBytes
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
) {
    val scheme = MaterialTheme.colorScheme
    val file = File(srtPath)
    val srtBody = remember(srtPath, preview) {
        runCatching { file.readText() }.getOrDefault(preview)
    }
    val plain = remember(srtBody) { SrtBuilder.plainText(srtBody) }
    val duration = durationSeconds.takeIf { it > 0 } ?: SrtBuilder.durationSeconds(srtBody)
    val langLabel = language
        ?.takeIf { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) }
        ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        ?: "Unknown"
    val previewText = srtBody.ifBlank { preview.ifBlank { "—" } }
    val headerTitle = title.trim().ifBlank { "Transcript ready" }
    val headerSubtitle = summary.trim().ifBlank { "Your subtitles have been saved." }
    val videoLabel = sourceName.ifBlank { file.nameWithoutExtension.ifBlank { "Video" } }
    val metaLine = listOf(
        SrtBuilder.formatClock(duration),
        langLabel,
        formatBytes(if (file.exists()) file.length() else 0L)
    ).joinToString(" · ")
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
                IconButton(onClick = onCopyLink) {
                    Icon(Icons.Outlined.Link, contentDescription = "Copy link")
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
                    summary = if (title.isNotBlank()) headerSubtitle else "",
                    thumbnailPath = thumbnailPath,
                    durationLabel = SrtBuilder.formatClock(duration),
                    playable = canPlay,
                    onPlay = { showPlayer = true },
                    onEditTitle = { showEditTitle = true }
                )

                if (title.isBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        headerSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant
                    )
                }

                if (quickSkills.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        "Create something",
                        style = MaterialTheme.typography.titleMedium
                    )
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
                                        showAskAi = true
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
                    IconButton(
                        onClick = {
                            selectAllOnOpen = true
                            showFull = true
                        }
                    ) {
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
                        .clickable {
                            selectAllOnOpen = false
                            showFull = true
                        }
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
                    Text(
                        "Creations",
                        style = MaterialTheme.typography.titleMedium
                    )
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
        }

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
private fun TranscriptMediaCard(
    title: String,
    filename: String,
    meta: String,
    summary: String,
    thumbnailPath: String,
    durationLabel: String,
    playable: Boolean,
    onPlay: () -> Unit,
    onEditTitle: () -> Unit
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
