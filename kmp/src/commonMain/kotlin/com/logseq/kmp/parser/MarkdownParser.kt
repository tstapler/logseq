package com.logseq.kmp.parser

import com.logseq.kmp.model.ParsedBlock
import com.logseq.kmp.model.ParsedPage
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser as JetbrainsMarkdownParser

class MarkdownParser {

    private val flavour = CommonMarkFlavourDescriptor()
    private val parser = JetbrainsMarkdownParser(flavour)

    fun parsePage(content: String): ParsedPage {
        // Preprocess to ensure indentation compatibility
        val normalizedContent = MarkdownPreprocessor.normalize(content)
        
        val rootNode = parser.buildMarkdownTreeFromString(normalizedContent)
        val blocks = convertToBlocks(rootNode, normalizedContent)
        
        // Logseq pages can have page-level properties in the first block if it's a property drawer
        // But in our block-based model, we usually just treat the first block as the page properties block 
        // if it contains ONLY properties.
        // For ParsedPage, we might want to extract them if needed, but for now we keep them on the block.
        
        return ParsedPage(
            title = null,
            properties = emptyMap(),
            blocks = blocks
        )
    }

    private fun convertToBlocks(node: ASTNode, content: String): List<ParsedBlock> {
        val blocks = mutableListOf<ParsedBlock>()
        
        for (child in node.children) {
            val type = child.type.toString()
            // Heuristic check for Unordered List
            if (type.contains("UL") || type.contains("UnorderedList") || type.contains("BULLET_LIST") || type.contains("UNORDERED_LIST")) {
                blocks.addAll(processList(child, content, 0))
            } else if (type.contains("PARAGRAPH") || type.contains("Paragraph")) {
                // Treat top-level paragraph as a block
                val text = getTextInNode(child, content).toString().trim()
                
                // 1. Parse Properties (Inline & Drawer)
                val propertiesResult = PropertiesParser.parse(text)
                
                // 2. Parse Timestamps from remaining content
                val timestampResult = TimestampParser.parse(propertiesResult.content)
                val finalContent = timestampResult.content
                
                val references = extractReferences(finalContent)
                
                blocks.add(
                    ParsedBlock(
                        content = finalContent,
                        properties = propertiesResult.properties,
                        level = 0,
                        children = emptyList(),
                        references = references,
                        scheduled = timestampResult.scheduled,
                        deadline = timestampResult.deadline
                    )
                )
            }
        }
        
        return blocks
    }

    private fun processList(listNode: ASTNode, content: String, level: Int): List<ParsedBlock> {
        val blocks = mutableListOf<ParsedBlock>()
        
        for (child in listNode.children) {
            val type = child.type.toString()
            // Heuristic check for List Item
            if (type.contains("LI") || type.contains("ListItem") || type.contains("LIST_ITEM")) {
                val (rawContent, nestedBlocks) = processListItem(child, content, level)
                
                // 1. Parse Properties
                val propertiesResult = PropertiesParser.parse(rawContent)
                
                // 2. Parse Timestamps
                val timestampResult = TimestampParser.parse(propertiesResult.content)
                val finalContent = timestampResult.content
                
                val references = extractReferences(finalContent)
                
                blocks.add(
                    ParsedBlock(
                        content = finalContent,
                        properties = propertiesResult.properties,
                        level = level,
                        children = nestedBlocks,
                        references = references,
                        scheduled = timestampResult.scheduled,
                        deadline = timestampResult.deadline
                    )
                )
            }
        }
        return blocks
    }

    private fun processListItem(node: ASTNode, content: String, level: Int): Pair<String, List<ParsedBlock>> {
        val nestedBlocks = mutableListOf<ParsedBlock>()
        val contentBuilder = StringBuilder()

        for (child in node.children) {
            val type = child.type.toString()
            if (type.contains("UL") || type.contains("UnorderedList") || type.contains("BULLET_LIST") || type.contains("UNORDERED_LIST")) {
                nestedBlocks.addAll(processList(child, content, level + 1))
            } else if (type.contains("PARAGRAPH") || type.contains("Paragraph")) {
                 contentBuilder.append(getTextInNode(child, content))
            } else {
                // For other elements, we might want to append them too if they are content
            }
        }
        
        return Pair(contentBuilder.toString().trim(), nestedBlocks)
    }

    private fun extractReferences(content: String): List<String> {
        val references = mutableListOf<String>()
        
        val wikiLinkRegex = Regex("""\[\[(.*?)\]\]""")
        wikiLinkRegex.findAll(content).forEach { matchResult ->
            references.add(matchResult.groupValues[1])
        }
        
        val blockRefRegex = Regex("""\(\((.*?)\)\)""")
        blockRefRegex.findAll(content).forEach { matchResult ->
            references.add(matchResult.groupValues[1])
        }
        
        return references
    }

    private fun getTextInNode(node: ASTNode, content: String): CharSequence {
        return content.subSequence(node.startOffset, node.endOffset)
    }
}
