package com.illyism.transcribe.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.illyism.transcribe.ui.components.PrimaryButton

@Composable
fun HomeScreen(
    onChooseVideo: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to scheme.background,
                    0.45f to scheme.surface.copy(alpha = 0.55f),
                    1f to scheme.background
                )
            ),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 12.dp, bottom = 28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Transcribe",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = scheme.primary
                )
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        Icons.Outlined.Settings,
                        contentDescription = "Settings",
                        tint = scheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                "Transcripts from\nvideos on your phone.",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = scheme.onBackground
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Extract audio on-device. Only small Whisper chunks leave the phone — never the full file.",
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(36.dp))

            StartCard(onChooseVideo = onChooseVideo)

            Spacer(modifier = Modifier.height(28.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TrustChip(
                    icon = Icons.Outlined.Lock,
                    label = "Local extract",
                    modifier = Modifier.weight(1f)
                )
                TrustChip(
                    icon = Icons.Outlined.GraphicEq,
                    label = "Audio only",
                    modifier = Modifier.weight(1f)
                )
                TrustChip(
                    icon = Icons.Outlined.FolderOpen,
                    label = "Any size",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StartCard(onChooseVideo: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        scheme.primary.copy(alpha = 0.14f),
                        scheme.surfaceVariant.copy(alpha = 0.9f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        scheme.primary.copy(alpha = 0.45f),
                        scheme.outline.copy(alpha = 0.25f)
                    )
                ),
                shape = shape
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = scheme.primary),
                onClick = onChooseVideo
            )
            .padding(horizontal = 22.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PipelineVisual(
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
        )
        Spacer(modifier = Modifier.height(22.dp))
        Text(
            "Choose a video or audio file",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "We’ll pull the soundtrack locally, then send optimized chunks to Whisper.",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(22.dp))
        PrimaryButton(
            text = "Choose video",
            onClick = onChooseVideo,
            icon = Icons.Outlined.Videocam
        )
    }
}

@Composable
private fun TrustChip(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surfaceVariant.copy(alpha = 0.75f))
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = scheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** Compact video → waveform → text pipeline mark. */
@Composable
private fun PipelineVisual(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val primary = scheme.primary
    val muted = scheme.onSurfaceVariant.copy(alpha = 0.55f)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StepBubble(Icons.Outlined.Videocam, primary)
        Connector(muted)
        WaveBars(primary)
        Connector(muted)
        StepBubble(Icons.Outlined.GraphicEq, primary)
    }
}

@Composable
private fun StepBubble(icon: ImageVector, tint: Color) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun Connector(color: Color) {
    Canvas(modifier = Modifier.size(width = 28.dp, height = 2.dp)) {
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = 3f,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun WaveBars(color: Color) {
    Box(
        modifier = Modifier
            .size(width = 56.dp, height = 36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.10f)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp)) {
            val bars = 7
            val gap = size.width / (bars * 2f)
            val barW = gap * 0.9f
            val heights = floatArrayOf(0.35f, 0.7f, 0.45f, 0.95f, 0.55f, 0.8f, 0.4f)
            for (i in 0 until bars) {
                val h = size.height * heights[i]
                val x = gap + i * (barW + gap)
                val y = (size.height - h) / 2f
                drawRoundRect(
                    color = color.copy(alpha = 0.85f),
                    topLeft = Offset(x, y),
                    size = Size(barW, h),
                    cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                )
            }
        }
    }
}
