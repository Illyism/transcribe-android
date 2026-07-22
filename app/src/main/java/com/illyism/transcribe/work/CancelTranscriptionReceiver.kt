package com.illyism.transcribe.work

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.illyism.transcribe.TranscribeApp

class CancelTranscriptionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_JOB_ID) ?: return
        val app = context.applicationContext as? TranscribeApp ?: return
        app.historyStore.requestCancel(id)
    }

    companion object {
        private const val EXTRA_JOB_ID = "job_id"

        fun pendingIntent(context: Context, jobId: String): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                jobId.hashCode(),
                Intent(context, CancelTranscriptionReceiver::class.java)
                    .putExtra(EXTRA_JOB_ID, jobId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
    }
}
