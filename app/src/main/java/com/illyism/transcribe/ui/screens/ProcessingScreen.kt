package com.illyism.transcribe.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.illyism.transcribe.domain.PipelineStage
import com.illyism.transcribe.ui.components.InfoBanner
import com.illyism.transcribe.ui.components.ProgressBar
import com.illyism.transcribe.ui.components.StepRow
import com.illyism.transcribe.ui.components.formatBytes
import com.illyism.transcribe.ui.theme.Amber
import com.illyism.transcribe.ui.theme.Bg
import com.illyism.transcribe.ui.theme.SurfaceAlt
import com.illyism.transcribe.ui.theme.TextSecondary

@Composable
fun ProcessingScreen(
    stage: PipelineStage,
    percent: Int,
    chunksDone: Int,
    chunksTotal: Int,
    videoBytes: Long,
    audioBytes: Long,
    message: String,
    error: String?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text("Working…", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            if (error != null) error else message.ifBlank { "Preparing…" },
            style = MaterialTheme.typography.bodyLarge,
            color = if (error != null) MaterialTheme.colorScheme.error else TextSecondary
        )
        Spacer(modifier = Modifier.height(24.dp))

        val extractDone = stage.ordinal > PipelineStage.EXTRACTING.ordinal ||
            stage == PipelineStage.DONE
        val optimizeDone = stage.ordinal > PipelineStage.OPTIMIZING.ordinal ||
            stage == PipelineStage.DONE
        val transcribingActive = stage == PipelineStage.TRANSCRIBING || stage == PipelineStage.CHUNKING
        val transcribeDone = stage == PipelineStage.SAVING || stage == PipelineStage.DONE

        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceAlt)
                .padding(16.dp)
        ) {
            StepRow(
                title = "Extracting audio",
                subtitle = if (extractDone) "Completed" else "In progress",
                done = extractDone && stage != PipelineStage.EXTRACTING,
                active = stage == PipelineStage.EXTRACTING
            )
            Spacer(modifier = Modifier.height(16.dp))
            StepRow(
                title = "Optimizing",
                subtitle = when {
                    stage.ordinal < PipelineStage.OPTIMIZING.ordinal -> "Waiting"
                    optimizeDone && stage != PipelineStage.OPTIMIZING -> "Completed"
                    else -> "In progress"
                },
                done = optimizeDone && stage != PipelineStage.OPTIMIZING,
                active = stage == PipelineStage.OPTIMIZING
            )
            Spacer(modifier = Modifier.height(16.dp))
            val chunkLabel = if (chunksTotal > 0) {
                "Transcribing chunks ($chunksDone/$chunksTotal)"
            } else {
                "Transcribing chunks"
            }
            StepRow(
                title = chunkLabel,
                subtitle = when {
                    stage == PipelineStage.FAILED -> "Failed"
                    transcribeDone -> "Completed"
                    transcribingActive -> "In progress"
                    else -> "Waiting"
                },
                done = transcribeDone,
                active = transcribingActive
            )
        }

        if (videoBytes > 0 && audioBytes > 0) {
            Spacer(modifier = Modifier.height(16.dp))
            val reduction = ((1.0 - audioBytes.toDouble() / videoBytes.toDouble()) * 100).coerceIn(0.0, 99.9)
            InfoBanner(
                text = "${formatBytes(videoBytes)} video → ${formatBytes(audioBytes)} audio " +
                    "(${String.format("%.1f", reduction)}% smaller before upload).",
                tint = Amber
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Overall progress", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(10.dp))
        ProgressBar(percent = percent)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "You can leave the app. We’ll keep working in the background.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
