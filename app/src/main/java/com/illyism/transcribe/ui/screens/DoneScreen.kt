package com.illyism.transcribe.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.illyism.transcribe.domain.SrtBuilder
import com.illyism.transcribe.ui.components.PrimaryButton
import com.illyism.transcribe.ui.components.SecondaryButton
import com.illyism.transcribe.ui.components.StickyBottomBar
import com.illyism.transcribe.ui.components.formatBytes
import java.io.File
import java.util.Locale

private enum class PreviewMode { Text, Srt }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoneScreen(
    srtPath: String,
    preview: String,
    language: String?,
    durationSeconds: Double,
    saveLocationLabel: String,
    onExport: (ExportFormat) -> Unit,
    onCopyText: (String) -> Unit,
    onRename: (String) -> Unit,
    onCreateSomething: () -> Unit,
    onAnother: () -> Unit,
    onBack: () -> Unit
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
    val previewText = plain.ifBlank { srtBody.ifBlank { preview.ifBlank { "—" } } }

    var showRename by remember { mutableStateOf(false) }
    var showFull by remember { mutableStateOf(false) }
    var selectAllOnOpen by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
            Text(
                "Transcribe",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(scheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = null,
                        tint = scheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        "Transcript ready",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        "Your subtitles have been saved.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(scheme.surfaceVariant)
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(scheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.InsertDriveFile,
                            contentDescription = null,
                            tint = scheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            file.name.ifBlank { "transcript.srt" },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            listOf(
                                formatBytes(if (file.exists()) file.length() else 0L),
                                SrtBuilder.formatClock(duration),
                                langLabel
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showRename = true }) {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = "Rename",
                            tint = scheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.FolderOpen,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Saved to ", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                    Text(
                        saveLocationLabel,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = scheme.primary,
                            fontWeight = FontWeight.Medium
                        )
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
                IconButton(
                    onClick = { onCopyText(plain.ifBlank { srtBody }) }
                ) {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        contentDescription = "Copy text",
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
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurface,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        StickyBottomBar(showDivider = false) {
            PrimaryButton(
                text = "Create something",
                onClick = onCreateSomething,
                icon = Icons.Outlined.AutoAwesome
            )
            SecondaryButton(
                text = "Export",
                onClick = { showExport = true },
                icon = Icons.Outlined.IosShare
            )
            SecondaryButton(
                text = "Transcribe another video",
                onClick = onAnother,
                icon = Icons.Outlined.Videocam
            )
        }
    }

    if (showRename) {
        RenameDialog(
            currentName = file.nameWithoutExtension,
            onDismiss = { showRename = false },
            onConfirm = {
                onRename(it)
                showRename = false
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

@Composable
private fun RenameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    var draft by remember {
        mutableStateOf(
            TextFieldValue(
                text = currentName,
                selection = TextRange(0, currentName.length)
            )
        )
    }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename") },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                suffix = {
                    Text(".srt", color = MaterialTheme.colorScheme.onSurfaceVariant)
                },
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
    var mode by remember { mutableStateOf(PreviewMode.Text) }
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
