package com.illyism.transcribe.domain

data class WhisperSegment(
    val start: Double,
    val end: Double,
    val text: String
)

data class WhisperResult(
    val text: String,
    val language: String,
    val segments: List<WhisperSegment>
)

enum class PipelineStage {
    IDLE,
    EXTRACTING,
    OPTIMIZING,
    CHUNKING,
    TRANSCRIBING,
    SAVING,
    DONE,
    FAILED
}

data class PipelineProgress(
    val stage: PipelineStage,
    val overallPercent: Int,
    val chunksDone: Int = 0,
    val chunksTotal: Int = 0,
    val videoBytes: Long = 0,
    val audioBytes: Long = 0,
    val message: String = ""
)

data class PipelineResult(
    val srtPath: String,
    val preview: String,
    val language: String,
    val durationSeconds: Double,
    val videoBytes: Long,
    val audioBytes: Long
)
