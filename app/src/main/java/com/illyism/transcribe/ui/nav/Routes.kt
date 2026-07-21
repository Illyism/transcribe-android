package com.illyism.transcribe.ui.nav

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Type-safe Nav3 keys; id routes load from HistoryStore / SkillRepository. */
sealed interface AppKey : NavKey {
    @Serializable
    data object Home : AppKey

    @Serializable
    data object History : AppKey

    @Serializable
    data object Skills : AppKey

    @Serializable
    data object Selected : AppKey

    @Serializable
    data object Processing : AppKey

    @Serializable
    data object Settings : AppKey

    @Serializable
    data class TranscriptDetail(val transcriptId: String) : AppKey

    @Serializable
    data class SkillResults(val transcriptId: String, val skillId: String) : AppKey

    @Serializable
    data class SkillEditor(val skillId: String? = null) : AppKey
}

val TOP_LEVEL_KEYS: Set<AppKey> = setOf(AppKey.Home, AppKey.History, AppKey.Skills)
