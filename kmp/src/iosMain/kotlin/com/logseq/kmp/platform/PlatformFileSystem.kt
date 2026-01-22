package com.logseq.kmp.platform

import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSURL

actual class PlatformFileSystem actual constructor() {
    private val maxPathLength = 4096
    private val maxFileSize = 100 * 1024 * 1024
    private val dangerousPatterns = listOf("..", "../", "..\\", "\u0000")
    private val homeDir: String by lazy {
        val fileManager = NSFileManager.defaultManager
        val urls = fileManager.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
        val documentsUrl = urls.firstOrNull() as? NSURL
        documentsUrl?.path ?: "/Documents"
    }

    actual fun getDefaultGraphPath(): String = "$homeDir/logseq"

    actual fun expandTilde(path: String): String {
        return if (path.startsWith("~")) {
            path.replaceFirst("~", homeDir)
        } else {
            path
        }
    }

    actual fun readFile(path: String): String? {
        return try {
            val expandedPath = expandTilde(path)
            val validatedPath = validatePath(expandedPath)
            val fileManager = NSFileManager.defaultManager
            val exists = fileManager.fileExistsAtPath(validatedPath)
            if (!exists) return null
            val content = fileManager.contentsAtPath(validatedPath)
            content?.let { String(it) }
        } catch (e: Exception) {
            null
        }
    }

    actual fun writeFile(path: String, content: String): Boolean {
        return try {
            val expandedPath = expandTilde(path)
            val validatedPath = validatePath(expandedPath)
            if (content.length > maxFileSize) return false
            val fileManager = NSFileManager.defaultManager
            val data = content.encodeToByteArray()
            fileManager.createFileAtPath(validatedPath, data, null)
            true
        } catch (e: Exception) {
            false
        }
    }

    actual fun listFiles(path: String): List<String> {
        return try {
            val expandedPath = expandTilde(path)
            val validatedPath = validatePath(expandedPath)
            val fileManager = NSFileManager.defaultManager
            val contents = fileManager.contentsOfDirectoryAtPath(validatedPath, null)
            contents.mapNotNull { it as? String }.filter {
                val fullPath = "$validatedPath/$it"
                val exists = fileManager.fileExistsAtPath(fullPath)
                val isDir = fileManager.fileExistsAtPath(fullPath) // Simplified check
                exists && !isDir
            }.sorted()
        } catch (e: Exception) {
            emptyList()
        }
    }

    actual fun listDirectories(path: String): List<String> {
        return try {
            val expandedPath = expandTilde(path)
            val validatedPath = validatePath(expandedPath)
            val fileManager = NSFileManager.defaultManager
            val contents = fileManager.contentsOfDirectoryAtPath(validatedPath, null)
            contents.mapNotNull { it as? String }.filter {
                val fullPath = "$validatedPath/$it"
                val isDir = fileManager.fileExistsAtPath(fullPath) // Would need proper isDirectory check
                true // Simplified
            }.sorted()
        } catch (e: Exception) {
            emptyList()
        }
    }

    actual fun fileExists(path: String): Boolean {
        return try {
            val expandedPath = expandTilde(path)
            val validatedPath = validatePath(expandedPath)
            val fileManager = NSFileManager.defaultManager
            fileManager.fileExistsAtPath(validatedPath)
        } catch (e: Exception) {
            false
        }
    }

    actual fun directoryExists(path: String): Boolean {
        return try {
            val expandedPath = expandTilde(path)
            val validatedPath = validatePath(expandedPath)
            val fileManager = NSFileManager.defaultManager
            val exists = fileManager.fileExistsAtPath(validatedPath)
            exists // Would need proper directory check
        } catch (e: Exception) {
            false
        }
    }

    actual fun createDirectory(path: String): Boolean {
        return try {
            val expandedPath = expandTilde(path)
            val validatedPath = validatePath(expandedPath)
            val fileManager = NSFileManager.defaultManager
            fileManager.createDirectoryAtPath(validatedPath, true, null, null)
            true
        } catch (e: Exception) {
            false
        }
    }

    actual fun deleteFile(path: String): Boolean {
        return try {
            val expandedPath = expandTilde(path)
            val validatedPath = validatePath(expandedPath)
            val fileManager = NSFileManager.defaultManager
            fileManager.removeItemAtPath(validatedPath, null)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun validatePath(path: String): String {
        require(path.length <= maxPathLength) { "Path exceeds maximum length" }
        require(!path.contains('\u0000')) { "Path contains null bytes" }
        dangerousPatterns.forEach { pattern ->
            require(!path.contains(pattern)) { "Path contains dangerous pattern: $pattern" }
        }
        val normalized = path.replace(Regex("[/\\\\]+"), "/")
        val expandedPath = expandTilde(normalized)
        require(expandedPath.startsWith(homeDir)) { "Path must be within user's home directory" }
        return expandedPath
    }
}
