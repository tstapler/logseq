package com.logseq.kmp.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

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

/**
 * Parses markdown and applies proper styling and annotations
 */
fun parseMarkdownWithStyling(
    text: String,
    linkColor: Color,
    textColor: Color,
    resolvedRefs: Map<String, String> = emptyMap()
): AnnotatedString {
    // 1. Pre-process text to resolve block refs (expand them)
    val sb = StringBuilder()
    var lastIndex = 0
    val refMap = mutableMapOf<IntRange, String>() // Map range in SB to UUID (for styling)
    
    MarkdownPatterns.blockRefPattern.findAll(text).forEach { match ->
        // Append text before match
        sb.append(text.substring(lastIndex, match.range.first))
        
        val refUuid = match.groupValues[1]
        val content = resolvedRefs[refUuid] ?: "((...))" // Show placeholder if not resolved
        val start = sb.length
        sb.append(content)
        val end = sb.length
        
        refMap[start until end] = refUuid
        lastIndex = match.range.last + 1
    }
    sb.append(text.substring(lastIndex))
    val newText = sb.toString()

    // 2. Build AnnotatedString from newText
    val builder = AnnotatedString.Builder(newText)
    val textLength = newText.length

    // Apply base text color
    if (textColor != Color.Unspecified) {
        builder.addStyle(SpanStyle(color = textColor), 0, textLength)
    }
    
    // Apply styling for Block Refs
    refMap.forEach { (range, uuid) ->
        builder.addStringAnnotation(BLOCK_REF_TAG, uuid, range.first, range.last + 1)
        builder.addStyle(
            SpanStyle(
                color = linkColor,
                textDecoration = TextDecoration.Underline,
                fontStyle = FontStyle.Italic,
                background = Color.Gray.copy(alpha = 0.05f)
            ),
            range.first, 
            range.last + 1
        )
    }

    // Helper for safe bounds
    fun safeRange(start: Int, end: Int): Pair<Int, Int> {
        val safeStart = start.coerceIn(0, textLength)
        val safeEnd = end.coerceIn(safeStart, textLength)
        return safeStart to safeEnd
    }

    // Apply styling for images
    MarkdownPatterns.imagePattern.findAll(newText).forEach { match ->
        val imageUrl = match.groupValues[2]
        val (safeStart, safeEnd) = safeRange(match.range.first, match.range.last + 1)
        if (safeStart < safeEnd) {
            builder.addStringAnnotation("image", imageUrl, safeStart, safeEnd)
            builder.addStyle(
                SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                safeStart, safeEnd
            )
        }
    }

    // Apply styling for links
    val markdownLinkRanges = mutableListOf<IntRange>()
    MarkdownPatterns.linkPattern.findAll(newText).forEach { match ->
        val linkUrl = match.groupValues[2]
        val (safeStart, safeEnd) = safeRange(match.range.first, match.range.last + 1)
        if (safeStart < safeEnd) {
            markdownLinkRanges.add(match.range)
            builder.addStringAnnotation("link", linkUrl, safeStart, safeEnd)
            builder.addStyle(
                SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                safeStart, safeEnd
            )
        }
    }

    // Apply styling for plain URLs
    MarkdownPatterns.urlPattern.findAll(newText).forEach { match ->
        val (safeStart, safeEnd) = safeRange(match.range.first, match.range.last + 1)
        val isInside = markdownLinkRanges.any { it.contains(safeStart) }
        if (!isInside && safeStart < safeEnd) {
            builder.addStringAnnotation("url", match.value, safeStart, safeEnd)
            builder.addStyle(
                SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                safeStart, safeEnd
            )
        }
    }
    
    // Apply styling for code
    MarkdownPatterns.codePattern.findAll(newText).forEach { match ->
        val (safeStart, safeEnd) = safeRange(match.range.first, match.range.last + 1)
        if (safeStart < safeEnd) {
            builder.addStyle(
                SpanStyle(fontFamily = FontFamily.Monospace, background = Color.Gray.copy(alpha = 0.1f)),
                safeStart, safeEnd
            )
        }
    }
    
    // Apply styling for strikethrough
    MarkdownPatterns.strikethroughPattern.findAll(newText).forEach { match ->
        val (safeStart, safeEnd) = safeRange(match.range.first, match.range.last + 1)
        if (safeStart < safeEnd) {
            builder.addStyle(
                SpanStyle(textDecoration = TextDecoration.LineThrough),
                safeStart, safeEnd
            )
        }
    }
    
    // Apply styling for bold
    MarkdownPatterns.boldPattern.findAll(newText).forEach { match ->
        val (safeStart, safeEnd) = safeRange(match.range.first, match.range.last + 1)
        if (safeStart < safeEnd) {
            builder.addStyle(
                SpanStyle(fontWeight = FontWeight.Bold),
                safeStart, safeEnd
            )
        }
    }
    
    // Apply styling for italic
    MarkdownPatterns.italicPattern.findAll(newText).forEach { match ->
        val (safeStart, safeEnd) = safeRange(match.range.first, match.range.last + 1)
        if (safeStart < safeEnd) {
            builder.addStyle(
                SpanStyle(fontStyle = FontStyle.Italic),
                safeStart, safeEnd
            )
        }
    }
    
    // Apply styling for wiki links
    MarkdownPatterns.wikiLinkPattern.findAll(newText).forEach { match ->
        val linkText = match.groupValues[1]
        val (safeStart, safeEnd) = safeRange(match.range.first, match.range.last + 1)
        if (safeStart < safeEnd) {
            builder.addStringAnnotation(WIKI_LINK_TAG, linkText, safeStart, safeEnd)
            builder.addStyle(
                SpanStyle(color = linkColor, fontWeight = FontWeight.Medium),
                safeStart, safeEnd
            )
        }
    }

    // Apply styling for tags
    MarkdownPatterns.tagPattern.findAll(newText).forEach { match ->
        val tagName = match.groupValues[1]
        // Skip if inside a wiki link range (simple collision check)
        val isInsideLink = MarkdownPatterns.wikiLinkPattern.findAll(newText).any { linkMatch ->
            match.range.first >= linkMatch.range.first && match.range.last <= linkMatch.range.last
        }
        
        if (!isInsideLink) {
            val (safeStart, safeEnd) = safeRange(match.range.first, match.range.last + 1)
            if (safeStart < safeEnd) {
                builder.addStringAnnotation(TAG_TAG, tagName, safeStart, safeEnd)
                builder.addStyle(
                    SpanStyle(color = linkColor, fontWeight = FontWeight.Medium),
                    safeStart, safeEnd
                )
            }
        }
    }

    return builder.toAnnotatedString()
}
