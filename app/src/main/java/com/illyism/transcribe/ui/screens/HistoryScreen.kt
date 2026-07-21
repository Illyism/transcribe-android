package com.illyism.transcribe.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.illyism.transcribe.data.HistoryEntry
import com.illyism.transcribe.domain.SrtBuilder
import com.illyism.transcribe.ui.components.LocalThumbnail
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryScreen(
    entries: List<HistoryEntry>,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRefresh: () -> Unit = {}
) {
    val scheme = MaterialTheme.colorScheme
    var query by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        onRefresh()
    }

    val filtered = if (query.isBlank()) {
        entries
    } else {
        val q = query.trim()
        entries.filter { entry ->
            entry.filename.contains(q, ignoreCase = true) ||
                entry.title.contains(q, ignoreCase = true) ||
                entry.summary.contains(q, ignoreCase = true) ||
                entry.preview.contains(q, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .padding(horizontal = 20.dp)
            .padding(top = 24.dp)
    ) {
        Text(
            "Files",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Videos you've transcribed — open one to create with Skills.",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (entries.isNotEmpty()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search by name or transcript…") },
                leadingIcon = {
                    Icon(Icons.Outlined.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Outlined.Clear, contentDescription = "Clear search")
                        }
                    }
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        when {
            entries.isEmpty() -> {
                HistoryEmptyState(
                    icon = Icons.Outlined.Folder,
                    title = "No files yet",
                    subtitle = "Transcribe a video and it will show up here."
                )
            }
            filtered.isEmpty() -> {
                HistoryEmptyState(
                    icon = Icons.Outlined.Search,
                    title = "No matches",
                    subtitle = "Nothing matches “${query.trim()}”. Try another name or phrase."
                )
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = { it.id }) { entry ->
                        HistoryRow(
                            entry = entry,
                            onOpen = { onOpen(entry.id) },
                            onDelete = { onDelete(entry.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 80.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        .format(Date(entry.createdAt))
    val displayTitle = entry.title.ifBlank { entry.filename }
    val showFilename = entry.title.isNotBlank()
    val body = entry.summary.ifBlank { entry.preview }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surfaceVariant)
            .clickable(onClick = onOpen)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LocalThumbnail(
            path = entry.thumbnailPath,
            modifier = Modifier.size(56.dp),
            fallbackIcon = Icons.Outlined.Videocam
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                displayTitle,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (showFilename) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    entry.filename,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                listOfNotNull(
                    date,
                    entry.durationSeconds.takeIf { it > 0 }?.let { SrtBuilder.formatClock(it) },
                    entry.language.takeIf { it.isNotBlank() && !it.equals("unknown", true) }
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
            if (body.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = "Delete",
                tint = scheme.onSurfaceVariant
            )
        }
    }
}
