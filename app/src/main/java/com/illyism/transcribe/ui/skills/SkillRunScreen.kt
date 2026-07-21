package com.illyism.transcribe.ui.skills

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.illyism.transcribe.data.SkillModelTier
import com.illyism.transcribe.domain.skills.BuiltInSkills
import com.illyism.transcribe.domain.skills.Skill
import com.illyism.transcribe.ui.components.InfoBanner
import com.illyism.transcribe.ui.components.PrimaryButton
import com.illyism.transcribe.ui.components.SkillModelPicker
import com.illyism.transcribe.ui.components.StickyBottomBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillRunScreen(
    skill: Skill,
    transcriptName: String,
    selectedOutputIds: Set<String>,
    customPrompt: String,
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
    val canGenerate = if (isAskAi) {
        customPrompt.isNotBlank()
    } else {
        selectedOutputIds.isNotEmpty()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
    ) {
        TopAppBar(
            title = { Text(skill.name) },
            navigationIcon = {
                IconButton(onClick = onBack) {
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
                .padding(bottom = 8.dp)
        ) {
            Text(
                transcriptName,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant
            )
            Text(
                skill.estimatedRuntime,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )

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
                    placeholder = { Text("What were the main decisions?") }
                )
                if (customPrompt.isBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Enter a question to generate",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            } else {
                Text("Outputs", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                skill.outputs.forEach { out ->
                    val checked = out.id in selectedOutputIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { onToggleOutput(out.id) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { onToggleOutput(out.id) }
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
                if (selectedOutputIds.isEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Select at least one output",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }

            if (error != null) {
                Spacer(modifier = Modifier.height(16.dp))
                InfoBanner(text = error)
            }

            Spacer(modifier = Modifier.height(20.dp))
            SkillModelPicker(
                selected = skillModelTier,
                onSelected = onSkillModelTier,
                initiallyExpanded = false
            )
        }

        StickyBottomBar {
            PrimaryButton(
                text = "Generate",
                onClick = onGenerate,
                enabled = canGenerate,
                icon = Icons.Outlined.AutoAwesome
            )
        }
    }
}
