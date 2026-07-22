package com.illyism.transcribe.ui.nav

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

/**
 * Incoming entry points:
 * - Custom-scheme deep links (not web-shareable)
 * - System share / open-with for video & audio
 *
 * Deep links:
 * - `transcribe://transcript/{id}` → [AppKey.TranscriptDetail]
 * - `transcribe://skill/{transcriptId}/{skillId}` → [AppKey.SkillResults]
 *
 * Share / view:
 * - [Intent.ACTION_SEND] / [Intent.ACTION_SEND_MULTIPLE] with `EXTRA_STREAM`
 * - [Intent.ACTION_VIEW] with a content/file URI
 */
object DeepLinks {
    const val SCHEME = "transcribe"
    const val HOST_TRANSCRIPT = "transcript"
    const val HOST_SKILL = "skill"

    sealed interface Target {
        data class Transcript(val transcriptId: String) : Target
        data class SkillResult(val transcriptId: String, val skillId: String) : Target
    }

    sealed interface Incoming {
        data class DeepLink(val target: Target) : Incoming
        data class SharedMedia(val uri: Uri) : Incoming
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

    /**
     * Resolve a launch [Intent] into either a deep link or shared media URI.
     * Deep links win when [Intent.getData] is a `transcribe://` URI.
     */
    fun parseIncoming(intent: Intent?): Incoming? {
        if (intent == null) return null
        parse(intent.data)?.let { return Incoming.DeepLink(it) }
        sharedMediaUri(intent)?.let { return Incoming.SharedMedia(it) }
        return null
    }

    /**
     * First video/audio URI from a share or view intent.
     * Does not follow nested Intent extras (redirection-safe).
     */
    fun sharedMediaUri(intent: Intent): Uri? {
        val action = intent.action ?: return null
        val uri = when (action) {
            Intent.ACTION_SEND -> singleStreamUri(intent)
            Intent.ACTION_SEND_MULTIPLE -> firstStreamUri(intent)
            Intent.ACTION_VIEW -> intent.data
            else -> null
        } ?: return null
        return uri.takeIf { isReadableMediaUri(it) && isSupportedMediaType(intent, it) }
    }

    private fun singleStreamUri(intent: Intent): Uri? {
        IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            ?.let { return it }
        return intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
    }

    private fun firstStreamUri(intent: Intent): Uri? {
        IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            ?.firstOrNull()
            ?.let { return it }
        val clip = intent.clipData ?: return null
        for (i in 0 until clip.itemCount) {
            clip.getItemAt(i).uri?.let { return it }
        }
        return null
    }

    private fun isReadableMediaUri(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase() ?: return false
        return scheme == "content" || scheme == "file"
    }

    private fun isSupportedMediaType(intent: Intent, uri: Uri): Boolean {
        val declared = intent.type?.substringBefore(';')?.trim()?.lowercase()
        if (isVideoOrAudioMime(declared)) return true
        // SEND often declares video/* / audio/*; VIEW may omit type — accept safe schemes
        // and let UriMediaAccess / FFmpeg reject unreadable payloads.
        if (declared.isNullOrBlank() || declared == "*/*") return true
        // Reject clearly non-media shares (e.g. text/plain with a stream).
        return false
    }

    private fun isVideoOrAudioMime(mime: String?): Boolean {
        if (mime.isNullOrBlank()) return false
        return mime.startsWith("video/") || mime.startsWith("audio/")
    }
}
