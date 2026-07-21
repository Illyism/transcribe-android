package com.illyism.transcribe.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import com.illyism.transcribe.ui.components.LinkButton
import com.illyism.transcribe.ui.components.PrimaryButton
import com.illyism.transcribe.ui.components.SecondaryButton
import com.illyism.transcribe.ui.theme.Bg
import com.illyism.transcribe.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    apiKey: String,
    chunkMinutes: Int,
    maxParallel: Int,
    model: String,
    rawMode: Boolean,
    onSaveApiKey: (String) -> Unit,
    onClearApiKey: () -> Unit,
    onChunkMinutes: (Int) -> Unit,
    onMaxParallel: (Int) -> Unit,
    onRawMode: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var draftKey by remember(apiKey) { mutableStateOf(apiKey) }
    var visible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
    ) {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
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
                color = TextSecondary
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
            Text("Defaults", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))

            Text("Default chunk length: $chunkMinutes min", style = MaterialTheme.typography.bodyLarge)
            Slider(
                value = chunkMinutes.toFloat(),
                onValueChange = { onChunkMinutes(it.toInt()) },
                valueRange = 5f..40f,
                steps = 6
            )

            Text("Max parallel uploads: $maxParallel", style = MaterialTheme.typography.bodyLarge)
            Slider(
                value = maxParallel.toFloat(),
                onValueChange = { onMaxParallel(it.toInt()) },
                valueRange = 1f..8f,
                steps = 6
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text("Transcription model", style = MaterialTheme.typography.bodyLarge)
            Text(model, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)

            Spacer(modifier = Modifier.height(16.dp))
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
