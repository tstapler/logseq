package com.logseq.kmp.parser

/**
 * Preprocesses Markdown content to ensure compatibility with strict CommonMark parsers.
 * 
 * Main responsibility: Normalizing indentation.
 * Logseq (and many users) use 2-space indentation for lists, but strict CommonMark 
 * often requires 4 spaces (or 2 spaces relative to the bullet) for nested lists.
 * To ensure consistent AST generation, we normalize known list patterns to a safe indentation.
 */
object MarkdownPreprocessor {

    fun normalize(content: String): String {
        val lines = content.lines()
        val normalizedLines = mutableListOf<String>()
        
        // We need to infer the indentation "step" (e.g., is the user using 2 spaces or tab?)
        // For now, we assume a standard Logseq convention: 2 spaces = 1 level.
        // We will convert 2 spaces -> 4 spaces to satisfy strict CommonMark parsers if needed.
        // Or actually, just ensure it's consistent.
        
        for (line in lines) {
            val trimmedStart = line.trimStart()
            
            // If it's a list item
            if (isListItem(trimmedStart)) {
                val level = calculateLevel(line)
                
                // CommonMark standard is often 4 spaces for a sub-block.
                // Let's force 4 spaces per level to be safe for the parser.
                val newIndentation = "    ".repeat(level)
                normalizedLines.add(newIndentation + trimmedStart)
            } else {
                // For non-list items (e.g. paragraphs inside blocks), 
                // we should probably try to align them with the parent block.
                // For simplicity in this first pass, we preserve them as is 
                // OR we could try to indent them to match the previous block level.
                normalizedLines.add(line)
            }
        }
        
        return normalizedLines.joinToString("\n")
    }
    
    private fun isListItem(trimmedLine: String): Boolean {
        // Matches "- ", "* ", "+ ", "1. "
        return trimmedLine.startsWith("- ") || 
               trimmedLine.startsWith("* ") || 
               trimmedLine.startsWith("+ ") || 
               (trimmedLine.isNotEmpty() && trimmedLine[0].isDigit() && trimmedLine.contains(". "))
    }
    
    private fun calculateLevel(line: String): Int {
        var spaces = 0
        var tabs = 0
        
        for (char in line) {
            when (char) {
                ' ' -> spaces++
                '\t' -> tabs++
                else -> break
            }
        }
        
        // Logic: 1 tab = 1 level
        // 2 spaces = 1 level
        // But we need to handle mixed? Usually it's one or the other.
        // Let's assume tabs take precedence if present, or add to spaces.
        // A tab is usually 2 or 4 spaces visually.
        
        return tabs + (spaces / 2)
    }
}
