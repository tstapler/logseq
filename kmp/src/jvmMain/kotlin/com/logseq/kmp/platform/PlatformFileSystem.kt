package com.logseq.kmp.platform

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import javax.swing.JFileChooser
import javax.swing.SwingUtilities

actual class PlatformFileSystem actual constructor() : FileSystem {
    private val maxPathLength = 4096
    private val maxFileSize = 100 * 1024 * 1024
    private val dangerousPatterns = listOf("..", "../", "..\\", "\u0000")
    private val homeDir: String by lazy { System.getProperty("user.home") }

    actual override fun getDefaultGraphPath(): String = "$homeDir/Documents/logseq"

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
            Files.readString(Paths.get(validatedPath))
        } catch (e: Exception) {
            null
        }
    }

    actual override fun writeFile(path: String, content: String): Boolean {
        return try {
            val expandedPath = expandTilde(path)
            val validatedPath = validatePath(expandedPath)
            if (content.length > maxFileSize) return false
            val pathObj = Paths.get(validatedPath)
            val parentDir = pathObj.parent
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir)
            }
            Files.writeString(
                pathObj, content,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
            )
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
            val pathObj = Paths.get(validatedPath)
            val parentDir = pathObj.parent
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir)
            }
            if (Files.exists(pathObj)) {
                return Files.isDirectory(pathObj)
            }
            Files.createDirectory(pathObj)
            Files.exists(pathObj)
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

    actual override fun pickDirectory(): String? {
        var selectedPath: String? = null
        val task = Runnable {
            val chooser = JFileChooser()
            chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            val result = chooser.showOpenDialog(null)
            if (result == JFileChooser.APPROVE_OPTION) {
                selectedPath = chooser.selectedFile.absolutePath
            }
        }

        if (SwingUtilities.isEventDispatchThread()) {
            task.run()
        } else {
            SwingUtilities.invokeAndWait(task)
        }
        return selectedPath
    }

    private fun validatePath(path: String): String {
        require(path.length <= maxPathLength) { "Path exceeds maximum length" }
        require(!path.contains('\u0000')) { "Path contains null bytes" }
        dangerousPatterns.forEach { pattern ->
            require(!path.contains(pattern)) { "Path contains dangerous pattern: $pattern" }
        }
        val normalized = path.replace(Regex("[/\\\\]+"), "/")
        val expandedPath = expandTilde(normalized)
        val absolutePath = Paths.get(expandedPath).toAbsolutePath().normalize()
        val homePath = Paths.get(homeDir).toAbsolutePath().normalize()
        require(absolutePath.startsWith(homePath)) { "Path must be within user's home directory" }
        return absolutePath.toString()
    }
}
