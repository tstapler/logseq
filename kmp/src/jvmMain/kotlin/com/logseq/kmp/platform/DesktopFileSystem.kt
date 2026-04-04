package com.logseq.kmp.platform

/**
 * Desktop JVM implementation of file system operations.
 * Provides secure file reading, writing, and directory operations.
 * This class is only available on JVM targets.
 */
class DesktopFileSystem : JvmFileSystemBase()
