package com.logseq.kmp.parsing

import com.logseq.kmp.parsing.ast.*
import com.logseq.kmp.parsing.lexer.*

class InlineParser(private val source: CharSequence) {
    private val lexer = Lexer(source)
    private var currentToken = lexer.nextToken()

    fun parse(): List<InlineNode> {
        val nodes = mutableListOf<InlineNode>()
        while (currentToken.type != TokenType.EOF) {
            nodes.add(parseExpression(0))
        }
        return nodes
    }

    private fun parseExpression(precedence: Int): InlineNode {
        var token = currentToken
        advance()

        var left = parsePrefix(token)

        while (precedence < getPrecedence()) {
            token = currentToken
            advance()
            left = parseInfix(left, token)
        }
        return left
    }

    private fun parsePrefix(token: Token): InlineNode {
        return when (token.type) {
            TokenType.TEXT, TokenType.WS, TokenType.COLON, TokenType.NEWLINE -> TextNode(token.text(source).toString())
            TokenType.L_BRACKET -> parseLink(token)
            TokenType.L_PAREN -> TextNode("(") // Was parseBlockRef, now handled by BLOCK_REF_OPEN
            TokenType.BLOCK_REF_OPEN -> parseBlockRef(token)
            TokenType.STAR -> parseEmphasis(token, TokenType.STAR)
            TokenType.UNDERSCORE -> parseEmphasis(token, TokenType.UNDERSCORE)
            TokenType.TILDE -> parseEmphasis(token, TokenType.TILDE)
            TokenType.BACKTICK -> parseCode(token)
            TokenType.HASH -> parseTag(token)
            else -> TextNode(token.text(source).toString())
        }
    }

    private fun parseInfix(left: InlineNode, token: Token): InlineNode {
        // Implement infix logic here (e.g. for something like link text? or just simple concatenation?)
        // For simple inline markup, we often handle the closing tag in the prefix parselet itself (e.g. *...*).
        // But Pratt is useful if we have operators.
        // If we don't have true infix operators, we might just return left?
        // Actually, if we just want to parse a sequence, maybe recursive descent is simpler?
        // But Pratt allows "binding power" to handle nesting correctly.
        
        return left // Placeholder
    }

    private fun getPrecedence(): Int {
        // Determine precedence of currentToken
        return 0
    }

    private fun parseLink(token: Token): InlineNode {
        // Handle [[WikiLink]] or [Url](link)
        // Check for double bracket
        if (currentToken.type == TokenType.L_BRACKET) {
            // [[ detected
            advance()
            // Parse content until ]]
            val sb = StringBuilder()
            while (currentToken.type != TokenType.R_BRACKET && currentToken.type != TokenType.EOF) {
                sb.append(currentToken.text(source))
                advance()
            }
            // Consume first ]
            if (currentToken.type == TokenType.R_BRACKET) advance()
            // Consume second ]
            if (currentToken.type == TokenType.R_BRACKET) advance()
            
            return WikiLinkNode(sb.toString())
        }
        // Else simple [
        return TextNode("[") 
    }

    private fun parseBlockRef(token: Token): InlineNode {
        // Token is BLOCK_REF_OPEN ((
        val sb = StringBuilder()
        while (currentToken.type != TokenType.BLOCK_REF_CLOSE && currentToken.type != TokenType.EOF) {
            sb.append(currentToken.text(source))
            advance()
        }
        // Consume BLOCK_REF_CLOSE ))
        if (currentToken.type == TokenType.BLOCK_REF_CLOSE) advance()
        
        return BlockRefNode(sb.toString())
    }

    private fun parseEmphasis(token: Token, type: TokenType): InlineNode {
        // Token length determines emphasis type
        // length 1 = Italic (* or _)
        // length 2 = Bold (** or __)
        // length 2 = Strike (~~)
        
        val len = token.end - token.start
        
        val isBold = (type == TokenType.STAR || type == TokenType.UNDERSCORE) && len >= 2
        val isStrike = (type == TokenType.TILDE) && len >= 2
        
        // If it's a marker we don't support (e.g. ***), treat as text for now?
        // Or handle recursive? *** = Bold + Italic.
        // For simplicity, let's strictly handle 1 and 2.
        // If len > 2, we might need to consume only part of it?
        // But Lexer consumed it all.
        // Example: ***text***
        // Token is STAR (len 3).
        // Parsing this is tricky without splitting tokens.
        // Ideally we treat STAR(3) as STAR(2) + STAR(1).
        
        // MVP: Handle only strict 1 and 2 for now.
        
        val children = mutableListOf<InlineNode>()
        
        // Loop until we find a matching closing token
        while (currentToken.type != TokenType.EOF && currentToken.type != TokenType.NEWLINE) {
            if (currentToken.type == type) {
                val closeLen = currentToken.end - currentToken.start
                if (closeLen == len) {
                    // Match found
                    advance() // consume closing
                    
                    return when {
                        isBold -> BoldNode(children)
                        isStrike -> StrikeNode(children)
                        else -> ItalicNode(children)
                    }
                }
            }
            
            children.add(parseExpression(0))
        }
        
        // If loop finished without match, it's plain text
        // We need to fallback. 
        // This effectively means we need backtracking or we return a TextNode with the delimiter prefix + parsed children as text?
        // Converting children back to text is expensive.
        // Ideally, we shouldn't have parsed children if we fail.
        
        // For this MVP, if unclosed, we return TextNode with raw content.
        // But we already consumed tokens!
        // We need to restore state if we fail to find a match?
        // Or just return a "Broken" node that renders as text.
        
        // Let's wrap in a TextNode fallback
        val prefix = token.text(source).toString()
        // Reconstruct content from children? Hard.
        // This is why recursive descent without backtracking is hard for Markdown.
        
        // Alternative: Return the partial tree, but marked as "Unclosed"?
        // Renderer can handle it.
        // Or TextNode(prefix) followed by children inline.
        
        return TextNode(prefix) // Simplified failure case (loses children content in this naive impl)
    }

    private fun parseCode(token: Token): InlineNode {
        // `code`
        val sb = StringBuilder()
        while (currentToken.type != TokenType.BACKTICK && currentToken.type != TokenType.EOF) {
            sb.append(currentToken.text(source))
            advance()
        }
        if (currentToken.type == TokenType.BACKTICK) advance()
        return CodeNode(sb.toString())
    }

    private fun parseTag(token: Token): InlineNode {
        // #tag
        val tag = currentToken.text(source).toString()
        advance()
        return TagNode(tag)
    }

    private fun advance() {
        currentToken = lexer.nextToken()
    }
}
