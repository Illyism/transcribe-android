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

    var whisperUsdPerMinute: Double
        get() = java.lang.Double.longBitsToDouble(
            prefs.getLong(
                KEY_WHISPER_PRICE,
                java.lang.Double.doubleToRawLongBits(DEFAULT_WHISPER_USD_PER_MINUTE)
            )
        )
        set(value) = prefs.edit().putLong(
            KEY_WHISPER_PRICE,
            java.lang.Double.doubleToRawLongBits(value.coerceAtLeast(0.0))
        ).apply()

    val totalUploadAvoidedBytes: Long
        get() = prefs.getLong(KEY_UPLOAD_AVOIDED, 0L)

    val totalPreparedAudioBytes: Long
        get() = prefs.getLong(KEY_PREPARED_AUDIO, 0L)

    val videosProcessedCount: Int
        get() = prefs.getInt(KEY_VIDEOS_PROCESSED, 0)

    @Synchronized
    fun recordCompletedJob(sourceBytes: Long, preparedAudioBytes: Long) {
        if (sourceBytes <= 0L || preparedAudioBytes <= 0L) return
        val avoided = (sourceBytes - preparedAudioBytes).coerceAtLeast(0L)
        prefs.edit()
            .putLong(KEY_UPLOAD_AVOIDED, totalUploadAvoidedBytes + avoided)
            .putLong(KEY_PREPARED_AUDIO, totalPreparedAudioBytes + preparedAudioBytes)
            .putInt(KEY_VIDEOS_PROCESSED, videosProcessedCount + 1)
            .apply()
    }

    /** Skills chat model + reasoning preset. */
    var skillModelTier: SkillModelTier
        get() = SkillModelTier.fromStorage(
            prefs.getString(KEY_SKILL_MODEL, SkillModelTier.TERRA_LIGHT.name)
                ?: SkillModelTier.TERRA_LIGHT.name
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
        private const val KEY_WHISPER_PRICE = "whisper_usd_per_minute"
        private const val KEY_UPLOAD_AVOIDED = "total_upload_avoided_bytes"
        private const val KEY_PREPARED_AUDIO = "total_prepared_audio_bytes"
        private const val KEY_VIDEOS_PROCESSED = "videos_processed_count"

        const val DEFAULT_CHUNK_MINUTES = 20
        const val DEFAULT_PARALLEL = 4
        const val DEFAULT_MODEL = "whisper-1"
        const val DEFAULT_WHISPER_USD_PER_MINUTE = 0.006
    }
}

/**
 * Friendly GPT-5.6 model + reasoning presets for Skills (Responses API).
 * See https://developers.openai.com/api/docs/models,
 * https://developers.openai.com/api/docs/guides/reasoning, and
 * https://developers.openai.com/api/docs/pricing.
 * Transcription stays whisper-1.
 */
enum class SkillModelTier(
    val label: String,
    val modelId: String,
    val subtitle: String,
    /** Short quality chip shown in the model picker pill (e.g. Extra High). */
    val qualityLabel: String,
    val reasoningEffort: String,
    /** Relative cost meter 1–5 (higher = more expensive). */
    val relativeCost: Int,
    val speed: String
) {
    TERRA_LIGHT(
        label = "5.6 Terra",
        modelId = "gpt-5.6-terra",
        subtitle = "Balanced model, light reasoning",
        qualityLabel = "Light",
        reasoningEffort = "low",
        relativeCost = 1,
        speed = "Fastest"
    ),
    SOL_LIGHT(
        label = "5.6 Sol",
        modelId = "gpt-5.6-sol",
        subtitle = "Frontier model, light reasoning",
        qualityLabel = "Light",
        reasoningEffort = "low",
        relativeCost = 2,
        speed = "Fast"
    ),
    SOL_MEDIUM(
        label = "5.6 Sol",
        modelId = "gpt-5.6-sol",
        subtitle = "Frontier model, balanced reasoning",
        qualityLabel = "Medium",
        reasoningEffort = "medium",
        relativeCost = 3,
        speed = "Balanced"
    ),
    SOL_HIGH(
        label = "5.6 Sol",
        modelId = "gpt-5.6-sol",
        subtitle = "Frontier model, deep reasoning",
        qualityLabel = "High",
        reasoningEffort = "high",
        relativeCost = 4,
        speed = "Smart"
    ),
    SOL_EXTRA_HIGH(
        label = "5.6 Sol",
        modelId = "gpt-5.6-sol",
        subtitle = "Frontier model, extra deep reasoning",
        qualityLabel = "Extra High",
        reasoningEffort = "xhigh",
        relativeCost = 5,
        speed = "Smartest"
    );

    /** e.g. `$`, `$$`, … for UI meters. */
    val costMeter: String get() = "$".repeat(relativeCost.coerceIn(1, 5))

    companion object {
        fun fromStorage(value: String): SkillModelTier {
            entries.find { it.name.equals(value, ignoreCase = true) }?.let { return it }
            return when (value.uppercase()) {
                "LUNA", "TERRA" -> TERRA_LIGHT
                "SOL" -> SOL_EXTRA_HIGH
                else -> TERRA_LIGHT
            }
        }

        fun fromSlider(value: Float): SkillModelTier {
            val idx = kotlin.math.round(value).toInt().coerceIn(0, entries.lastIndex)
            return entries[idx]
        }
    }

    val sliderValue: Float get() = ordinal.toFloat()
}
