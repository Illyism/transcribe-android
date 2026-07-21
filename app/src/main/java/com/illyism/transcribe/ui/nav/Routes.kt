package com.illyism.transcribe.ui.nav

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Type-safe Navigation 3 keys. Top-level tabs each own a back stack;
 * id-carrying keys load their entity from HistoryStore / SkillRepository.
 */
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
    data class SkillPicker(val transcriptId: String) : AppKey

    @Serializable
    data class SkillRun(val transcriptId: String, val skillId: String) : AppKey

    @Serializable
    data class SkillResults(val transcriptId: String, val skillId: String) : AppKey

    @Serializable
    data class SkillEditor(val skillId: String? = null) : AppKey
}

val TOP_LEVEL_KEYS: Set<NavKey> = setOf(AppKey.Home, AppKey.History, AppKey.Skills)
