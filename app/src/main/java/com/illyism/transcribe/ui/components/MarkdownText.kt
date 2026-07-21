package com.illyism.transcribe.ui.components

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

private val NumberedListRegex = Regex("""^(\d+)\.\s+(.*)$""")
private val InlineMarkdownRegex = Regex(
    """(\*\*(.+?)\*\*)|(__(.+?)__)|(\*(.+?)\*)|(_(.+?)_)|(`(.+?)`)|(\[(.+?)]\((.+?)\))"""
)
private val TaskListPrefixRegex = Regex("""^\[[ xX]?]\s*""")

/**
 * Lightweight markdown for skill reasoning / result bodies.
 * Supports headings, **bold**, *italic*, `code`, bullets, numbered lists.
 * Copy/share should always use the original markdown source, not this render.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    style: TextStyle = MaterialTheme.typography.bodyMedium
) {
    val codeBackground = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val annotated = remember(markdown, codeBackground) {
        parseMarkdown(markdown, codeBackground)
    }
    SelectionContainer {
        Text(
            text = annotated,
            modifier = modifier,
            style = style.copy(color = color)
        )
    }
}

private fun parseMarkdown(
    source: String,
    codeBackground: Color
): AnnotatedString = buildAnnotatedString {
    val lines = source.replace("\r\n", "\n").lines()
    lines.forEachIndexed { index, line ->
        val trimmed = line.trimStart()
        val heading = headingLevel(trimmed)
        when {
            trimmed.isEmpty() -> Unit
            heading != null -> {
                val (level, title) = heading
                withStyle(
                    SpanStyle(
                        fontWeight = if (level == 1) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = headingSize(level)
                    )
                ) {
                    appendInlineMarkdown(title, codeBackground)
                }
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                append("• ")
                val item = TaskListPrefixRegex.replace(trimmed.drop(2).trimStart(), "")
                appendInlineMarkdown(item, codeBackground)
            }
            else -> {
                val numbered = NumberedListRegex.matchEntire(trimmed)
                if (numbered != null) {
                    append("${numbered.groupValues[1]}. ")
                    appendInlineMarkdown(numbered.groupValues[2], codeBackground)
                } else {
                    appendInlineMarkdown(line, codeBackground)
                }
            }
        }
        if (index < lines.lastIndex) append("\n")
    }
}

private fun headingLevel(trimmed: String): Pair<Int, String>? = when {
    trimmed.startsWith("### ") -> 3 to trimmed.removePrefix("### ").trim()
    trimmed.startsWith("## ") -> 2 to trimmed.removePrefix("## ").trim()
    trimmed.startsWith("# ") -> 1 to trimmed.removePrefix("# ").trim()
    else -> null
}

private fun headingSize(level: Int): TextUnit = when (level) {
    1 -> 18.sp
    2 -> 16.sp
    else -> 15.sp
}

private fun AnnotatedString.Builder.appendInlineMarkdown(
    text: String,
    codeBackground: Color
) {
    var index = 0
    for (match in InlineMarkdownRegex.findAll(text)) {
        if (match.range.first > index) {
            append(text.substring(index, match.range.first))
        }
        val bold = match.groups[2]?.value ?: match.groups[4]?.value
        val italic = match.groups[6]?.value ?: match.groups[8]?.value
        val code = match.groups[10]?.value
        val linkLabel = match.groups[12]?.value
        when {
            bold != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }
            italic != null -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(italic) }
            code != null -> withStyle(
                SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)
            ) { append(code) }
            linkLabel != null -> append(linkLabel)
        }
        index = match.range.last + 1
    }
    if (index < text.length) append(text.substring(index))
}
