package com.illyism.transcribe.domain.skills

import com.illyism.transcribe.data.SkillModelTier

/**
 * Runs the hidden Catalog skill via [SkillRunner] and returns title + summary.
 * Best-effort: returns null on blank/failed output.
 */
object CatalogEnricher {
    const val MAX_TITLE_CHARS = 60

    fun run(
        runner: SkillRunner,
        context: SkillRunContext,
        apiKey: String,
        tier: SkillModelTier = SkillModelTier.TERRA_LIGHT
    ): Pair<String, String>? {
        if (apiKey.isBlank()) return null
        val result = runner.run(
            skill = BuiltInSkills.catalog,
            context = context,
            apiKey = apiKey,
            model = tier.modelId,
            reasoningEffort = tier.reasoningEffort,
            selectedOutputIds = BuiltInSkills.catalog.outputs.map { it.id }
        )
        val title = result.outputs
            .find { it.outputId == "title" }
            ?.content
            ?.lineSequence()
            ?.firstOrNull()
            ?.trim()
            ?.trim('"', '\'')
            ?.take(MAX_TITLE_CHARS)
            .orEmpty()
        val summary = result.outputs
            .find { it.outputId == "summary" }
            ?.content
            ?.trim()
            ?.lines()
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.take(2)
            ?.joinToString("\n")
            .orEmpty()
        if (title.isBlank() && summary.isBlank()) return null
        return title to summary
    }
}
