package com.illyism.transcribe.domain.skills

object BuiltInSkills {
    val repurpose = Skill(
        id = "builtin_repurpose",
        name = "Repurpose",
        description = "Turn a transcript into posts, descriptions, emails, and action items.",
        icon = "sparkles",
        color = "#E8A838",
        prompt = """
Create platform-specific content from this transcript.
For each requested output, write ready-to-publish copy.
Keep tone clear and useful. Do not invent facts not present in the transcript.
""".trimIndent(),
        outputs = listOf(
            SkillOutput("x_thread", "X thread", SkillOutputType.X_THREAD, "Thread of short posts"),
            SkillOutput("linkedin", "LinkedIn post", SkillOutputType.LINKEDIN, "Professional post"),
            SkillOutput("youtube_description", "YouTube description", SkillOutputType.YOUTUBE_DESCRIPTION, "Description with chapters if possible"),
            SkillOutput("newsletter", "Newsletter / Email", SkillOutputType.NEWSLETTER, "Email-ready summary"),
            SkillOutput("action_items", "Action items", SkillOutputType.ACTION_ITEMS, "Checklist of next steps")
        ),
        category = SkillCategory.REPURPOSE,
        builtIn = true,
        estimatedRuntime = "~45s"
    )

    val studyGuide = Skill(
        id = "builtin_study_guide",
        name = "Study Guide",
        description = "Generate notes, a glossary, and a short quiz from the transcript.",
        icon = "graduation-cap",
        color = "#7E57C2",
        prompt = """
Create a study guide from this transcript.
Be accurate and concise. Prefer bullet notes over long paragraphs.
""".trimIndent(),
        outputs = listOf(
            SkillOutput("summary", "Summary notes", SkillOutputType.MARKDOWN, "Key takeaways"),
            SkillOutput("glossary", "Glossary", SkillOutputType.MARKDOWN, "Terms and definitions"),
            SkillOutput("quiz", "Quiz", SkillOutputType.QUIZ, "5 short questions with answers")
        ),
        category = SkillCategory.STUDY,
        builtIn = true,
        estimatedRuntime = "~40s"
    )

    val findHighlights = Skill(
        id = "builtin_highlights",
        name = "Find Highlights",
        description = "Find quotable moments with timestamps and suggested hooks.",
        icon = "lightbulb",
        color = "#42A5F5",
        prompt = """
Find the most interesting, quotable, or shareable moments in this transcript.
Include timestamps when available. Suggest short hooks suitable for clips or posts.
""".trimIndent(),
        outputs = listOf(
            SkillOutput("highlights", "Highlights", SkillOutputType.TIMESTAMP_LIST, "Timestamped moments"),
            SkillOutput("hooks", "Suggested hooks", SkillOutputType.MARKDOWN, "Short clip/post hooks")
        ),
        category = SkillCategory.HIGHLIGHTS,
        builtIn = true,
        estimatedRuntime = "~30s"
    )

    val askAi = Skill(
        id = "builtin_ask_ai",
        name = "Ask AI",
        description = "Ask a custom question about this transcript.",
        icon = "message",
        color = "#66BB6A",
        prompt = """
Answer the user's question using only the transcript.
If the answer is not in the transcript, say so clearly.
""".trimIndent(),
        outputs = listOf(
            SkillOutput("answer", "Answer", SkillOutputType.MARKDOWN, "Response to the custom prompt")
        ),
        category = SkillCategory.ASK,
        builtIn = true,
        estimatedRuntime = "~20s"
    )

    val all: List<Skill> = listOf(repurpose, studyGuide, findHighlights, askAi)

    fun byId(id: String): Skill? = all.find { it.id == id }
}
