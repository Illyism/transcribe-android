package com.illyism.transcribe.ui.skills

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.illyism.transcribe.data.SkillModelTier
import com.illyism.transcribe.domain.skills.SkillOutput
import com.illyism.transcribe.domain.skills.SkillOutputResult
import com.illyism.transcribe.domain.skills.SkillOutputType
import com.illyism.transcribe.domain.skills.SkillRunResult
import com.illyism.transcribe.ui.components.InfoBanner
import com.illyism.transcribe.ui.components.MarkdownText
import com.illyism.transcribe.ui.components.PrimaryButton
import com.illyism.transcribe.ui.components.SecondaryButton
import com.illyism.transcribe.ui.components.SkillModelPicker
import com.illyism.transcribe.ui.components.StickyBottomBar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SkillResultsScreen(
    result: SkillRunResult,
    running: Boolean,
    error: String?,
    outputs: List<SkillOutput> = emptyList(),
    selectedOutputIds: Set<String> = emptySet(),
    onToggleOutput: (String) -> Unit = {},
    skillModelTier: SkillModelTier = SkillModelTier.TERRA_LIGHT,
    onSkillModelTier: (SkillModelTier) -> Unit = {},
    onRegenerate: (() -> Unit)? = null,
    onCopy: (String) -> Unit,
    onShare: (String, String) -> Unit,
    onExportAll: () -> Unit,
    onShareAll: () -> Unit,
    onCancel: () -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val reasoning = result.reasoning?.trim()?.takeIf { it.isNotBlank() }
    val listState = rememberLazyListState()
    var showAdjust by remember { mutableStateOf(false) }
    val canAdjust = onRegenerate != null && !running

    // While streaming, keep the newest content in view unless the user scrolls up.
    var followLatest by remember { mutableStateOf(true) }
    LaunchedEffect(listState.isScrollInProgress) {
        if (running && listState.isScrollInProgress) {
            val last = listState.layoutInfo.totalItemsCount - 1
            followLatest = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index == last
        }
    }
    LaunchedEffect(result.outputs, result.reasoning, followLatest, running) {
        if (running && followLatest) {
            val last = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
            listState.animateScrollToItem(last)
        }
    }

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
            actions = {
                if (canAdjust) {
                    IconButton(onClick = { showAdjust = true }) {
                        Icon(Icons.Outlined.Tune, contentDescription = "Adjust")
                    }
                }
            },
            // Scaffold already applies system bar padding — don't add it again.
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = scheme.background)
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (running) {
                item(key = "generating") {
                    GeneratingRow()
                }
            }

            error?.let {
                item(key = "error") { InfoBanner(text = it) }
            }

            reasoning?.let {
                item(key = "reasoning") {
                    ReasoningCard(reasoning = it, streaming = running)
                }
            }

            items(
                items = result.outputs,
                key = { it.outputId }
            ) { out ->
                ResultCard(
                    output = out,
                    onCopy = { onCopy(out.content) },
                    onShare = { onShare(out.content, out.label) }
                )
            }
        }

        StickyBottomBar {
            if (running) {
                SecondaryButton(text = "Cancel", onClick = onCancel)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (canAdjust) {
                        SecondaryButton(
                            text = "Adjust",
                            onClick = { showAdjust = true },
                            icon = Icons.Outlined.Tune,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (result.outputs.size > 1) {
                        SecondaryButton(
                            text = "Share all",
                            onClick = onShareAll,
                            icon = Icons.Outlined.Share,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    SecondaryButton(
                        text = if (result.outputs.size > 1) "Export" else "Export markdown",
                        onClick = onExportAll,
                        icon = Icons.Outlined.IosShare,
                        modifier = Modifier.weight(1f)
                    )
                }
                PrimaryButton(
                    text = "Done",
                    onClick = onDone,
                    icon = Icons.Outlined.Check
                )
            }
        }
    }

    if (showAdjust && onRegenerate != null) {
        AdjustSheet(
            outputs = outputs,
            selectedOutputIds = selectedOutputIds,
            onToggleOutput = onToggleOutput,
            skillModelTier = skillModelTier,
            onSkillModelTier = onSkillModelTier,
            onDismiss = { showAdjust = false },
            onRegenerate = {
                showAdjust = false
                onRegenerate()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AdjustSheet(
    outputs: List<SkillOutput>,
    selectedOutputIds: Set<String>,
    onToggleOutput: (String) -> Unit,
    skillModelTier: SkillModelTier,
    onSkillModelTier: (SkillModelTier) -> Unit,
    onDismiss: () -> Unit,
    onRegenerate: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val canRegenerate = outputs.isEmpty() || selectedOutputIds.isNotEmpty()
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
                "Adjust",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Change outputs or model, then regenerate.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (outputs.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Outputs", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    outputs.forEach { out ->
                        FilterChip(
                            selected = out.id in selectedOutputIds,
                            onClick = { onToggleOutput(out.id) },
                            label = { Text(out.label) }
                        )
                    }
                }
                if (selectedOutputIds.isEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Select at least one output",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            SkillModelPicker(
                selected = skillModelTier,
                onSelected = onSkillModelTier,
                initiallyExpanded = true
            )

            Spacer(modifier = Modifier.height(20.dp))
            PrimaryButton(
                text = "Regenerate",
                onClick = onRegenerate,
                enabled = canRegenerate,
                icon = Icons.Outlined.AutoAwesome
            )
        }
    }
}

@Composable
private fun GeneratingRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Text("Generating…", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ReasoningCard(reasoning: String, streaming: Boolean) {
    val scheme = MaterialTheme.colorScheme
    // Expand live while streaming; collapse once the final result is in.
    var expanded by remember(streaming) { mutableStateOf(streaming) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Reasoning",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.weight(1f)
            )
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand"
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            MarkdownText(
                markdown = reasoning,
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
            )
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
                Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy markdown")
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Outlined.Share, contentDescription = shareLabel)
            }
        }
        MarkdownText(
            markdown = output.content,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurface
        )
    }
}
