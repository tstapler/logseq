package com.logseq.kmp.parsing

import com.logseq.kmp.parsing.ast.*

/**
 * Main facade for the Logseq Native KMP Parser.
 * Orchestrates Block Parsing and Inline Parsing.
 */
class LogseqParser {

    fun parse(source: CharSequence): DocumentNode {
        // 1. Parse Structure (Blocks, Indentation, Properties)
        val blockParser = BlockParser(source)
        val document = blockParser.parse()

        // 2. Parse Inline Content
        // We need to traverse the tree and parse the 'content' of each block
        // currently stored as a single TextNode.
        val processedChildren = document.children.map { processBlock(it) }

        return DocumentNode(processedChildren)
    }

    private fun processBlock(block: BlockNode): BlockNode {
        // 1. Parse Inline Content
        // Extract raw text from the placeholder TextNode
        val rawContent = (block.content.firstOrNull() as? TextNode)?.content ?: ""
        
        val inlineParser = InlineParser(rawContent)
        val parsedContent = inlineParser.parse()

        // 2. Recursively process children
        val processedChildren = block.children.map { processBlock(it) }

        return when (block) {
            is BulletBlockNode -> block.copy(
                content = parsedContent,
                children = processedChildren
            )
            is ParagraphBlockNode -> block.copy(
                content = parsedContent,
                children = processedChildren
            )
        }
    }
}
