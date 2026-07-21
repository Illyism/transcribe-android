package com.illyism.transcribe.domain

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/** Best-effort video frame → JPEG for History rows. */
object VideoThumbnailExtractor {
    private const val MAX_EDGE_PX = 256

    fun extract(context: Context, videoUri: Uri, destFile: File): Boolean {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, videoUri)
            val frame = retriever.getFrameAtTime(
                1_000_000L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: retriever.frameAtTime ?: return false
            val scaled = scaleDown(frame, MAX_EDGE_PX)
            if (scaled !== frame) frame.recycle()
            destFile.parentFile?.mkdirs()
            FileOutputStream(destFile).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            if (!scaled.isRecycled) scaled.recycle()
            destFile.exists() && destFile.length() > 0L
        } catch (_: Exception) {
            false
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun scaleDown(source: Bitmap, maxEdge: Int): Bitmap {
        val w = source.width
        val h = source.height
        if (w <= maxEdge && h <= maxEdge) return source
        val scale = maxEdge.toFloat() / maxOf(w, h)
        val nw = (w * scale).toInt().coerceAtLeast(1)
        val nh = (h * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, nw, nh, true)
    }
}
