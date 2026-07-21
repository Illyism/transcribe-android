package com.illyism.transcribe.ui.nav

import android.net.Uri

/**
 * Custom-scheme deep links for opening this app (not web-shareable).
 *
 * - `transcribe://transcript/{id}` → [AppKey.TranscriptDetail]
 * - `transcribe://skill/{transcriptId}/{skillId}` → [AppKey.SkillResults]
 */
object DeepLinks {
    const val SCHEME = "transcribe"
    const val HOST_TRANSCRIPT = "transcript"
    const val HOST_SKILL = "skill"

    sealed interface Target {
        data class Transcript(val transcriptId: String) : Target
        data class SkillResult(val transcriptId: String, val skillId: String) : Target
    }

    fun transcriptUri(transcriptId: String): String =
        "$SCHEME://$HOST_TRANSCRIPT/$transcriptId"

    fun skillUri(transcriptId: String, skillId: String): String =
        "$SCHEME://$HOST_SKILL/$transcriptId/$skillId"

    fun parse(uri: Uri?): Target? {
        if (uri == null || uri.scheme != SCHEME) return null
        val segments = uri.pathSegments.filter { it.isNotBlank() }
        return when (uri.host) {
            HOST_TRANSCRIPT -> {
                val id = segments.firstOrNull() ?: return null
                Target.Transcript(id)
            }
            HOST_SKILL -> {
                val transcriptId = segments.getOrNull(0) ?: return null
                val skillId = segments.getOrNull(1) ?: return null
                Target.SkillResult(transcriptId, skillId)
            }
            else -> null
        }
    }
}
