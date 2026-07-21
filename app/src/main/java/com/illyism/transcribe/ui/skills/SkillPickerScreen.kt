package com.illyism.transcribe.ui.skills

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.illyism.transcribe.domain.skills.Skill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillPickerScreen(
    transcriptName: String,
    customSkills: List<Skill>,
    builtIns: List<Skill>,
    onSelect: (String) -> Unit,
    onBack: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
    ) {
        TopAppBar(
            title = { Text("Create something") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = scheme.background)
        )

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Text(
                    "Pick a skill for",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
                Text(
                    transcriptName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (customSkills.isNotEmpty()) {
                item {
                    Text("My skills", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                }
                items(customSkills, key = { it.id }) { skill ->
                    SkillCard(
                        skill = skill,
                        onClick = { onSelect(skill.id) },
                        onEdit = {},
                        onDuplicate = {},
                        onDelete = {},
                        onExport = {},
                        showDelete = false,
                        showMenu = false
                    )
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }

            item {
                Text("Explore", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(4.dp))
            }
            items(builtIns, key = { it.id }) { skill ->
                SkillCard(
                    skill = skill,
                    onClick = { onSelect(skill.id) },
                    onEdit = {},
                    onDuplicate = {},
                    onDelete = {},
                    onExport = {},
                    showDelete = false,
                    showMenu = false
                )
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}
