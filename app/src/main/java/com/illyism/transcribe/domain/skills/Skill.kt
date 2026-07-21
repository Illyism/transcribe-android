package com.illyism.transcribe.domain.skills

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.Message
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.School
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

enum class SkillInput {
    TRANSCRIPT,
    SRT,
    METADATA
}

enum class SkillOutputType {
    TEXT,
    MARKDOWN,
    JSON,
    ACTION_ITEMS,
    CHAPTERS,
    FLASHCARDS,
    QUIZ,
    TIMESTAMP_LIST,
    X_THREAD,
    LINKEDIN,
    YOUTUBE_DESCRIPTION,
    NEWSLETTER
}

data class SkillOutput(
    val id: String,
    val label: String,
    val type: SkillOutputType,
    val hint: String = ""
)

enum class ExportTarget {
    COPY,
    SHARE,
    MARKDOWN,
    PDF
}

enum class SkillCategory {
    REPURPOSE,
    STUDY,
    HIGHLIGHTS,
    ASK,
    CUSTOM
}

data class Skill(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,
    val color: String = "#E8A838",
    val prompt: String,
    val inputs: List<SkillInput> = listOf(SkillInput.TRANSCRIPT, SkillInput.SRT, SkillInput.METADATA),
    val outputs: List<SkillOutput>,
    val exports: List<ExportTarget> = listOf(ExportTarget.COPY, ExportTarget.SHARE),
    val category: SkillCategory = SkillCategory.CUSTOM,
    val builtIn: Boolean = false,
    val estimatedRuntime: String = "~30s"
)

data class SkillOutputResult(
    val outputId: String,
    val label: String,
    val type: SkillOutputType,
    val content: String
)

data class SkillRunResult(
    val skillId: String,
    val skillName: String,
    val outputs: List<SkillOutputResult>
)

object SkillIcons {
    fun vector(name: String): ImageVector = when (name.lowercase()) {
        "sparkles", "autoawesome" -> Icons.Outlined.AutoAwesome
        "book", "menubook" -> Icons.AutoMirrored.Outlined.MenuBook
        "message", "chat" -> Icons.AutoMirrored.Outlined.Message
        "play" -> Icons.Outlined.PlayArrow
        "lightbulb" -> Icons.Outlined.Lightbulb
        "clipboard", "checklist" -> Icons.Outlined.Checklist
        "rocket", "bolt" -> Icons.Outlined.Bolt
        "graduation-cap", "school" -> Icons.Outlined.School
        "list" -> Icons.AutoMirrored.Outlined.List
        "target", "gps" -> Icons.Outlined.GpsFixed
        else -> Icons.Outlined.AutoAwesome
    }

    fun color(hex: String): Color = try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        Color(0xFFE8A838)
    }

    val allNames = listOf(
        "sparkles", "book", "message", "play", "lightbulb",
        "clipboard", "rocket", "graduation-cap", "list", "target"
    )
}
