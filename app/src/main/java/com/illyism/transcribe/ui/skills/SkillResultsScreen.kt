package com.illyism.transcribe.ui.skills

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.illyism.transcribe.domain.skills.SkillOutputResult
import com.illyism.transcribe.domain.skills.SkillOutputType
import com.illyism.transcribe.domain.skills.SkillRunResult
import com.illyism.transcribe.ui.components.PrimaryButton
import com.illyism.transcribe.ui.components.SecondaryButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillResultsScreen(
    result: SkillRunResult,
    onCopy: (String) -> Unit,
    onShare: (String, String) -> Unit,
    onExportAll: () -> Unit,
    onShareAll: () -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
    ) {
        TopAppBar(
            title = { Text(result.skillName) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                }
            },
            // Scaffold already applies system bar padding — don't add it again.
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = scheme.background)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            result.outputs.forEach { out ->
                ResultCard(
                    output = out,
                    onCopy = { onCopy(out.content) },
                    onShare = { onShare(out.content, out.label) }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SecondaryButton(
                    text = "Share all",
                    onClick = onShareAll,
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Share
                )
                PrimaryButton(
                    text = "Export all",
                    onClick = onExportAll,
                    modifier = Modifier.weight(1f)
                )
            }
            TextButton(onClick = onDone, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Done")
            }
        }
    }
}

@Composable
private fun ResultCard(
    output: SkillOutputResult,
    onCopy: () -> Unit,
    onShare: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val shareLabel = when (output.type) {
        SkillOutputType.X_THREAD -> "Share to X"
        SkillOutputType.LINKEDIN -> "Share to LinkedIn"
        else -> "Share"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                output.label,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onCopy) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy")
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Outlined.Share, contentDescription = shareLabel)
            }
        }
        when (output.type) {
            SkillOutputType.ACTION_ITEMS -> ActionItemsBody(output.content)
            SkillOutputType.CHAPTERS, SkillOutputType.TIMESTAMP_LIST -> TimestampListBody(output.content)
            else -> SelectionContainer {
                Text(output.content, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ActionItemsBody(content: String) {
    val lines = content.lines()
        .map { it.trim().removePrefix("-").removePrefix("*").removePrefix("☐").removePrefix("[ ]").trim() }
        .filter { it.isNotBlank() }
    if (lines.isEmpty()) {
        Text(content, style = MaterialTheme.typography.bodyMedium)
        return
    }
    Column {
        lines.forEach { line ->
            Row(verticalAlignment = Alignment.Top) {
                Checkbox(checked = false, onCheckedChange = null)
                Text(
                    line,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun TimestampListBody(content: String) {
    val lines = content.lines().map { it.trim() }.filter { it.isNotBlank() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        lines.forEach { line ->
            Text("• $line", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
