package com.logseq.kmp.platform

import android.content.Context
import android.os.Environment
import java.io.File

actual class PlatformFileSystem actual constructor() : JvmFileSystemBase(), FileSystem {
    private var context: Context? = null
    override val homeDir: String by lazy { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)?.absolutePath ?: "/storage/emulated/0/Documents" }

    fun init(context: Context) {
        this.context = context
    }

    actual override fun getDefaultGraphPath(): String = super.getDefaultGraphPath()

    actual override fun expandTilde(path: String): String = super.expandTilde(path)

    actual override fun readFile(path: String): String? = super.readFile(path)

    actual override fun writeFile(path: String, content: String): Boolean = super.writeFile(path, content)

    actual override fun listFiles(path: String): List<String> = super.listFiles(path)

    actual override fun listDirectories(path: String): List<String> = super.listDirectories(path)

    actual override fun fileExists(path: String): Boolean = super.fileExists(path)

    actual override fun directoryExists(path: String): Boolean = super.directoryExists(path)

    actual override fun createDirectory(path: String): Boolean = super.createDirectory(path)

    actual override fun deleteFile(path: String): Boolean = super.deleteFile(path)
    
    actual override fun pickDirectory(): String? = null

    actual override fun getLastModifiedTime(path: String): Long? = super.getLastModifiedTime(path)
}
