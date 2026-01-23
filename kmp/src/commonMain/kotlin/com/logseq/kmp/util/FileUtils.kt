package com.logseq.kmp.util

object FileUtils {
    private val RESERVED_WINDOWS_NAMES = setOf(
        "CON", "PRN", "AUX", "NUL",
        "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
        "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    )

    /**
     * Sanitizes a page title to be safe for filenames across platforms (Android, Windows, etc.).
     * 
     * Replaces reserved characters with their percent-encoded equivalents:
     * - / -> %2F (Directory separator)
     * - : -> %3A (Reserved in Windows paths)
     * - \ -> %5C (Windows path separator)
     * - * -> %2A (Wildcard)
     * - ? -> %3F (Wildcard)
     * - " -> %22 (Reserved)
     * - < -> %3C (Redirection)
     * - > -> %3E (Redirection)
     * - | -> %7C (Pipe)
     * 
     * Also handles Windows reserved names and trailing dots/spaces.
     */
    fun sanitizeFileName(name: String): String {
        // 1. Basic character replacement
        var sanitized = name
            .replace("%", "%25") // Encode % first to avoid double-encoding
            .replace("/", "%2F")
            .replace(":", "%3A")
            .replace("\\", "%5C")
            .replace("*", "%2A")
            .replace("?", "%3F")
            .replace("\"", "%22")
            .replace("<", "%3C")
            .replace(">", "%3E")
            .replace("|", "%7C")

        // 2. Handle Windows reserved names (case-insensitive)
        if (RESERVED_WINDOWS_NAMES.contains(sanitized.uppercase())) {
            sanitized += "_"
        }

        // 3. Handle trailing dots and spaces (Windows restriction)
        if (sanitized.endsWith(" ") || sanitized.endsWith(".")) {
             val lastChar = sanitized.last()
             sanitized = sanitized.dropLast(1) + if (lastChar == ' ') "%20" else "%2E"
        }

        return sanitized.ifEmpty { "Untitled" }
    }
}
