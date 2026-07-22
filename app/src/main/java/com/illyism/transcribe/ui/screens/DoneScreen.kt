package com.illyism.transcribe.ui.screens

import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.illyism.transcribe.data.CachedSkillRun
import com.illyism.transcribe.domain.ExportFormat
import com.illyism.transcribe.domain.SrtBuilder
import com.illyism.transcribe.domain.skills.BuiltInSkills
import com.illyism.transcribe.domain.skills.Skill
import com.illyism.transcribe.domain.skills.SkillIcons
import com.illyism.transcribe.ui.components.PrimaryButton
import com.illyism.transcribe.ui.components.SecondaryButton
import com.illyism.transcribe.ui.components.SourceVideoPlayer
import java.io.File
import java.util.Locale
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
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
    onLocate: () -> Unit = {},
    onDelete: () -> Unit = {},
    onBack: () -> Unit,
    showBack: Boolean = true,
    @Suppress("UNUSED_PARAMETER") phase: Any? = null,
    @Suppress("UNUSED_PARAMETER") sizeBytes: Long = 0,
    @Suppress("UNUSED_PARAMETER") durationMs: Long = 0,
    @Suppress("UNUSED_PARAMETER") hasApiKey: Boolean = true,
    @Suppress("UNUSED_PARAMETER") stage: Any? = null,
    @Suppress("UNUSED_PARAMETER") percent: Int = 100,
    @Suppress("UNUSED_PARAMETER") chunksDone: Int = 0,
    @Suppress("UNUSED_PARAMETER") chunksTotal: Int = 0,
    @Suppress("UNUSED_PARAMETER") videoBytes: Long = 0,
    @Suppress("UNUSED_PARAMETER") audioBytes: Long = 0,
    @Suppress("UNUSED_PARAMETER") message: String = "",
    @Suppress("UNUSED_PARAMETER") error: String? = null,
    @Suppress("UNUSED_PARAMETER") onStart: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onRetry: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onChooseDifferent: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onOpenSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val srt by produceState(preview, srtPath, preview) {
        value = withContext(Dispatchers.IO) {
            runCatching { File(srtPath).readText() }.getOrDefault(preview)
        }
    }
    val cues = remember(srt) { SrtBuilder.parse(srt) }
    val plainText = remember(srt) { SrtBuilder.plainText(srt) }
    val displayTitle = title.ifBlank { sourceName.substringBeforeLast('.').ifBlank { "Transcript" } }
    var query by remember { mutableStateOf("") }
    var activeCue by remember { mutableIntStateOf(-1) }
    var showExport by remember { mutableStateOf(false) }
    var showAsk by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val highlightDuration = remember(context) {
        val scale = runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
        }.getOrDefault(1f)
        if (scale == 0f) 0 else 150
    }

    var playbackFailed by remember(sourceUri) { mutableStateOf(false) }
    val player = remember(sourceUri) {
        sourceUri.takeIf(String::isNotBlank)?.let {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(Uri.parse(it)))
                prepare()
            }
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                playbackFailed = true
            }
        }
        player?.addListener(listener)
        onDispose {
            player?.removeListener(listener)
            player?.release()
        }
    }
    LaunchedEffect(player, cues) {
        val activePlayer = player ?: return@LaunchedEffect
        while (currentCoroutineContext().isActive) {
            if (activePlayer.isPlaying) {
                val seconds = activePlayer.currentPosition / 1000.0
                cues.lastOrNull {
                    seconds >= it.startSeconds && seconds < it.endSeconds
                }?.let { cue ->
                    if (activeCue != cue.index) activeCue = cue.index
                }
            }
            delay(250)
        }
    }

    val filteredCues = remember(cues, query) {
        if (query.isBlank()) cues else cues.filter { it.text.contains(query.trim(), true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showBack) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOfNotNull(
                        SrtBuilder.formatClock(durationSeconds),
                        language?.takeIf { it.isNotBlank() && it != "unknown" }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = { showEdit = true }) {
                Icon(Icons.Outlined.Edit, contentDescription = "Edit title")
            }
            IconButton(onClick = onCopyLink) {
                Icon(Icons.Outlined.Link, contentDescription = "Copy transcript link")
            }
            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Delete transcript",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }

        if (player != null) {
            val audioOnly = sourceName.substringAfterLast('.', "")
                .lowercase() in setOf("mp3", "m4a", "wav", "ogg", "flac")
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                SourceVideoPlayer(
                    sourceUri = sourceUri,
                    thumbnailPath = thumbnailPath,
                    externalPlayer = player,
                    onLocateSource = onLocate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (audioOnly) 88.dp else 210.dp)
                )
            }

            val activePosition = cues.indexOfFirst { it.index == activeCue }
            PlaybackProgress(
                cue = cues.getOrNull(activePosition),
                cuePosition = activePosition,
                cueCount = cues.size,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 2.dp,
                bottom = 20.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item("search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Search in transcript") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Outlined.Clear, contentDescription = "Clear search")
                            }
                        }
                    }
                )
            }

            if (quickSkills.isNotEmpty()) {
                item("skills") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        quickSkills.take(3).forEach { skill ->
                            AssistChip(
                                onClick = {
                                    if (skill.id == BuiltInSkills.askAi.id) showAsk = true
                                    else onRunSkill(skill.id)
                                },
                                label = { Text(skill.name) },
                                leadingIcon = {
                                    Icon(
                                        SkillIcons.vector(skill.icon),
                                        contentDescription = null,
                                        tint = SkillIcons.color(skill.color),
                                        modifier = Modifier.size(AssistChipDefaults.IconSize)
                                    )
                                }
                            )
                        }
                        AssistChip(
                            onClick = onManageSkills,
                            label = { Text("More") },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(AssistChipDefaults.IconSize)
                                )
                            }
                        )
                    }
                }
            }

            items(filteredCues, key = { it.index }) { cue ->
                CueRow(
                    cue = cue,
                    query = query,
                    active = activeCue == cue.index,
                    playable = player != null && !playbackFailed,
                    highlightDurationMillis = highlightDuration,
                    onPlay = {
                        if (playbackFailed) {
                            onLocate()
                        } else {
                            activeCue = cue.index
                            player?.seekTo((cue.startSeconds * 1000).toLong())
                            player?.play()
                        }
                    }
                )
            }

            if (filteredCues.isEmpty()) {
                item("empty-search") {
                    Text(
                        "No transcript matches “${query.trim()}”",
                        modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (skillRuns.isNotEmpty()) {
                item("creations-title") {
                    Text(
                        "Creations",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                items(skillRuns, key = { it.skillId }) { run ->
                    val interactionSource = remember { MutableInteractionSource() }
                    val pressed by interactionSource.collectIsPressedAsState()
                    val scale by animateFloatAsState(
                        targetValue = if (pressed) 0.96f else 1f,
                        animationSpec = tween(120),
                        label = "creation-press"
                    )
                    Surface(
                        onClick = { onOpenSkillResult(run.skillId) },
                        interactionSource = interactionSource,
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.size(12.dp))
                            Text(run.skillName, modifier = Modifier.weight(1f))
                            Icon(
                                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = null
                            )
                        }
                    }
                }
            }
        }

        Surface(tonalElevation = 3.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SecondaryButton(
                    text = "Copy",
                    onClick = { onCopyText(plainText.ifBlank { srt }) },
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.ContentCopy
                )
                PrimaryButton(
                    text = "Export",
                    onClick = { showExport = true },
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.IosShare
                )
            }
        }
    }

    if (showExport) {
        ExportSheet(
            onDismiss = { showExport = false },
            onCopy = {
                showExport = false
                onCopyText(plainText.ifBlank { srt })
            },
            onSelect = {
                showExport = false
                onExport(it)
            }
        )
    }
    if (showAsk) {
        AskAiDialog(
            onDismiss = { showAsk = false },
            onSubmit = {
                showAsk = false
                onAskAi(it)
            }
        )
    }
    if (showEdit) {
        EditTitleDialog(
            current = displayTitle,
            onDismiss = { showEdit = false },
            onSave = {
                showEdit = false
                onEditTitle(it)
            }
        )
    }
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete transcript?") },
            text = { Text("Are you sure you want to delete this transcript? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun CueRow(
    cue: SrtBuilder.Cue,
    query: String,
    active: Boolean,
    playable: Boolean,
    highlightDurationMillis: Int,
    onPlay: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val cueScale by animateFloatAsState(
        targetValue = if (pressed && playable) 0.96f else 1f,
        animationSpec = tween(if (pressed) highlightDurationMillis.coerceAtMost(100) else highlightDurationMillis),
        label = "cue-press"
    )
    val background by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = tween(highlightDurationMillis),
        label = "cue-highlight"
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = cueScale
                scaleY = cueScale
            }
            .clickable(
                enabled = playable,
                interactionSource = interactionSource,
                indication = null,
                onClick = onPlay
            )
            .semantics(mergeDescendants = true) {
                if (playable) {
                    role = Role.Button
                    stateDescription = if (active) "Playing" else "Not playing"
                }
                contentDescription = buildString {
                    append("Cue ${cue.index}, ")
                    append(SrtBuilder.formatClock(cue.startSeconds))
                    append(". ")
                    append(cue.text)
                    if (playable) append(". Play from this point")
                }
            },
        shape = RoundedCornerShape(14.dp),
        color = background,
        border = BorderStroke(
            if (active) 1.5.dp else 1.dp,
            if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${cue.index}   ${SrtBuilder.formatClock(cue.startSeconds)} → " +
                        SrtBuilder.formatClock(cue.endSeconds),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFeatureSettings = "tnum"
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(highlightQuery(cue.text, query), style = MaterialTheme.typography.bodyMedium)
            }
            if (playable) {
                Icon(
                    Icons.Outlined.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .padding(12.dp)
                        .offset(x = 1.5.dp)
                )
            }
        }
    }
}

