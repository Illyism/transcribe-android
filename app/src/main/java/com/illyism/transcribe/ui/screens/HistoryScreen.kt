package com.illyism.transcribe.ui.screens

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.illyism.transcribe.data.HistoryEntry
import com.illyism.transcribe.domain.CostEstimator
import com.illyism.transcribe.domain.JobState
import com.illyism.transcribe.domain.SrtBuilder
import com.illyism.transcribe.ui.PendingFile
import com.illyism.transcribe.ui.components.LocalThumbnail
import com.illyism.transcribe.ui.components.PrimaryButton
import com.illyism.transcribe.ui.components.SecondaryButton
import com.illyism.transcribe.ui.components.formatBytes
import com.illyism.transcribe.ui.components.formatDuration
import com.illyism.transcribe.ui.theme.LocalJobStatusColors

private enum class TranscriptFilter { All, Processing, Done, Issues }

private val MotionEaseOut = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    entries: List<HistoryEntry>,
    pendingFiles: List<PendingFile>,
    estimatedBatchCost: Double,
    maxParallel: Int,
    onAddFiles: () -> Unit,
    onConfirmFiles: () -> Unit,
    onDismissFiles: () -> Unit,
    onRemovePending: (PendingFile) -> Unit,
    onOpenSettings: () -> Unit,
    onOpen: (String) -> Unit,
    onCancel: (String) -> Unit,
    onRetry: (String) -> Unit,
    onLocate: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRefresh: () -> Unit = {}
) {
    val scheme = MaterialTheme.colorScheme
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(TranscriptFilter.All) }
    var selectedEntry by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteEntry by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val motionDuration = motionDurationMillis()
    val fabExpanded by remember {
        derivedStateOf {
            entries.isEmpty() ||
                (listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset < 24)
        }
    }

    LaunchedEffect(Unit) { onRefresh() }

    val filtered = entries.filter { entry ->
        val matchesQuery = query.isBlank() ||
            entry.filename.contains(query.trim(), ignoreCase = true) ||
            entry.title.contains(query.trim(), ignoreCase = true) ||
            entry.summary.contains(query.trim(), ignoreCase = true)
        val matchesFilter = when (filter) {
            TranscriptFilter.All -> true
            TranscriptFilter.Processing -> entry.jobState in processingStates
            TranscriptFilter.Done -> entry.jobState == JobState.COMPLETED
            TranscriptFilter.Issues -> entry.jobState in issueStates
        }
        matchesQuery && matchesFilter
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Transcripts",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                singleLine = true,
                label = { Text("Search files") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Outlined.Clear, contentDescription = "Clear search")
                        }
                    }
                }
            )

            if (entries.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TranscriptFilter.entries.forEach { item ->
                        FilterChip(
                            selected = filter == item,
                            onClick = { filter = item },
                            label = { Text(item.name) }
                        )
                    }
                }
            }

            when {
                entries.isEmpty() -> EmptyTranscripts()
                filtered.isEmpty() -> EmptySearch(query)
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 112.dp),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 12.dp,
                        bottom = 12.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.id }) { entry ->
                        SwipeableTranscriptCard(
                            entry = entry,
                            jobsAhead = entries.count {
                                it.queueOrder < entry.queueOrder &&
                                    it.jobState in processingStates
                            },
                            motionDuration = motionDuration,
                            onClick = {
                                if (entry.jobState == JobState.COMPLETED) {
                                    onOpen(entry.id)
                                } else {
                                    selectedEntry = entry.id
                                }
                            },
                            onMoreClick = {
                                selectedEntry = entry.id
                            },
                            onDeleteClick = {
                                deleteEntry = entry.id
                            }
                        )
                    }
                }
            }
        }

        AnimatedContent(
            targetState = fabExpanded,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(20.dp),
            transitionSpec = {
                val enter = fadeIn(tween(motionDuration, easing = MotionEaseOut)) +
                    scaleIn(
                        initialScale = 0.96f,
                        animationSpec = tween(motionDuration, easing = MotionEaseOut)
                    )
                val exit = fadeOut(tween((motionDuration * 0.75f).toInt())) +
                    scaleOut(
                        targetScale = 0.96f,
                        animationSpec = tween(
                            (motionDuration * 0.75f).toInt(),
                            easing = MotionEaseOut
                        )
                    )
                (enter togetherWith exit).using(
                    SizeTransform(clip = false) { _, _ -> snap() }
                )
            },
            label = "add-files-fab"
        ) { expanded ->
            if (expanded) {
                ExtendedFloatingActionButton(
                    text = { Text("Add files") },
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    onClick = onAddFiles
                )
            } else {
                FloatingActionButton(onClick = onAddFiles) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add files")
                }
            }
        }
    }

    if (pendingFiles.isNotEmpty()) {
        BatchConfirmationSheet(
            files = pendingFiles,
            estimatedCost = estimatedBatchCost,
            maxParallel = maxParallel,
            onRemove = onRemovePending,
            onDismiss = onDismissFiles,
            onConfirm = onConfirmFiles
        )
    }

    selectedEntry?.let { id ->
        entries.firstOrNull { it.id == id }?.let { entry ->
            JobActionSheet(
                entry = entry,
                onDismiss = { selectedEntry = null },
                onOpen = {
                    selectedEntry = null
                    onOpen(entry.id)
                },
                onCancel = {
                    selectedEntry = null
                    onCancel(entry.id)
                },
                onRetry = {
                    selectedEntry = null
                    onRetry(entry.id)
                },
                onLocate = {
                    selectedEntry = null
                    onLocate(entry.id)
                },
                onDelete = {
                    selectedEntry = null
                    onDelete(entry.id)
                },
                onOpenSettings = {
                    selectedEntry = null
                    onOpenSettings()
                }
            )
        }
    }

    deleteEntry?.let { id ->
        val entry = entries.firstOrNull { it.id == id }
        if (entry != null) {
            AlertDialog(
                onDismissRequest = { deleteEntry = null },
                title = { Text("Delete transcript?") },
                text = {
                    Text(
                        "Delete “${entry.title.ifBlank { entry.filename }}”? " +
                            "This action cannot be undone."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            deleteEntry = null
                            onDelete(id)
                        }
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteEntry = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableTranscriptCard(
    entry: HistoryEntry,
    jobsAhead: Int,
    motionDuration: Int,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { it != SwipeToDismissBoxValue.StartToEnd },
        positionalThreshold = { distance -> distance * 0.25f }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SwipeAction(
                    label = "Actions",
                    icon = Icons.Outlined.MoreVert,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = onMoreClick
                )
                SwipeAction(
                    label = "Delete",
                    icon = Icons.Outlined.Delete,
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    onClick = onDeleteClick
                )
            }
        }
    ) {
        TranscriptStatusCard(
            entry = entry,
            jobsAhead = jobsAhead,
            motionDuration = motionDuration,
            onClick = onClick,
            onMoreClick = onMoreClick
        )
    }
}

@Composable
private fun SwipeAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .width(84.dp)
            .fillMaxSize(),
        color = color,
        contentColor = contentColor
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null)
            Spacer(modifier = Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun TranscriptStatusCard(
    entry: HistoryEntry,
    jobsAhead: Int,
    motionDuration: Int,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val status = statusText(entry, jobsAhead)
    val statusColor = statusColor(entry.jobState)
    val semantics = "${entry.title.ifBlank { entry.filename }}. $status"
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(
            durationMillis = if (pressed) motionDuration.coerceAtMost(100) else motionDuration,
            easing = MotionEaseOut
        ),
        label = "transcript-card-press"
    )
    val density = LocalDensity.current
    val statusMinHeight = with(density) {
        MaterialTheme.typography.bodySmall.lineHeight.toDp() *
            if (fontScale >= 1.5f) 2 else 1
    }

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            }
            .semantics { contentDescription = semantics },
        shape = RoundedCornerShape(16.dp),
        color = scheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .heightIn(min = 72.dp)
                .padding(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LocalThumbnail(
                    path = entry.thumbnailPath,
                    modifier = Modifier.size(48.dp),
                    cornerRadius = 6.dp,
                    fallbackIcon = Icons.Outlined.Videocam
                )
                Spacer(modifier = Modifier.size(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        entry.title.ifBlank { entry.filename },
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = statusMinHeight),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        AnimatedContent(
                            targetState = StatusVisual(status, statusColor),
                            transitionSpec = {
                                fadeIn(
                                    tween(motionDuration, easing = MotionEaseOut)
                                ) togetherWith fadeOut(
                                    tween(
                                        (motionDuration * 0.75f).toInt(),
                                        easing = MotionEaseOut
                                    )
                                )
                            },
                            label = "queue-status"
                        ) { visual ->
                            Text(
                                visual.text,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFeatureSettings = "tnum"
                                ),
                                color = visual.color,
                                maxLines = 2
                            )
                        }
                    }
                    if (
                        entry.sourceFileSizeBytes > 0 &&
                        entry.uploadedAudioBytes > 0 &&
                        entry.jobState !in setOf(JobState.RUNNING, JobState.RETRYING)
                    ) {
                        Text(
                            "${formatBytes(entry.sourceFileSizeBytes)} source → " +
                                "${formatBytes(entry.uploadedAudioBytes)} prepared",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFeatureSettings = "tnum"
                            ),
                            color = scheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onMoreClick) {
                    Icon(
                        Icons.Outlined.MoreVert,
                        contentDescription = "Options for ${entry.title.ifBlank { entry.filename }}"
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
            ) {
                if (entry.jobState in setOf(JobState.RUNNING, JobState.RETRYING)) {
                    LinearProgressIndicator(
                        progress = { entry.percent.coerceIn(0, 100) / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                }
            }
        }
    }
}

