package com.illyism.transcribe.domain

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.arthenica.ffmpegkit.FFmpegKitConfig
import java.io.File

data class VideoMeta(
    val displayName: String,
    val sizeBytes: Long,
    val durationMs: Long
)

object UriMediaAccess {
    fun readMeta(context: Context, uri: Uri): VideoMeta {
        var name = "video"
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: name
                if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
            }
        }

        var durationMs = 0L
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L
        } catch (_: Exception) {
            // Duration optional for UI; FFmpeg will probe later.
        } finally {
            runCatching { retriever.release() }
        }

        return VideoMeta(displayName = name, sizeBytes = size, durationMs = durationMs)
    }

    /**
     * Convert a SAF content [Uri] into an FFmpegKit `saf:` path.
     * Do not use `/proc/self/fd/N` — native FFmpeg often gets Permission denied on it.
     * Does not copy the file into app storage.
     */
    fun safReadPath(context: Context, uri: Uri): String {
        return FFmpegKitConfig.getSafParameterForRead(context, uri)
            ?: throw IllegalStateException("Could not open video. Check storage permission.")
    }

    fun workDir(context: Context): File {
        val dir = File(context.cacheDir, "transcribe_work")
        dir.mkdirs()
        return dir
    }

    fun outputDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "transcripts")
        dir.mkdirs()
        return dir
    }
}
