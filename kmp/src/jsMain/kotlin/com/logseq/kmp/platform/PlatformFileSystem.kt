package com.logseq.kmp.platform

import kotlinx.browser.window

actual class PlatformFileSystem actual constructor() {
    private val maxPathLength = 4096
    private val maxFileSize = 100 * 1024 * 1024
    private val dangerousPatterns = listOf("..", "../", "..\\", "\u0000")
    private val homeDir: String = "/logseq"

    actual fun getDefaultGraphPath(): String = homeDir

    actual fun expandTilde(path: String): String {
        return if (path.startsWith("~")) {
            path.replaceFirst("~", homeDir)
        } else {
            path
        }
    }

    actual fun readFile(path: String): String? {
        // Browser environment - simulated
        console.log("Reading file: $path")
        return null
    }

    actual fun writeFile(path: String, content: String): Boolean {
        // Browser environment - simulated
        console.log("Writing file: $path")
        return true
    }

    actual fun listFiles(path: String): List<String> {
        // Browser environment - simulated
        console.log("Listing files: $path")
        return emptyList()
    }

    actual fun listDirectories(path: String): List<String> {
        // Browser environment - simulated
        console.log("Listing directories: $path")
        return emptyList()
    }

    actual fun fileExists(path: String): Boolean {
        // Browser environment - simulated
        console.log("Checking file exists: $path")
        return false
    }

    actual fun directoryExists(path: String): Boolean {
        // Browser environment - simulated
        console.log("Checking directory exists: $path")
        return false
    }

    actual fun createDirectory(path: String): Boolean {
        // Browser environment - simulated
        console.log("Creating directory: $path")
        return true
    }

    actual fun deleteFile(path: String): Boolean {
        // Browser environment - simulated
        console.log("Deleting file: $path")
        return true
    }

    actual fun pickDirectory(): String? {
        console.log("Picking directory not supported in JS yet")
        return null
    }

    actual fun getLastModifiedTime(path: String): Long? {
        // Browser environment - not supported
        console.log("Getting last modified time not supported in JS: $path")
        return null
    }
}