@Composable
private fun PlaybackProgress(
    cue: SrtBuilder.Cue?,
    cuePosition: Int,
    cueCount: Int,
    modifier: Modifier = Modifier
) {
    val spokenStatus = if (cue == null) {
        "Playback ready"
    } else {
        "Playing cue ${cuePosition + 1} of $cueCount at " +
            SrtBuilder.formatClock(cue.startSeconds)
    }
    Text(
        text = spokenStatus,
        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 20.dp)
            .semantics {
                stateDescription = spokenStatus
                if (cue != null) {
                    liveRegion = LiveRegionMode.Polite
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = (cuePosition + 1).toFloat(),
                        range = 1f..cueCount.coerceAtLeast(1).toFloat(),
                        steps = (cueCount - 2).coerceAtLeast(0)
                    )
                }
            }
    )
}

@Composable
private fun highlightQuery(text: String, query: String): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(text)
    val start = text.indexOf(query.trim(), ignoreCase = true)
    if (start < 0) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        addStyle(
            SpanStyle(
                background = MaterialTheme.colorScheme.secondaryContainer,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            ),
            start,
            start + query.trim().length
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportSheet(
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onSelect: (ExportFormat) -> Unit
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Export transcript", style = MaterialTheme.typography.headlineSmall)
            ExportAction("Copy plain text", Icons.Outlined.ContentCopy, onCopy)
            ExportAction("Subtitles (.srt)", Icons.Outlined.ClosedCaption) {
                onSelect(ExportFormat.SRT)
            }
            ExportAction("Text (.txt)", Icons.Outlined.Description) {
                onSelect(ExportFormat.TXT)
            }
            ExportAction("Markdown (.md)", Icons.Outlined.Code) {
                onSelect(ExportFormat.MD)
            }
        }
    }
}

@Composable
private fun ExportAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(120),
        label = "export-press"
    )
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.size(12.dp))
            Text(label)
        }
    }
}

@Composable
private fun AskAiDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var prompt by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ask AI") },
        text = {
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                label = { Text("Question about this transcript") },
                minLines = 3
            )
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(prompt.trim()) }, enabled = prompt.isNotBlank()) {
                Text("Ask")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun EditTitleDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember(current) { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit title") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("Transcript title") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(value.trim()) }, enabled = value.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
