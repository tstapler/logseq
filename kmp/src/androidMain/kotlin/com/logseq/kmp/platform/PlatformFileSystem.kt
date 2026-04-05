package com.logseq.kmp.platform

import android.content.Context
import android.os.Environment
import java.io.File

actual class PlatformFileSystem actual constructor() : FileSystem {
    private var context: Context? = null
    private var onPickDirectory: (suspend () -> String?)? = null
    private val maxPathLength = 4096
    private val maxFileSize = 100 * 1024 * 1024
    private val dangerousPatterns = listOf("..", "../", "..\\", "\u0000")
    private val homeDir: String by lazy { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)?.absolutePath ?: "/storage/emulated/0/Documents" }

    fun init(context: Context, onPickDirectory: (suspend () -> String?)? = null) {
        this.context = context
        this.onPickDirectory = onPickDirectory
    }

    actual override fun getDefaultGraphPath(): String {
        return "${homeDir}/logseq"
    }

    actual override fun expandTilde(path: String): String {
        return if (path.startsWith("~")) {
            path.replaceFirst("~", homeDir)
        } else {
            path
        }
    }

    actual override fun readFile(path: String): String? {
        return try {
            val expandedPath = expandTilde(path)
            val validatedPath = validatePath(expandedPath)
            val file = File(validatedPath)
            if (!file.exists() || !file.isFile) return null
            if (file.length() > maxFileSize) return null
            file.readText()
        } catch (e: Exception) {
            null
        }
    }

    actual override fun writeFile(path: String, content: String): Boolean {
        return try {
            val expandedPath = expandTilde(path)
            val validatedPath = validatePath(expandedPath)
            if (content.length > maxFileSize) return false
            val file = File(validatedPath)
            val parentDir = file.parentFile
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs()
            }
            file.writeText(content)
            true
        } catch (e: Exception) {
            false
        }
    }

    actual override fun listFiles(path: String): List<String> {
        return try {
            val expandedPath = expandTilde(path)
            val validatedPath = validatePath(expandedPath)
            val directory = File(validatedPath)
            if (!directory.exists() || !directory.isDirectory) return emptyList()
            directory.listFiles()
                ?.filter { it.isFile }
                ?.map { it.name }
                ?.sorted()
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    actual override fun listDirectories(path: String): List<String> {
        return try {
            val expandedPath = expandTilde(path)
            val validatedPath = validatePath(expandedPath)
            val directory = File(validatedPath)
            if (!directory.exists() || !directory.isDirectory) return emptyList()
            directory.listFiles()
                ?.filter { it.isDirectory }
                ?.map { it.name }
                ?.sorted()
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    actual override fun fileExists(path: String): Boolean {
        return try {
            val expandedPath = expandTilde(path)
            val validatedPath = validatePath(expandedPath)
            val file = File(validatedPath)
            file.exists() && file.isFile
        } catch (e: Exception) {
            false
        }
    }

    actual override fun directoryExists(path: String): Boolean {
        return try {
            val expandedPath = expandTilde(path)
            val validatedPath = validatePath(expandedPath)
            val file = File(validatedPath)
            file.exists() && file.isDirectory
        } catch (e: Exception) {
            false
        }
    }

    actual override fun createDirectory(path: String): Boolean {
        return try {
            val expandedPath = expandTilde(path)
            val validatedPath = validatePath(expandedPath)
            val file = File(validatedPath)
            if (file.exists()) {
                return file.isDirectory
            }
            file.mkdirs()
        } catch (e: Exception) {
            false
        }
    }

    actual override fun deleteFile(path: String): Boolean {
        return try {
            val expandedPath = expandTilde(path)
            val validatedPath = validatePath(expandedPath)
            val file = File(validatedPath)
            if (!file.exists()) return true
            if (file.isDirectory) file.deleteRecursively() else file.delete()
        } catch (e: Exception) {
            false
        }
    }
    
    actual override fun pickDirectory(): String? = null // Handled via pickDirectoryAsync on Android

    actual override suspend fun pickDirectoryAsync(): String? {
        return onPickDirectory?.invoke()
    }

    actual override fun getLastModifiedTime(path: String): Long? {
        return try {
            val expandedPath = expandTilde(path)
            val validatedPath = validatePath(expandedPath)
            val file = File(validatedPath)
            if (file.exists() && file.isFile) {
                file.lastModified()
            } else {
                null
            }
        } catch (e: Exception) {
            null
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
        val file = File(expandedPath)
        val canonicalPath = file.canonicalPath
        val homePath = File(homeDir).canonicalPath
        require(canonicalPath.startsWith(homePath)) { "Path must be within allowed directory" }
        return canonicalPath
    }
}
