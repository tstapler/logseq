package com.logseq.kmp.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Read-only rendered view of a block's content with clickable wiki links,
 * tags, markdown links, and plain URLs.
 */
@Composable
internal fun BlockViewer(
    content: String,
    textColor: Color,
    linkColor: Color,
    resolvedRefs: Map<String, String>,
    onLinkClick: (String) -> Unit,
    onStartEditing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    WikiLinkText(
        text = content,
        textColor = if (textColor != Color.Unspecified) textColor else MaterialTheme.colorScheme.onBackground,
        linkColor = linkColor,
        resolvedRefs = resolvedRefs,
        onLinkClick = onLinkClick,
        onUrlClick = { url ->
            try {
                uriHandler.openUri(url)
            } catch (e: Exception) {
                // Ignore if can't open URL
            }
        },
        onClick = onStartEditing,
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * Renders text with clickable wiki links [[Page Name]], #tags,
 * markdown links, and auto-detected URLs.
 */
@Composable
fun WikiLinkText(
    text: String,
    textColor: Color,
    linkColor: Color,
    modifier: Modifier = Modifier,
    resolvedRefs: Map<String, String> = emptyMap(),
    onLinkClick: (String) -> Unit = {},
    onUrlClick: (String) -> Unit = {},
    onClick: () -> Unit = {},
) {
    val annotatedString = remember(text, linkColor, textColor, resolvedRefs) {
        parseMarkdownWithStyling(text, linkColor, textColor, resolvedRefs)
    }

    ClickableText(
        text = annotatedString,
        onClick = { offset ->
            val annotations = annotatedString.getStringAnnotations(start = offset, end = offset)

            // Priority: Wiki Link > Tag > Markdown Link > URL > Image > Default
            val wikiLink = annotations.firstOrNull { it.tag == WIKI_LINK_TAG }
            val tag = annotations.firstOrNull { it.tag == TAG_TAG }
            val link = annotations.firstOrNull { it.tag == "link" }
            val url = annotations.firstOrNull { it.tag == "url" }
            val image = annotations.firstOrNull { it.tag == "image" }

            when {
                wikiLink != null -> onLinkClick(wikiLink.item)
                tag != null -> onLinkClick(tag.item) // Treat tag click as link click (navigate to page)
                link != null -> onUrlClick(link.item)
                url != null -> onUrlClick(url.item)
                image != null -> onUrlClick(image.item)
                else -> onClick() // Enter edit mode
            }
        },
        style = MaterialTheme.typography.bodyMedium.copy(
            color = textColor
        ),
        modifier = modifier.padding(vertical = 4.dp)
    )
}
