package com.illyism.transcribe.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.illyism.transcribe.ui.components.FileChip
import com.illyism.transcribe.ui.components.InfoBanner
import com.illyism.transcribe.ui.components.PrimaryButton
import com.illyism.transcribe.ui.components.SecondaryButton
import com.illyism.transcribe.ui.components.formatBytes
import com.illyism.transcribe.ui.components.formatDuration
import com.illyism.transcribe.ui.theme.Amber
import com.illyism.transcribe.ui.theme.Bg
import com.illyism.transcribe.ui.theme.SurfaceAlt
import com.illyism.transcribe.ui.theme.TextSecondary

@Composable
fun SelectedScreen(
    name: String,
    sizeBytes: Long,
    durationMs: Long,
    hasApiKey: Boolean,
    onStart: () -> Unit,
    onChooseDifferent: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text("Ready to transcribe", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        FileChip(
            name = name,
            meta = "${formatBytes(sizeBytes)} · ${formatDuration(durationMs)}"
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text("What happens next", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceAlt)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StepIcon(Icons.Outlined.GraphicEq, "Extract\nlocally")
            StepIcon(Icons.Outlined.Tune, "Optimize\nchunks")
            StepIcon(Icons.Outlined.Cloud, "Transcribe\nin parallel")
        }
        Spacer(modifier = Modifier.height(16.dp))
        InfoBanner(
            text = "Full video stays on your phone. Only optimized audio chunks are uploaded.",
            icon = Icons.Outlined.Shield,
            tint = Amber
        )
        if (!hasApiKey) {
            Spacer(modifier = Modifier.height(12.dp))
            InfoBanner(
                text = "API key required before starting. Add it in Settings.",
                icon = Icons.Outlined.WarningAmber
            )
        }
        Spacer(modifier = Modifier.height(28.dp))
        PrimaryButton(
            text = "Start transcription",
            onClick = onStart,
            enabled = hasApiKey
        )
        Spacer(modifier = Modifier.height(10.dp))
        SecondaryButton("Choose different video", onClick = onChooseDifferent)
        if (!hasApiKey) {
            Spacer(modifier = Modifier.height(8.dp))
            SecondaryButton("Add API key", onClick = onOpenSettings)
        }
    }
}

@Composable
private fun StepIcon(icon: ImageVector, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = Amber)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
    }
}
