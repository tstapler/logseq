package com.logseq.kmp.parser

import com.logseq.kmp.model.ParsedBlock
import com.logseq.kmp.model.ParsedPage
import com.logseq.kmp.parsing.LogseqParser
import com.logseq.kmp.parsing.ast.*

class MarkdownParser {

    private val parser = LogseqParser()

    fun parsePage(content: String): ParsedPage {
        val document = parser.parse(content)
        
        // DEBUG: Dump AST structure for specific page
        if (content.contains("Rediscovering Paper")) {
            println("MarkdownParser DEBUG: AST for page with Rediscovering Paper:")
            dumpAST(document.children, 0)
        }
        
        // Convert AST to ParsedPage model
        val parsedBlocks = document.children.map { convertBlock(it) }
        
        return ParsedPage(
            title = null,
            properties = emptyMap(), // Page props handled in GraphLoader via first block?
            blocks = parsedBlocks
        )
    }
    
    private fun dumpAST(nodes: List<BlockNode>, indent: Int) {
        nodes.forEach { node ->
            val prefix = "  ".repeat(indent)
            val content = reconstructContent(node.content).take(20)
            println("$prefix- Block: '$content' (Children: ${node.children.size})")
            dumpAST(node.children, indent + 1)
        }
    }

    private fun convertBlock(block: BlockNode): ParsedBlock {
        val level = when(block) {
            is BulletBlockNode -> block.level
            is ParagraphBlockNode -> 0 // Treat paragraph as root? Or error?
        }
        
        // Reconstruct content string from InlineNodes
        // Note: The original ParsedBlock.content was a String.
        // We need to serialize the InlineNodes back to text or (better) update ParsedBlock to hold InlineNodes.
        // For now, to keep GraphLoader compatible, we serialize back to string but stripped of properties?
        // Wait, GraphLoader expects the content to show in the UI.
        // The UI (BlockRenderer) likely renders the raw string or parses it again.
        // If we return the raw string, we are double parsing?
        // Yes, current architecture: GraphLoader saves raw string -> Database -> UI -> BlockRenderer (parses again).
        // 
        // Ideally: GraphLoader saves the AST or a structured format.
        // But for "Swap", let's reconstruct the "Content" string (without properties).
        
        val contentString = reconstructContent(block.content)
        
        // Extract references from InlineNodes
        val references = extractReferences(block.content)
        
        val children = block.children.map { convertBlock(it) }
        
        // Extract Scheduled/Deadline from properties or content?
        // Our new parser might put them in properties?
        // Or we need to parse them from content if they weren't property keys.
        // The legacy mldoc treated SCHEDULED as metadata.
        // My `BlockParser` treats `key:: value` as properties.
        // `SCHEDULED: <...>` is NOT a property syntax. It's content.
        // So it stays in `content`.
        // We need `TimestampParser` logic? 
        // Wait, the previous `MarkdownParser` used `TimestampParser` to extract them and REMOVE from content.
        // My new `LogseqParser`'s `InlineParser` treats them as text for now.
        // I should probably enhance `LogseqParser` or `convertBlock` to handle timestamps.
        
        // Let's use the existing `TimestampParser` on the reconstructed content 
        // to populate metadata and strip it for the final content field?
        // OR better: Update `InlineParser` to recognize timestamps?
        // For now, reuse `TimestampParser` here to match previous behavior.
        
        val timestampResult = TimestampParser.parse(contentString)
        
        return ParsedBlock(
            content = timestampResult.content, // Strip timestamps
            properties = block.properties,
            level = level,
            children = children,
            references = references,
            scheduled = timestampResult.scheduled,
            deadline = timestampResult.deadline
        )
    }
    
    private fun reconstructContent(nodes: List<InlineNode>): String {
        val sb = StringBuilder()
        nodes.forEach { node ->
            when(node) {
                is TextNode -> sb.append(node.content)
                is BoldNode -> {
                    sb.append("**")
                    sb.append(reconstructContent(node.children))
                    sb.append("**")
                }
                is ItalicNode -> {
                    sb.append("*")
                    sb.append(reconstructContent(node.children))
                    sb.append("*")
                }
                is StrikeNode -> {
                    sb.append("~~")
                    sb.append(reconstructContent(node.children))
                    sb.append("~~")
                }
                is CodeNode -> {
                    sb.append("`")
                    sb.append(node.content)
                    sb.append("`")
                }
                is WikiLinkNode -> {
                    sb.append("[[")
                    sb.append(node.target)
                    sb.append("]]")
                }
                is BlockRefNode -> {
                    sb.append("((")
                    sb.append(node.blockUuid)
                    sb.append("))")
                }
                is TagNode -> {
                    sb.append("#")
                    sb.append(node.tag)
                }
                is UrlLinkNode -> {
                    sb.append("[")
                    sb.append(reconstructContent(node.text))
                    sb.append("](")
                    sb.append(node.url)
                    sb.append(")")
                }
            }
        }
        return sb.toString()
    }
    
    private fun extractReferences(nodes: List<InlineNode>): List<String> {
        val refs = mutableListOf<String>()
        nodes.forEach { node ->
            when(node) {
                is WikiLinkNode -> refs.add(node.target)
                is BlockRefNode -> refs.add(node.blockUuid)
                is TagNode -> refs.add(node.tag) // Tags are references? Yes usually.
                is BoldNode -> refs.addAll(extractReferences(node.children))
                is ItalicNode -> refs.addAll(extractReferences(node.children))
                is StrikeNode -> refs.addAll(extractReferences(node.children))
                else -> {}
            }
        }
        return refs
    }
}
