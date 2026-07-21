package com.illyism.transcribe.ui.skills

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.illyism.transcribe.domain.skills.ExportTarget
import com.illyism.transcribe.domain.skills.Skill
import com.illyism.transcribe.domain.skills.SkillIcons
import com.illyism.transcribe.domain.skills.SkillOutput
import com.illyism.transcribe.domain.skills.SkillOutputType
import com.illyism.transcribe.ui.components.PrimaryButton

private val presetColors = listOf(
    "#E8A838", "#7E57C2", "#42A5F5", "#66BB6A", "#EF5350", "#26A69A", "#FFA726"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SkillEditorScreen(
    skill: Skill,
    onChange: ((Skill) -> Skill) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
    ) {
        TopAppBar(
            title = { Text("Edit skill") },
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
                .padding(bottom = 24.dp)
        ) {
            OutlinedTextField(
                value = skill.name,
                onValueChange = { v -> onChange { it.copy(name = v) } },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = skill.description,
                onValueChange = { v -> onChange { it.copy(description = v) } },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("Icon", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SkillIcons.allNames.forEach { name ->
                    val selected = skill.icon == name
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selected) scheme.primary.copy(alpha = 0.2f)
                                else scheme.surfaceVariant
                            )
                            .border(
                                width = if (selected) 2.dp else 0.dp,
                                color = scheme.primary,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onChange { it.copy(icon = name) } },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(SkillIcons.vector(name), contentDescription = name)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Color", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                presetColors.forEach { hex ->
                    val selected = skill.color.equals(hex, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(SkillIcons.color(hex))
                            .border(
                                width = if (selected) 3.dp else 0.dp,
                                color = scheme.onBackground,
                                shape = CircleShape
                            )
                            .clickable { onChange { it.copy(color = hex) } }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = skill.prompt,
                onValueChange = { v -> onChange { it.copy(prompt = v) } },
                label = { Text("Prompt") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                minLines = 5
            )

            Spacer(modifier = Modifier.height(20.dp))
            Text("Outputs", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            val catalog = listOf(
                SkillOutput("result", "Result", SkillOutputType.MARKDOWN),
                SkillOutput("summary", "Summary", SkillOutputType.MARKDOWN),
                SkillOutput("action_items", "Action items", SkillOutputType.ACTION_ITEMS),
                SkillOutput("x_thread", "X thread", SkillOutputType.X_THREAD),
                SkillOutput("linkedin", "LinkedIn post", SkillOutputType.LINKEDIN),
                SkillOutput("youtube_description", "YouTube description", SkillOutputType.YOUTUBE_DESCRIPTION),
                SkillOutput("newsletter", "Newsletter", SkillOutputType.NEWSLETTER),
                SkillOutput("glossary", "Glossary", SkillOutputType.MARKDOWN),
                SkillOutput("quiz", "Quiz", SkillOutputType.QUIZ),
                SkillOutput("highlights", "Highlights", SkillOutputType.TIMESTAMP_LIST),
                SkillOutput("chapters", "Chapters", SkillOutputType.CHAPTERS)
            )
            catalog.forEach { option ->
                val checked = skill.outputs.any { it.id == option.id }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onChange { current ->
                                val outputs = current.outputs.toMutableList()
                                if (checked) outputs.removeAll { it.id == option.id }
                                else outputs.add(option)
                                current.copy(outputs = outputs)
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = {
                            onChange { current ->
                                val outputs = current.outputs.toMutableList()
                                if (checked) outputs.removeAll { it.id == option.id }
                                else outputs.add(option)
                                current.copy(outputs = outputs)
                            }
                        }
                    )
                    Text(option.label, style = MaterialTheme.typography.bodyLarge)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Exports", style = MaterialTheme.typography.titleSmall)
            listOf(ExportTarget.COPY, ExportTarget.SHARE, ExportTarget.MARKDOWN).forEach { target ->
                val checked = target in skill.exports
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onChange { current ->
                                val exports = current.exports.toMutableList()
                                if (checked) exports.remove(target) else exports.add(target)
                                current.copy(exports = exports)
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = {
                            onChange { current ->
                                val exports = current.exports.toMutableList()
                                if (checked) exports.remove(target) else exports.add(target)
                                current.copy(exports = exports)
                            }
                        }
                    )
                    Text(
                        target.name.lowercase().replaceFirstChar { c ->
                            c.titlecase()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            PrimaryButton("Save skill", onClick = onSave)
        }
    }
}
