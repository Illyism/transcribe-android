package com.illyism.transcribe.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.illyism.transcribe.ui.components.FileChip
import com.illyism.transcribe.ui.components.PrimaryButton
import com.illyism.transcribe.ui.components.SecondaryButton
import com.illyism.transcribe.ui.components.formatBytes
import com.illyism.transcribe.ui.theme.Amber
import com.illyism.transcribe.ui.theme.Bg
import com.illyism.transcribe.ui.theme.Success
import com.illyism.transcribe.ui.theme.SurfaceAlt
import java.io.File

@Composable
fun DoneScreen(
    srtPath: String,
    preview: String,
    onShare: () -> Unit,
    onCopyPreview: () -> Unit,
    onAnother: () -> Unit
) {
    val file = File(srtPath)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Success.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Check, contentDescription = null, tint = Success, modifier = Modifier.size(36.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("SRT saved.", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(18.dp))
        FileChip(
            name = file.name,
            meta = formatBytes(if (file.exists()) file.length() else 0L)
        )
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            "Preview (first lines)",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            preview.ifBlank { "—" },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceAlt)
                .padding(16.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        PrimaryButton("Open / Share SRT", onClick = onShare)
        Spacer(modifier = Modifier.height(10.dp))
        SecondaryButton("Copy text preview", onClick = onCopyPreview)
        Spacer(modifier = Modifier.height(10.dp))
        SecondaryButton("Transcribe another", onClick = onAnother)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Saved under app files · share to Files or Drive",
            style = MaterialTheme.typography.bodyMedium,
            color = Amber.copy(alpha = 0.8f)
        )
    }
}
