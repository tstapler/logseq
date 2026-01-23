package com.logseq.kmp.parsing

import com.logseq.kmp.parsing.ast.*
import com.logseq.kmp.parsing.lexer.*

class BlockParser(private val source: CharSequence) {
    private val lexer = Lexer(source)
    private var currentToken = lexer.nextToken()

    fun parse(): DocumentNode {
        val rootBlocks = parseBlocksAtLevel(0)
        return DocumentNode(rootBlocks)
    }

    private fun parseBlocksAtLevel(minLevel: Int): List<BlockNode> {
        val blocks = mutableListOf<BlockNode>()

        while (currentToken.type != TokenType.EOF) {
            val currentLevel = peekIndentLevel()
            
            if (currentLevel < minLevel) {
                // This block belongs to a parent (or grandparent)
                break
            }

            // We are at a block that is at least minLevel.
            // In strict mode, it should be == minLevel.
            // But if user skips levels (e.g. 0 -> 2), we treat it as a child of the previous?
            // Or just a block at level 2.
            
            // For now, let's assume we consume one block and its children.
            val block = parseBlock(currentLevel)
            blocks.add(block)
        }
        return blocks
    }

    private fun parseBlock(level: Int): BlockNode {
        // 1. Consume INDENT if present
        if (currentToken.type == TokenType.INDENT) {
            advance()
        }

        // 2. Check for Bullet
        val isBullet = if (currentToken.type == TokenType.BULLET) {
            advance()
            true
        } else {
            false
        }

        // 3. Parse Content & Properties
        // A block consists of:
        // - First line text
        // - Optional properties (indented, key:: value)
        // - Optional continuation text (indented, no bullet)
        // - Children (indented, bullet)

        val contentBuilder = StringBuilder()
        val properties = mutableMapOf<String, String>()
        
        // Parse first line
        contentBuilder.append(parseLine())

        // Check for subsequent lines that belong to this block
        while (currentToken.type != TokenType.EOF) {
            val nextLevel = peekIndentLevel()
            val nextIsBullet = peekIsBullet()
            
            if (nextLevel <= level) {
                // Sibling or parent -> Stop
                break
            }
            
            if (nextIsBullet) {
                // Child block -> Stop processing content, move to children
                break
            }
            
            // It is indented and NOT a bullet -> Content or Property
            // Consume the indent
            if (currentToken.type == TokenType.INDENT) advance()
            
            // Check for Property (key:: value)
            val property = tryParseProperty()
            if (property != null) {
                properties[property.first] = property.second
                // Consume newline after property
                if (currentToken.type == TokenType.NEWLINE) advance()
            } else {
                // Continuation text
                if (contentBuilder.isNotEmpty()) contentBuilder.append("\n")
                contentBuilder.append(parseLine())
            }
        }

        // 4. Parse Children
        // Children are blocks with level > current level
        // We already verified above that if we hit a bullet > level, it's a child.
        val children = parseBlocksAtLevel(level + 1)

        val inlineContent = listOf(TextNode(contentBuilder.toString())) // Placeholder

        return if (isBullet) {
            BulletBlockNode(
                content = inlineContent,
                children = children,
                properties = properties,
                level = level
            )
        } else {
            ParagraphBlockNode(
                content = inlineContent,
                children = children,
                properties = properties
            )
        }
    }

    private fun parseLine(): String {
        val sb = StringBuilder()
        while (currentToken.type != TokenType.NEWLINE && currentToken.type != TokenType.EOF) {
            sb.append(currentToken.text(source))
            advance()
        }
        if (currentToken.type == TokenType.NEWLINE) {
            advance()
        }
        return sb.toString()
    }

    private fun tryParseProperty(): Pair<String, String>? {
        // Expected sequence: TEXT(key) COLON COLON WS(optional) TEXT(value)
        // Note: Lexer might emit WS tokens if we handle them?
        // My Lexer emits WS for spaces not at start of line.
        
        // 1. Check for Key (TEXT)
        if (currentToken.type != TokenType.TEXT) return null
        val keyToken = currentToken
        
        // 2. Check for :: (COLON, COLON)
        val t1 = peekToken(1)
        val t2 = peekToken(2)
        
        if (t1.type == TokenType.COLON && t2.type == TokenType.COLON) {
            // Found "key::"
            val key = keyToken.text(source).toString()
            
            // Consume KEY, COLON, COLON
            advance() // key
            advance() // :
            advance() // :
            
            // Consume optional WS
            if (currentToken.type == TokenType.WS) advance()
            
            // Consume Value (rest of line)
            val value = parseLine()
            return key to value
        }
        
        return null
    }

    private fun peekIndentLevel(): Int {
        if (currentToken.type == TokenType.INDENT) {
            val text = currentToken.text(source)
            return calculateLevel(text)
        }
        return 0
    }
    
    private fun peekIsBullet(): Boolean {
        if (currentToken.type == TokenType.BULLET) return true
        
        if (currentToken.type == TokenType.INDENT) {
            val next = peekToken(1)
            return next.type == TokenType.BULLET
        }
        return false
    }
    
    private fun peekToken(offset: Int): Token {
        if (offset == 0) return currentToken
        
        val state = lexer.saveState()
        // Current token is already consumed/cached in `currentToken`?
        // No, `currentToken` holds the result of `lexer.nextToken()`.
        // The lexer's cursor is AFTER `currentToken`.
        // So `lexer.nextToken()` will return the *next* token (offset 1).
        
        var token = currentToken
        // We need to advance `offset` times from current state?
        // No, `lexer` is poised to return `next` (offset 1).
        
        for (i in 0 until offset) {
            token = lexer.nextToken()
        }
        
        lexer.restoreState(state)
        return token
    }



    private fun calculateLevel(text: CharSequence): Int {
        var spaces = 0
        var tabs = 0
        for (char in text) {
            when (char) {
                ' ' -> spaces++
                '\t' -> tabs++
            }
        }
        // Logic: 1 tab = 1 level, 2 spaces = 1 level. Rounding up.
        return tabs + ((spaces + 1) / 2)
    }

    private fun advance() {
        currentToken = lexer.nextToken()
    }
}
