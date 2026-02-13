package com.logseq.kmp.platform

interface FileSystem {
    fun getDefaultGraphPath(): String
    fun expandTilde(path: String): String
    fun readFile(path: String): String?
    fun writeFile(path: String, content: String): Boolean
    fun listFiles(path: String): List<String>
    fun listDirectories(path: String): List<String>
    fun fileExists(path: String): Boolean
    fun directoryExists(path: String): Boolean
    fun createDirectory(path: String): Boolean
    fun deleteFile(path: String): Boolean
    fun pickDirectory(): String?
    fun getLastModifiedTime(path: String): Long?
}
