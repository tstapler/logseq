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
        val rootNode = parser.buildMarkdownTreeFromString(content)
        val blocks = convertToBlocks(rootNode, content)
        val properties = emptyMap<String, String>() 
        
        return ParsedPage(
            title = null,
            properties = properties,
            blocks = blocks
        )
    }

    private fun convertToBlocks(node: ASTNode, content: String): List<ParsedBlock> {
        val blocks = mutableListOf<ParsedBlock>()
        
        for (child in node.children) {
            val type = child.type.toString()
            // Heuristic check for Unordered List
            if (type.contains("UL") || type.contains("UnorderedList") || type.contains("BULLET_LIST")) {
                blocks.addAll(processList(child, content, 0))
            } else if (type.contains("PARAGRAPH") || type.contains("Paragraph")) {
                // Treat top-level paragraph as a block
                val text = getTextInNode(child, content).toString().trim()
                val (finalContent, properties) = parseProperties(text)
                val references = extractReferences(finalContent)
                blocks.add(ParsedBlock(finalContent, properties, 0, emptyList(), references))
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
                val (finalContent, properties) = parseProperties(rawContent)
                val references = extractReferences(finalContent)
                
                blocks.add(
                    ParsedBlock(
                        content = finalContent,
                        properties = properties,
                        level = level,
                        children = nestedBlocks,
                        references = references
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
            if (type.contains("UL") || type.contains("UnorderedList") || type.contains("BULLET_LIST")) {
                nestedBlocks.addAll(processList(child, content, level + 1))
            } else if (type.contains("PARAGRAPH") || type.contains("Paragraph")) {
                 contentBuilder.append(getTextInNode(child, content))
            } else {
                // For other elements, we might want to append them too if they are content
            }
        }
        
        return Pair(contentBuilder.toString().trim(), nestedBlocks)
    }

    private fun parseProperties(content: String): Pair<String, Map<String, String>> {
        val lines = content.lines()
        val properties = mutableMapOf<String, String>()
        val contentLines = mutableListOf<String>()
        
        val propertyRegex = Regex("""^\s*([\w-_]+)::\s*(.*)$""")
        
        for (line in lines) {
            val match = propertyRegex.matchEntire(line)
            if (match != null) {
                val (key, value) = match.destructured
                properties[key] = value
            } else {
                contentLines.add(line)
            }
        }
        
        return Pair(content, properties)
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
