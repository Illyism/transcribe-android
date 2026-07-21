package com.illyism.transcribe.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SettingsRepository(context: Context) {
    private val appContext = context.applicationContext

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            "transcribe_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    var apiKey: String
        get() = prefs.getString(KEY_API, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API, value.trim()).apply()

    var chunkMinutes: Int
        get() = prefs.getInt(KEY_CHUNK_MINUTES, DEFAULT_CHUNK_MINUTES)
        set(value) = prefs.edit().putInt(KEY_CHUNK_MINUTES, value.coerceIn(5, 60)).apply()

    var maxParallelUploads: Int
        get() = prefs.getInt(KEY_PARALLEL, DEFAULT_PARALLEL)
        set(value) = prefs.edit().putInt(KEY_PARALLEL, value.coerceIn(1, 8)).apply()

    var model: String
        get() = prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(value) = prefs.edit().putString(KEY_MODEL, value).apply()

    var rawMode: Boolean
        get() = prefs.getBoolean(KEY_RAW, false)
        set(value) = prefs.edit().putBoolean(KEY_RAW, value).apply()

    fun clearApiKey() {
        apiKey = ""
    }

    fun hasApiKey(): Boolean = apiKey.isNotBlank()

    companion object {
        private const val KEY_API = "api_key"
        private const val KEY_CHUNK_MINUTES = "chunk_minutes"
        private const val KEY_PARALLEL = "max_parallel"
        private const val KEY_MODEL = "model"
        private const val KEY_RAW = "raw_mode"

        const val DEFAULT_CHUNK_MINUTES = 20
        const val DEFAULT_PARALLEL = 4
        const val DEFAULT_MODEL = "whisper-1"
    }
}
