package com.illyism.transcribe.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.illyism.transcribe.ui.theme.Amber
import com.illyism.transcribe.ui.theme.SurfaceAlt
import com.illyism.transcribe.ui.theme.TextSecondary
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Amber,
            contentColor = Color(0xFF1A1200),
            disabledContainerColor = Amber.copy(alpha = 0.35f),
            disabledContentColor = Color(0xFF1A1200).copy(alpha = 0.5f)
        )
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun LinkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text(text, color = Amber, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun FileChip(
    name: String,
    meta: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceAlt)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Amber.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.InsertDriveFile, contentDescription = null, tint = Amber)
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 2)
            Spacer(modifier = Modifier.height(4.dp))
            Text(meta, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun InfoBanner(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.WarningAmber,
    tint: Color = Amber
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(tint.copy(alpha = 0.12f))
            .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
fun ProgressBar(percent: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            progress = { (percent.coerceIn(0, 100) / 100f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(8.dp)),
            color = Amber,
            trackColor = SurfaceAlt,
            strokeCap = StrokeCap.Round
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text("$percent%", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "—"
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    if (gb >= 1) return String.format(Locale.US, "%.1f GB", gb)
    val mb = bytes / (1024.0 * 1024.0)
    if (mb >= 1) return String.format(Locale.US, "%.1f MB", mb)
    val kb = bytes / 1024.0
    return String.format(Locale.US, "%.0f KB", kb)
}

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "—"
    val hours = TimeUnit.MILLISECONDS.toHours(ms)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return if (hours > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

@Composable
fun StepRow(
    title: String,
    subtitle: String,
    done: Boolean,
    active: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        val color = when {
            done -> Amber
            active -> Amber
            else -> TextSecondary.copy(alpha = 0.5f)
        }
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(color)
        )
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = if (done || active) MaterialTheme.colorScheme.onBackground else TextSecondary
            )
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
