package com.illyism.transcribe.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.illyism.transcribe.data.SkillModelTier
import com.illyism.transcribe.ui.components.LinkButton
import com.illyism.transcribe.ui.components.PrimaryButton
import com.illyism.transcribe.ui.components.SecondaryButton
import com.illyism.transcribe.ui.components.SkillModelPicker
import com.illyism.transcribe.ui.components.formatBytes
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    apiKey: String,
    chunkMinutes: Int,
    maxParallel: Int,
    model: String,
    rawMode: Boolean,
    skillModelTier: SkillModelTier,
    onSaveApiKey: (String) -> Unit,
    onClearApiKey: () -> Unit,
    onChunkMinutes: (Int) -> Unit,
    onMaxParallel: (Int) -> Unit,
    onRawMode: (Boolean) -> Unit,
    onSkillModelTier: (SkillModelTier) -> Unit,
    totalUploadAvoidedBytes: Long,
    totalPreparedAudioBytes: Long,
    videosProcessedCount: Int,
    onManageSkills: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    var draftKey by remember(apiKey) { mutableStateOf(apiKey) }
    var visible by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
    ) {
        TopAppBar(
            title = { Text("Settings") },
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
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Text("OpenAI API key", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = draftKey,
                onValueChange = { draftKey = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("sk-…") },
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { visible = !visible }) {
                        Icon(
                            if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = null
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Encrypted and stored only on this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            PrimaryButton("Save key", onClick = { onSaveApiKey(draftKey) })
            Spacer(modifier = Modifier.height(8.dp))
            SecondaryButton("Clear key", onClick = {
                draftKey = ""
                onClearApiKey()
            })
            LinkButton(
                text = "How to get an API key",
                onClick = {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://platform.openai.com/api-keys")
                    )
                    context.startActivity(intent)
                }
            )

            Spacer(modifier = Modifier.height(28.dp))
            Text("Usage & impact", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(scheme.surfaceContainer, androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Text("Estimated video upload avoided", style = MaterialTheme.typography.bodyMedium)
                Text(
                    formatBytes(totalUploadAvoidedBytes),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFeatureSettings = "tnum",
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    ),
                    color = scheme.primary
                )
                Text(
                    "By extracting audio on-device instead of uploading full videos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    UsageStat(
                        label = "Videos processed",
                        value = videosProcessedCount.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    UsageStat(
                        label = "Audio prepared",
                        value = formatBytes(totalPreparedAudioBytes),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Estimates exclude transport overhead and retries.",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
            Text("Skills", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onManageSkills, modifier = Modifier.fillMaxWidth()) {
                Text("Manage custom skills")
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Skills model", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Used for Create something / Skills. Transcription stays $model.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            SkillModelPicker(
                selected = skillModelTier,
                onSelected = onSkillModelTier
            )

            Spacer(modifier = Modifier.height(28.dp))
            OutlinedButton(
                onClick = { advanced = !advanced },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (advanced) "Hide advanced processing" else "Advanced processing")
            }
            if (advanced) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Chunk length: $chunkMinutes min",
                    style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum")
                )
                Slider(
                    value = chunkMinutes.toFloat(),
                    onValueChange = { onChunkMinutes(it.toInt()) },
                    valueRange = 5f..40f,
                    steps = 6
                )
                Text(
                    "Parallel uploads: $maxParallel",
                    style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum")
                )
                Slider(
                    value = maxParallel.toFloat(),
                    onValueChange = { onMaxParallel(it.toInt()) },
                    valueRange = 1f..8f,
                    steps = 6
                )
                Text("Transcription model", style = MaterialTheme.typography.bodyLarge)
                Text(
                    model,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Raw mode", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Skip 1.2× speed optimize (larger uploads).",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Switch(checked = rawMode, onCheckedChange = onRawMode)
            }
        }
    }
}

@Composable
private fun UsageStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum")
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
