package com.illyism.transcribe

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.illyism.transcribe.data.SettingsRepository
import com.illyism.transcribe.data.TranscribeSessionStore

class TranscribeApp : Application() {
    lateinit var settings: SettingsRepository
        private set
    lateinit var sessionStore: TranscribeSessionStore
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(this)
        sessionStore = TranscribeSessionStore(this)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "transcribe_progress"
    }
}
