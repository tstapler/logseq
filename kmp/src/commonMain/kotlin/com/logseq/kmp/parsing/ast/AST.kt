package com.logseq.kmp.parsing.ast

/**
 * Base class for all AST nodes in the Logseq graph parser.
 */
sealed class ASTNode

/**
 * Root node representing a parsed document (Page or Journal).
 */
data class DocumentNode(
    val children: List<BlockNode>
) : ASTNode()

/**
 * Represents a structural block (bullet point or paragraph).
 */
sealed class BlockNode : ASTNode() {
    abstract val content: List<InlineNode>
    abstract val children: List<BlockNode>
    abstract val properties: Map<String, String>
}

/**
 * Standard bullet block (e.g. "- content").
 */
data class BulletBlockNode(
    override val content: List<InlineNode>,
    override val children: List<BlockNode> = emptyList(),
    override val properties: Map<String, String> = emptyMap(),
    val level: Int
) : BlockNode()

/**
 * Paragraph block (no bullet, usually top level or inside a block in some dialects).
 */
data class ParagraphBlockNode(
    override val content: List<InlineNode>,
    override val children: List<BlockNode> = emptyList(),
    override val properties: Map<String, String> = emptyMap()
) : BlockNode()

/**
 * Represents inline elements within a block's content.
 */
sealed class InlineNode : ASTNode()

data class TextNode(val content: String) : InlineNode()

data class BoldNode(val children: List<InlineNode>) : InlineNode()
data class ItalicNode(val children: List<InlineNode>) : InlineNode()
data class StrikeNode(val children: List<InlineNode>) : InlineNode()
data class CodeNode(val content: String) : InlineNode()

data class WikiLinkNode(
    val target: String,
    val alias: String? = null
) : InlineNode()

data class BlockRefNode(val blockUuid: String) : InlineNode()

data class TagNode(val tag: String) : InlineNode()

data class UrlLinkNode(
    val url: String,
    val text: List<InlineNode>
) : InlineNode()
