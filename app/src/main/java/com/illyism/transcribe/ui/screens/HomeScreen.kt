package com.illyism.transcribe.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.illyism.transcribe.ui.components.LinkButton
import com.illyism.transcribe.ui.components.PrimaryButton
import com.illyism.transcribe.ui.theme.Amber
import com.illyism.transcribe.ui.theme.Bg
import com.illyism.transcribe.ui.theme.Surface
import com.illyism.transcribe.ui.theme.SurfaceAlt
import com.illyism.transcribe.ui.theme.TextSecondary

@Composable
fun HomeScreen(
    onChooseVideo: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Bg, Surface, Bg)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Transcribe", style = MaterialTheme.typography.displaySmall, color = Amber)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Transcripts from videos on your phone.",
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "Extract audio locally. No 15GB transfers.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary
                )
            }

            HeroIllustration()

            Column {
                Text(
                    "Full video stays on your device. Only small audio chunks are uploaded.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(20.dp))
                PrimaryButton("Choose video", onClick = onChooseVideo)
                Spacer(modifier = Modifier.height(8.dp))
                LinkButton("API key", onClick = onOpenSettings, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

@Composable
private fun HeroIllustration() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(SurfaceAlt),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Amber.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Mic, contentDescription = null, tint = Amber, modifier = Modifier.size(34.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("On-device extract", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Lock stays local · waves go to Whisper",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(14.dp))
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = Amber)
                Icon(Icons.Outlined.CloudUpload, contentDescription = null, tint = TextSecondary)
            }
        }
    }
}
