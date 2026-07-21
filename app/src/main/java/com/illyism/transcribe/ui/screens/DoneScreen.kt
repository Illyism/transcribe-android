package com.illyism.transcribe.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.illyism.transcribe.domain.SrtBuilder
import com.illyism.transcribe.ui.components.PrimaryButton
import com.illyism.transcribe.ui.components.SecondaryButton
import com.illyism.transcribe.ui.components.formatBytes
import com.illyism.transcribe.ui.theme.Amber
import com.illyism.transcribe.ui.theme.Bg
import com.illyism.transcribe.ui.theme.Success
import com.illyism.transcribe.ui.theme.Surface
import com.illyism.transcribe.ui.theme.SurfaceAlt
import com.illyism.transcribe.ui.theme.TextPrimary
import com.illyism.transcribe.ui.theme.TextSecondary
import java.io.File
import java.util.Locale

private enum class PreviewMode { Text, Srt }

@Composable
fun DoneScreen(
    srtPath: String,
    preview: String,
    language: String?,
    durationSeconds: Double,
    saveLocationLabel: String,
    onShare: () -> Unit,
    onOpenFile: () -> Unit,
    onCopyText: () -> Unit,
    onRename: (String) -> Unit,
    onOpenSourceVideo: () -> Unit,
    onEditSave: (String) -> Unit,
    onReadSrt: () -> String,
    onTranscribeAgain: () -> Unit,
    onAnother: () -> Unit,
    onBack: () -> Unit
) {
    val file = File(srtPath)
    val srtBody = remember(srtPath, preview) {
        runCatching { file.readText() }.getOrDefault(preview)
    }
    val plain = remember(srtBody) { SrtBuilder.plainText(srtBody) }
    val segments = remember(srtBody) { SrtBuilder.segmentCount(srtBody) }
    val duration = durationSeconds.takeIf { it > 0 } ?: SrtBuilder.durationSeconds(srtBody)
    val langLabel = language
        ?.takeIf { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) }
        ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        ?: "Unknown"

    var previewMode by remember { mutableStateOf(PreviewMode.Text) }
    var moreOpen by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showFull by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
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
            Box {
                IconButton(onClick = { moreOpen = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "More")
                }
                DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Open source video") },
                        onClick = {
                            moreOpen = false
                            onOpenSourceVideo()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Share SRT") },
                        onClick = {
                            moreOpen = false
                            onShare()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = {
                            moreOpen = false
                            showRename = true
                        }
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 12.dp)
        ) {
            // Compact completion header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Success.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = null,
                        tint = Success,
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
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // File card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceAlt)
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Amber.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.InsertDriveFile, contentDescription = null, tint = Amber)
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
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    IconButton(onClick = { showRename = true }) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Rename", tint = TextSecondary)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.FolderOpen,
                        contentDescription = null,
                        tint = Amber,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Saved to ",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        saveLocationLabel,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Amber,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Primary actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PrimaryButton(
                    text = "Share SRT",
                    onClick = onShare,
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Share
                )
                SecondaryButton(
                    text = "Open file",
                    onClick = onOpenFile,
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.FolderOpen
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CompactAction(
                    label = "Copy text",
                    icon = Icons.Outlined.ContentCopy,
                    onClick = onCopyText,
                    modifier = Modifier.weight(1f)
                )
                CompactAction(
                    label = "Rename",
                    icon = Icons.Outlined.Edit,
                    onClick = { showRename = true },
                    modifier = Modifier.weight(1f)
                )
                Box(modifier = Modifier.weight(1f)) {
                    CompactAction(
                        label = "More",
                        icon = Icons.Outlined.MoreVert,
                        onClick = { moreOpen = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Open source video") },
                            onClick = {
                                moreOpen = false
                                onOpenSourceVideo()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Share SRT") },
                            onClick = {
                                moreOpen = false
                                onShare()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            onClick = {
                                moreOpen = false
                                showRename = true
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Preview
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Preview",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                PreviewToggle(mode = previewMode, onModeChange = { previewMode = it })
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "00:00–${SrtBuilder.formatClock(duration)} · $segments subtitle segments",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceAlt)
                    .padding(16.dp)
            ) {
                Text(
                    text = when (previewMode) {
                        PreviewMode.Text -> plain.ifBlank { "—" }
                        PreviewMode.Srt -> srtBody.ifBlank { preview.ifBlank { "—" } }
                    },
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = TextPrimary,
                        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
                    ),
                    maxLines = if (previewMode == PreviewMode.Text) 5 else 8,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "View full transcript →",
                    color = Amber,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clickable {
                        editing = false
                        showFull = true
                    }
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Quality checkpoint
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Surface)
                    .border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = Amber)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Looks wrong?",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "Improve this transcript or try again.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Edit transcript",
                    color = Amber,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clickable {
                        editing = true
                        showFull = true
                    }
                )
                Text("|", color = TextSecondary)
                Text(
                    "Transcribe again",
                    color = Amber,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clickable(onClick = onTranscribeAgain)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
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

    if (showFull) {
        FullTranscriptDialog(
            initial = if (editing) onReadSrt() else srtBody,
            editable = editing,
            onDismiss = { showFull = false },
            onSave = {
                onEditSave(it)
                showFull = false
            }
        )
    }
}

@Composable
private fun PreviewToggle(mode: PreviewMode, onModeChange: (PreviewMode) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceAlt)
            .padding(3.dp)
    ) {
        PreviewToggleChip("Text", mode == PreviewMode.Text) { onModeChange(PreviewMode.Text) }
        PreviewToggleChip("SRT", mode == PreviewMode.Srt) { onModeChange(PreviewMode.Srt) }
    }
}

@Composable
private fun PreviewToggleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) Color(0xFF1A1200) else TextSecondary,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Amber else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
private fun CompactAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceAlt)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
    }
}

@Composable
private fun RenameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var draft by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename") },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                suffix = { Text(".srt", color = TextSecondary) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = SurfaceAlt,
                    unfocusedContainerColor = SurfaceAlt
                )
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(draft) }) { Text("Save", color = Amber) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun FullTranscriptDialog(
    initial: String,
    editable: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    var isEditing by remember(editable) { mutableStateOf(editable) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Bg)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isEditing) "Edit transcript" else "Full transcript",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDismiss) { Text("Close", color = Amber) }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (isEditing) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = SurfaceAlt,
                        unfocusedContainerColor = SurfaceAlt
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                PrimaryButton("Save changes", onClick = { onSave(draft) })
            } else {
                Text(
                    draft,
                    style = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceAlt)
                        .padding(16.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                SecondaryButton(
                    text = "Edit transcript",
                    onClick = { isEditing = true },
                    icon = Icons.Outlined.Edit
                )
            }
        }
    }
}
