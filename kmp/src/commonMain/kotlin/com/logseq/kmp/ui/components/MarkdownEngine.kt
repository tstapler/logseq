package com.logseq.kmp.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

/**
 * Markdown patterns for parsing various markdown syntax elements
 */
object MarkdownPatterns {
    val boldPattern = Regex("""(\*\*|__)(.+?)\1""")
    val italicPattern = Regex("""(?<!\*)(\*|_)(?!\1)(.+?)\1""")
    val codePattern = Regex("""`([^`]+)`""")
    val strikethroughPattern = Regex("""~~(.+?)~~""")
    val linkPattern = Regex("""\[([^\]]+)\]\(([^)]+)\)""")
    val imagePattern = Regex("""!\[([^\]]*)\]\(([^)]+)\)""")
    val wikiLinkPattern = Regex("""\[\[([^\]]+)\]\]""")
    val blockRefPattern = Regex("""\(\(([^)]+)\)\)""")
    val tagPattern = Regex("""#([^\s#.,!\[\]()]+)""")
    // Auto-detect plain URLs - matches http:// or https:// followed by non-whitespace
    val urlPattern = Regex("""https?://[^\s<>"]+""")
}

const val WIKI_LINK_TAG = "WIKI_LINK"
const val BLOCK_REF_TAG = "BLOCK_REF"
const val TAG_TAG = "TAG"

private sealed class InlineToken {
    data class Text(val content: String) : InlineToken()
    data class Bold(val content: String) : InlineToken()
    data class Italic(val content: String) : InlineToken()
    data class Code(val content: String) : InlineToken()
    data class Strike(val content: String) : InlineToken()
    data class WikiLink(val title: String) : InlineToken()
    data class BlockRef(val content: String, val uuid: String) : InlineToken()
    data class Tag(val name: String) : InlineToken()
    data class Url(val url: String) : InlineToken()
    data class MdLink(val text: String, val url: String) : InlineToken()
    data class Image(val alt: String, val url: String) : InlineToken()
}

private data class RawMatch(val start: Int, val end: Int, val token: InlineToken)

/**
 * Tokenizes markdown text into a list of inline tokens.
 * Non-overlapping matches are selected greedily left-to-right.
 */
private fun tokenizeMarkdown(text: String, resolvedRefs: Map<String, String>): List<InlineToken> {
    val matches = mutableListOf<RawMatch>()

    MarkdownPatterns.imagePattern.findAll(text).forEach { m ->
        matches.add(RawMatch(m.range.first, m.range.last + 1, InlineToken.Image(m.groupValues[1], m.groupValues[2])))
    }

    MarkdownPatterns.linkPattern.findAll(text).forEach { m ->
        matches.add(RawMatch(m.range.first, m.range.last + 1, InlineToken.MdLink(m.groupValues[1], m.groupValues[2])))
    }

    MarkdownPatterns.wikiLinkPattern.findAll(text).forEach { m ->
        matches.add(RawMatch(m.range.first, m.range.last + 1, InlineToken.WikiLink(m.groupValues[1])))
    }

    MarkdownPatterns.blockRefPattern.findAll(text).forEach { m ->
        val uuid = m.groupValues[1]
        val content = resolvedRefs[uuid] ?: "((...))"
        matches.add(RawMatch(m.range.first, m.range.last + 1, InlineToken.BlockRef(content, uuid)))
    }

    MarkdownPatterns.boldPattern.findAll(text).forEach { m ->
        matches.add(RawMatch(m.range.first, m.range.last + 1, InlineToken.Bold(m.groupValues[2])))
    }

    MarkdownPatterns.italicPattern.findAll(text).forEach { m ->
        matches.add(RawMatch(m.range.first, m.range.last + 1, InlineToken.Italic(m.groupValues[2])))
    }

    MarkdownPatterns.codePattern.findAll(text).forEach { m ->
        matches.add(RawMatch(m.range.first, m.range.last + 1, InlineToken.Code(m.groupValues[1])))
    }

    MarkdownPatterns.strikethroughPattern.findAll(text).forEach { m ->
        matches.add(RawMatch(m.range.first, m.range.last + 1, InlineToken.Strike(m.groupValues[1])))
    }

    MarkdownPatterns.tagPattern.findAll(text).forEach { m ->
        matches.add(RawMatch(m.range.first, m.range.last + 1, InlineToken.Tag(m.groupValues[1])))
    }

    MarkdownPatterns.urlPattern.findAll(text).forEach { m ->
        matches.add(RawMatch(m.range.first, m.range.last + 1, InlineToken.Url(m.value)))
    }

    // Sort by start position; greedily select non-overlapping matches
    matches.sortBy { it.start }
    val selected = mutableListOf<RawMatch>()
    var pos = 0
    for (m in matches) {
        if (m.start >= pos) {
            selected.add(m)
            pos = m.end
        }
    }

    // Fill gaps between matches with plain text
    val tokens = mutableListOf<InlineToken>()
    var textPos = 0
    for (m in selected) {
        if (m.start > textPos) {
            tokens.add(InlineToken.Text(text.substring(textPos, m.start)))
        }
        tokens.add(m.token)
        textPos = m.end
    }
    if (textPos < text.length) {
        tokens.add(InlineToken.Text(text.substring(textPos)))
    }
    return tokens
}

