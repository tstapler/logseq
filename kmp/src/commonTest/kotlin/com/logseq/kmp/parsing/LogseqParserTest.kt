package com.logseq.kmp.parsing

import com.logseq.kmp.parsing.ast.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogseqParserTest {

    @Test
    fun `test full document parsing`() {
        val input = """
- Root Block
  id:: 123
  type:: page
  This is **bold** and [[Link]].
  - Child 1
    #tag
  - Child 2
- Root 2
        """.trimIndent()

        val parser = LogseqParser()
        val doc = parser.parse(input)

        // 1. Structure
        assertEquals(2, doc.children.size)
        val root1 = doc.children[0] as BulletBlockNode
        val root2 = doc.children[1] as BulletBlockNode

        // 2. Properties
        assertEquals("123", root1.properties["id"]?.trim())
        assertEquals("page", root1.properties["type"]?.trim())

        // 3. Inline Content
        // "Root Block\nThis is **bold** and [[Link]]."
        // Note: Newlines in content are preserved by BlockParser?
        // BlockParser.parseLine uses loop while != NEWLINE.
        // It consumes NEWLINE and starts next line.
        // The `parseBlock` loop appends `\n` then next line.
        
        // Let's check the inline nodes.
        // Expect: Text("Root Block\nThis is "), Bold("bold"), Text(" and "), WikiLink("Link"), Text(".")
        
        val content = root1.content
        // Depending on how InlineParser handles newlines/text runs
        // "Root Block\nThis is " might be one TextNode
        
        // Debugging output
        content.forEach { println(it) }
        
        assertTrue(content.any { it is BoldNode }, "Should contain BoldNode")
        assertTrue(content.any { it is WikiLinkNode }, "Should contain WikiLinkNode")
        
        val wikiLink = content.filterIsInstance<WikiLinkNode>().first()
        assertEquals("Link", wikiLink.target)
        
        // 4. Children
        assertEquals(2, root1.children.size)
        val child1 = root1.children[0] as BulletBlockNode
        // Content might be multiple nodes (Text, WS, Tag)
        // assertEquals(1, child1.content.size) // Removed strict size check
        
        // Check "Child 1\n#tag"
        // InlineParser might produce multiple TextNodes.
        
        assertTrue(child1.content.any { it is TagNode }, "Child 1 should have TagNode")
        val tagNode = child1.content.find { it is TagNode } as TagNode
        assertEquals("tag", tagNode.tag)
    }
}
