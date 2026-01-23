package com.logseq.kmp.parsing.lexer

enum class TokenType {
    // Structural
    EOF,
    NEWLINE,        // \n
    INDENT,         // Spaces/Tabs at start of line
    BULLET,         // - or * or + (at start of line after indent)
    
    // Inline Formatting Markers
    STAR,           // *
    UNDERSCORE,     // _
    TILDE,          // ~
    BACKTICK,       // `
    
    // Links & References
    L_BRACKET,      // [
    R_BRACKET,      // ]
    L_PAREN,        // (
    R_PAREN,        // )
    HASH,           // #
    
    // Properties
    COLON,          // :
    
    // Text Content
    TEXT,           // Normal text
    WS              // Whitespace (that isn't a newline or indent)
}

/**
 * Represents a lexical token.
 * Holds indices into the source text to avoid string allocation (Zero-Copy).
 */
data class Token(
    val type: TokenType,
    val start: Int,
    val end: Int
) {
    fun text(source: CharSequence): CharSequence {
        return source.subSequence(start, end)
    }
}
