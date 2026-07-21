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

    /** Skills chat model tier: Luna / Terra / Sol. */
    var skillModelTier: SkillModelTier
        get() = SkillModelTier.fromStorage(
            prefs.getString(KEY_SKILL_MODEL, SkillModelTier.TERRA.name) ?: SkillModelTier.TERRA.name
        )
        set(value) = prefs.edit().putString(KEY_SKILL_MODEL, value.name).apply()

    fun skillModelId(): String = skillModelTier.modelId

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
        private const val KEY_SKILL_MODEL = "skill_model_tier"

        const val DEFAULT_CHUNK_MINUTES = 20
        const val DEFAULT_PARALLEL = 4
        const val DEFAULT_MODEL = "whisper-1"
    }
}

/** Friendly tiers for skills chat completions. Transcription stays whisper-1. */
enum class SkillModelTier(
    val label: String,
    val modelId: String,
    val subtitle: String,
    /** Short quality chip shown in the model picker pill (e.g. Extra High). */
    val qualityLabel: String
) {
    LUNA("Luna", "gpt-4o-mini", "Fast & affordable", "Fast"),
    TERRA("Terra", "gpt-4o", "Balanced quality", "High"),
    SOL("Sol", "o3", "Highest quality", "Extra High");

    companion object {
        fun fromStorage(value: String): SkillModelTier =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: TERRA

        fun fromSlider(value: Float): SkillModelTier {
            val idx = kotlin.math.round(value).toInt().coerceIn(0, entries.lastIndex)
            return entries[idx]
        }
    }

    val sliderValue: Float get() = ordinal.toFloat()
}
