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
import com.illyism.transcribe.ui.components.PrimaryButton
import com.illyism.transcribe.ui.components.ProgressBar
import com.illyism.transcribe.ui.components.SecondaryButton
import com.illyism.transcribe.ui.components.StepRow
import com.illyism.transcribe.ui.components.formatBytes
@Composable
fun ProcessingScreen(
    stage: PipelineStage,
    percent: Int,
    chunksDone: Int,
    chunksTotal: Int,
    videoBytes: Long,
    audioBytes: Long,
    message: String,
    error: String?,
    onRetry: (() -> Unit)? = null,
    onChooseDifferent: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val failed = stage == PipelineStage.FAILED || error != null
    // FAILED is last in the enum — never use its ordinal to mark earlier steps done.
    val progressStage = if (stage == PipelineStage.FAILED) PipelineStage.EXTRACTING else stage

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            if (failed) "Failed" else "Working…",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            if (error != null) error else message.ifBlank { "Preparing…" },
            style = MaterialTheme.typography.bodyLarge,
            color = if (error != null) scheme.error else scheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        val extractDone = progressStage.ordinal > PipelineStage.EXTRACTING.ordinal ||
            progressStage == PipelineStage.DONE
        val optimizeDone = progressStage.ordinal > PipelineStage.OPTIMIZING.ordinal ||
            progressStage == PipelineStage.DONE
        val transcribingActive = progressStage == PipelineStage.TRANSCRIBING ||
            progressStage == PipelineStage.CHUNKING
        val transcribeDone = progressStage == PipelineStage.SAVING || progressStage == PipelineStage.DONE

        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(scheme.surfaceVariant)
                .padding(16.dp)
        ) {
            StepRow(
                title = "Extracting audio",
                subtitle = when {
                    failed && !extractDone -> "Failed"
                    extractDone -> "Completed"
                    else -> "In progress"
                },
                done = extractDone && progressStage != PipelineStage.EXTRACTING,
                active = !failed && progressStage == PipelineStage.EXTRACTING
            )
            Spacer(modifier = Modifier.height(16.dp))
            StepRow(
                title = "Optimizing",
                subtitle = when {
                    failed && progressStage.ordinal < PipelineStage.OPTIMIZING.ordinal -> "Waiting"
                    failed && progressStage == PipelineStage.OPTIMIZING -> "Failed"
                    progressStage.ordinal < PipelineStage.OPTIMIZING.ordinal -> "Waiting"
                    optimizeDone && progressStage != PipelineStage.OPTIMIZING -> "Completed"
                    else -> "In progress"
                },
                done = optimizeDone && progressStage != PipelineStage.OPTIMIZING,
                active = !failed && progressStage == PipelineStage.OPTIMIZING
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
                    failed && (transcribingActive || progressStage.ordinal >= PipelineStage.CHUNKING.ordinal) ->
                        "Failed"
                    failed -> "Waiting"
                    transcribeDone -> "Completed"
                    transcribingActive -> "In progress"
                    else -> "Waiting"
                },
                done = transcribeDone,
                active = !failed && transcribingActive
            )
        }

        if (videoBytes > 0 && audioBytes > 0) {
            Spacer(modifier = Modifier.height(16.dp))
            val reduction = ((1.0 - audioBytes.toDouble() / videoBytes.toDouble()) * 100).coerceIn(0.0, 99.9)
            InfoBanner(
                text = "${formatBytes(videoBytes)} video → ${formatBytes(audioBytes)} audio " +
                    "(${String.format("%.1f", reduction)}% smaller before upload).",
                tint = scheme.primary
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        if (failed) {
            if (onRetry != null) {
                PrimaryButton("Retry", onClick = onRetry)
                Spacer(modifier = Modifier.height(12.dp))
            }
            if (onChooseDifferent != null) {
                SecondaryButton("Choose different video", onClick = onChooseDifferent)
            }
        } else {
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
}
