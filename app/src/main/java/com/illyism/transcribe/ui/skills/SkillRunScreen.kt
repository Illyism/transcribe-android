package com.illyism.transcribe.ui.skills

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.illyism.transcribe.data.SkillModelTier
import com.illyism.transcribe.domain.skills.BuiltInSkills
import com.illyism.transcribe.domain.skills.Skill
import com.illyism.transcribe.domain.skills.SkillIcons
import com.illyism.transcribe.ui.components.InfoBanner
import com.illyism.transcribe.ui.components.PrimaryButton
import com.illyism.transcribe.ui.components.SkillModelPicker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillRunScreen(
    skill: Skill,
    transcriptName: String,
    selectedOutputIds: Set<String>,
    customPrompt: String,
    running: Boolean,
    error: String?,
    skillModelTier: SkillModelTier,
    onSkillModelTier: (SkillModelTier) -> Unit,
    onToggleOutput: (String) -> Unit,
    onCustomPrompt: (String) -> Unit,
    onGenerate: () -> Unit,
    onBack: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val isAskAi = skill.id == BuiltInSkills.askAi.id
    val iconColor = SkillIcons.color(skill.color)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
    ) {
        TopAppBar(
            title = { Text(skill.name) },
            navigationIcon = {
                IconButton(onClick = onBack, enabled = !running) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = scheme.background)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(scheme.surfaceVariant)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BoxIcon(skill.icon, iconColor)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(skill.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Using · $transcriptName",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                    Text(
                        skill.estimatedRuntime,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (isAskAi) {
                Text("Your question", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = customPrompt,
                    onValueChange = onCustomPrompt,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    placeholder = { Text("What were the main decisions?") },
                    enabled = !running
                )
            } else {
                Text("Outputs", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                skill.outputs.forEach { out ->
                    val checked = out.id in selectedOutputIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !running) { onToggleOutput(out.id) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { onToggleOutput(out.id) },
                            enabled = !running
                        )
                        Column {
                            Text(out.label, style = MaterialTheme.typography.bodyLarge)
                            if (out.hint.isNotBlank()) {
                                Text(
                                    out.hint,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (error != null) {
                Spacer(modifier = Modifier.height(16.dp))
                InfoBanner(text = error)
            }

            Spacer(modifier = Modifier.height(20.dp))
            if (!running) {
                SkillModelPicker(
                    selected = skillModelTier,
                    onSelected = onSkillModelTier,
                    initiallyExpanded = false
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (running) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Generating…", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                PrimaryButton(
                    text = "Generate",
                    onClick = onGenerate,
                    icon = Icons.Outlined.AutoAwesome
                )
            }
        }
    }
}

@Composable
private fun BoxIcon(icon: String, tint: Color) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(SkillIcons.vector(icon), contentDescription = null, tint = tint)
    }
}
