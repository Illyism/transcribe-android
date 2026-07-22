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

/** Durable execution state. Pipeline position lives separately in [JobStage]. */
enum class JobState {
    QUEUED,
    RUNNING,
    WAITING_FOR_KEY,
    RETRYING,
    NEEDS_ATTENTION,
    CANCELLING,
    COMPLETED,
    CANCELLED,
    FAILED
}

enum class JobStage {
    EXTRACTING_AUDIO,
    OPTIMIZING_AUDIO,
    PREPARING_CHUNKS,
    UPLOADING_CHUNKS,
    SAVING,
    ENRICHING
}

enum class TranscribeErrorKind {
    NETWORK,
    AUTH,
    RATE_LIMIT,
    QUOTA,
    SERVER,
    SOURCE_UNAVAILABLE,
    UNSUPPORTED_MEDIA,
    TOO_LARGE,
    STORAGE,
    GENERIC
}

enum class ErrorScope { JOB, QUEUE }

object JobStateMachine {
    private val allowed = mapOf(
        JobState.QUEUED to setOf(JobState.RUNNING, JobState.CANCELLED),
        JobState.RUNNING to setOf(
            JobState.QUEUED,
            JobState.WAITING_FOR_KEY,
            JobState.RETRYING,
            JobState.NEEDS_ATTENTION,
            JobState.CANCELLING,
            JobState.COMPLETED,
            JobState.FAILED
        ),
        JobState.WAITING_FOR_KEY to setOf(
            JobState.RUNNING,
            JobState.QUEUED,
            JobState.CANCELLED
        ),
        JobState.RETRYING to setOf(
            JobState.RUNNING,
            JobState.QUEUED,
            JobState.NEEDS_ATTENTION,
            JobState.CANCELLING,
            JobState.FAILED
        ),
        JobState.NEEDS_ATTENTION to setOf(JobState.QUEUED, JobState.CANCELLED),
        JobState.CANCELLING to setOf(JobState.CANCELLED),
        JobState.FAILED to setOf(JobState.QUEUED),
        JobState.CANCELLED to setOf(JobState.QUEUED),
        JobState.COMPLETED to emptySet()
    )

    fun canTransition(from: JobState, to: JobState): Boolean =
        from == to || to in allowed.getValue(from)

    fun requireTransition(from: JobState, to: JobState) {
        require(canTransition(from, to)) { "Illegal job transition: $from → $to" }
    }
}

class TranscribeException(
    val kind: TranscribeErrorKind,
    val scope: ErrorScope,
    override val message: String,
    cause: Throwable? = null
) : Exception(message, cause)

class JobCancelledException : Exception("Transcription cancelled")

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