/**
 * Parses markdown and applies proper styling and annotations.
 * Markdown syntax markers (**, *, ~~, `) are stripped from the output text
 * so that only the rendered style is visible in view mode.
 */
fun parseMarkdownWithStyling(
    text: String,
    linkColor: Color,
    textColor: Color,
    resolvedRefs: Map<String, String> = emptyMap()
): AnnotatedString {
    val tokens = tokenizeMarkdown(text, resolvedRefs)

    return buildAnnotatedString {
        if (textColor != Color.Unspecified) {
            pushStyle(SpanStyle(color = textColor))
        }

        tokens.forEach { token ->
            when (token) {
                is InlineToken.Text -> append(token.content)

                is InlineToken.Bold ->
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(token.content) }

                is InlineToken.Italic ->
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(token.content) }

                is InlineToken.Code ->
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color.Gray.copy(alpha = 0.1f))) {
                        append(token.content)
                    }

                is InlineToken.Strike ->
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(token.content) }

                is InlineToken.WikiLink -> {
                    val start = length
                    withStyle(SpanStyle(color = linkColor, fontWeight = FontWeight.Medium)) {
                        append("[[${token.title}]]")
                    }
                    addStringAnnotation(WIKI_LINK_TAG, token.title, start, length)
                }

                is InlineToken.BlockRef -> {
                    val start = length
                    withStyle(SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline,
                        fontStyle = FontStyle.Italic,
                        background = Color.Gray.copy(alpha = 0.05f)
                    )) {
                        append(token.content)
                    }
                    addStringAnnotation(BLOCK_REF_TAG, token.uuid, start, length)
                }

                is InlineToken.Tag -> {
                    val start = length
                    withStyle(SpanStyle(color = linkColor, fontWeight = FontWeight.Medium)) {
                        append("#${token.name}")
                    }
                    addStringAnnotation(TAG_TAG, token.name, start, length)
                }

                is InlineToken.Url -> {
                    val start = length
                    withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                        append(token.url)
                    }
                    addStringAnnotation("url", token.url, start, length)
                }

                is InlineToken.MdLink -> {
                    val start = length
                    withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                        append(token.text)
                    }
                    addStringAnnotation("link", token.url, start, length)
                }

                is InlineToken.Image -> {
                    val start = length
                    withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                        append(token.alt.ifEmpty { token.url })
                    }
                    addStringAnnotation("image", token.url, start, length)
                }
            }
        }

        if (textColor != Color.Unspecified) {
            pop()
        }
    }
}

/**
 * Applies markdown styling to an existing AnnotatedString.Builder for edit mode.
 * In edit mode, markers remain visible but are styled to give visual hints.
 */
fun applyMarkdownStylingForEditor(
    text: String,
    builder: AnnotatedString.Builder,
    linkColor: Color
) {
    val textLength = text.length

    fun safeRange(start: Int, end: Int): Pair<Int, Int> {
        val safeStart = start.coerceIn(0, textLength)
        val safeEnd = end.coerceIn(safeStart, textLength)
        return safeStart to safeEnd
    }

    MarkdownPatterns.boldPattern.findAll(text).forEach { match ->
        val (s, e) = safeRange(match.range.first, match.range.last + 1)
        if (s < e) builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold), s, e)
    }

    MarkdownPatterns.italicPattern.findAll(text).forEach { match ->
        val (s, e) = safeRange(match.range.first, match.range.last + 1)
        if (s < e) builder.addStyle(SpanStyle(fontStyle = FontStyle.Italic), s, e)
    }

    MarkdownPatterns.codePattern.findAll(text).forEach { match ->
        val (s, e) = safeRange(match.range.first, match.range.last + 1)
        if (s < e) builder.addStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color.Gray.copy(alpha = 0.1f)), s, e)
    }

    MarkdownPatterns.strikethroughPattern.findAll(text).forEach { match ->
        val (s, e) = safeRange(match.range.first, match.range.last + 1)
        if (s < e) builder.addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), s, e)
    }

    MarkdownPatterns.wikiLinkPattern.findAll(text).forEach { match ->
        val linkText = match.groupValues[1]
        val (s, e) = safeRange(match.range.first, match.range.last + 1)
        if (s < e) {
            builder.addStringAnnotation(WIKI_LINK_TAG, linkText, s, e)
            builder.addStyle(SpanStyle(color = linkColor, fontWeight = FontWeight.Medium), s, e)
        }
    }

    MarkdownPatterns.tagPattern.findAll(text).forEach { match ->
        val tagName = match.groupValues[1]
        val isInsideLink = MarkdownPatterns.wikiLinkPattern.findAll(text).any { lm ->
            match.range.first >= lm.range.first && match.range.last <= lm.range.last
        }
        if (!isInsideLink) {
            val (s, e) = safeRange(match.range.first, match.range.last + 1)
            if (s < e) {
                builder.addStringAnnotation(TAG_TAG, tagName, s, e)
                builder.addStyle(SpanStyle(color = linkColor, fontWeight = FontWeight.Medium), s, e)
            }
        }
    }
}