private data class StatusVisual(val text: String, val color: Color)

@Composable
private fun motionDurationMillis(): Int {
    val context = LocalContext.current
    return remember(context) {
        val scale = runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
        }.getOrDefault(1f)
        if (scale == 0f) 0 else 150
    }
}

@Composable
private fun statusColor(state: JobState): Color {
    val status = LocalJobStatusColors.current
    return when (state) {
        JobState.QUEUED -> status.queued
        JobState.RUNNING, JobState.RETRYING -> status.running
        JobState.NEEDS_ATTENTION, JobState.CANCELLING -> status.needsAttention
        JobState.FAILED -> status.failed
        JobState.COMPLETED -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

private fun statusText(entry: HistoryEntry, jobsAhead: Int): String = when (entry.jobState) {
    JobState.QUEUED -> "Queued · $jobsAhead ahead"
    JobState.RUNNING -> when {
        entry.chunksTotal > 0 ->
            "Transcribing · ${entry.percent}% · ${entry.chunksDone}/${entry.chunksTotal} chunks"
        else -> entry.stageMessage.ifBlank { "Preparing · ${entry.percent}%" }
    }
    JobState.WAITING_FOR_KEY -> "Waiting for key · Add key to continue"
    JobState.RETRYING -> "Retrying · ${entry.stageMessage}"
    JobState.NEEDS_ATTENTION -> "Needs attention · ${entry.errorMessage}"
    JobState.CANCELLING -> "Cancelling…"
    JobState.COMPLETED -> listOfNotNull(
        "SRT ready",
        entry.durationSeconds.takeIf { it > 0 }?.let(SrtBuilder::formatClock),
        entry.language.takeIf { it.isNotBlank() && it != "unknown" }
    ).joinToString(" · ")
    JobState.CANCELLED -> "Cancelled · No transcript created"
    JobState.FAILED -> "Failed · ${entry.errorMessage.ifBlank { "Tap to review" }}"
}

@Composable
private fun EmptyTranscripts() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 36.dp, vertical = 96.dp)
        ) {
            Icon(
                Icons.Outlined.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text("No transcripts yet", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Add video or audio files to start a background transcription queue.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptySearch(query: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "No files match “${query.trim()}”",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BatchConfirmationSheet(
    files: List<PendingFile>,
    estimatedCost: Double,
    maxParallel: Int,
    onRemove: (PendingFile) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .heightIn(max = 640.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item("heading") {
                Text("Add ${files.size} files", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "${formatDuration(files.sumOf { it.durationMs })} total · " +
                        "${CostEstimator.formatEstimate(estimatedCost)} estimated OpenAI cost",
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "One file at a time · Up to $maxParallel chunks in parallel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            items(files, key = { it.uri.toString() }) { file ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        file.displayName,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        formatDuration(file.durationMs),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFeatureSettings = "tnum"
                        )
                    )
                    IconButton(onClick = { onRemove(file) }) {
                        Icon(
                            Icons.Outlined.Clear,
                            contentDescription = "Remove ${file.displayName}"
                        )
                    }
                }
            }
            item("actions") {
                Spacer(modifier = Modifier.height(16.dp))
                PrimaryButton("Start processing", onClick = onConfirm)
                Spacer(modifier = Modifier.height(8.dp))
                SecondaryButton("Cancel", onClick = onDismiss)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JobActionSheet(
    entry: HistoryEntry,
    onDismiss: () -> Unit,
    onOpen: () -> Unit = {},
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onLocate: () -> Unit,
    onDelete: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                entry.filename,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                statusText(entry, 0),
                color = statusColor(entry.jobState),
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum")
            )
            if (entry.jobState in setOf(JobState.RUNNING, JobState.RETRYING)) {
                Spacer(modifier = Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { entry.percent.coerceIn(0, 100) / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (entry.sourceFileSizeBytes > 0 && entry.uploadedAudioBytes > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "${formatBytes(entry.sourceFileSizeBytes)} source → " +
                        "${formatBytes(entry.uploadedAudioBytes)} prepared",
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum")
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            when {
                entry.jobState == JobState.COMPLETED ->
                    PrimaryButton("Open transcript", onClick = onOpen)
                entry.jobState == JobState.FAILED &&
                    entry.errorKind == com.illyism.transcribe.domain.TranscribeErrorKind.SOURCE_UNAVAILABLE ->
                    PrimaryButton("Locate file", onClick = onLocate)
                entry.jobState in setOf(JobState.WAITING_FOR_KEY, JobState.NEEDS_ATTENTION) ->
                    PrimaryButton("Open settings", onClick = onOpenSettings)
                entry.jobState in setOf(JobState.FAILED, JobState.CANCELLED) ->
                    PrimaryButton("Retry", onClick = onRetry, icon = Icons.Outlined.Refresh)
                entry.jobState in setOf(JobState.QUEUED, JobState.RUNNING, JobState.RETRYING) ->
                    SecondaryButton("Cancel job", onClick = onCancel)
                else -> Unit
            }
            if (entry.jobState !in setOf(JobState.RUNNING, JobState.CANCELLING)) {
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(
                    onClick = onDelete,
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Delete transcript")
                }
            }
        }
    }
}

private val processingStates = setOf(
    JobState.QUEUED,
    JobState.RUNNING,
    JobState.WAITING_FOR_KEY,
    JobState.RETRYING,
    JobState.CANCELLING
)

private val issueStates = setOf(
    JobState.NEEDS_ATTENTION,
    JobState.FAILED,
    JobState.CANCELLED
)
