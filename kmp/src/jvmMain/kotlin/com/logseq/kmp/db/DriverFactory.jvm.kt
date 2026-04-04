package com.logseq.kmp.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File

actual class DriverFactory actual constructor() {
    actual fun createDriver(jdbcUrl: String): SqlDriver {
        // Ensure parent directory exists for file-based URLs
        if (jdbcUrl.startsWith("jdbc:sqlite:") && !jdbcUrl.contains(":memory:")) {
            val path = jdbcUrl.substringAfter("jdbc:sqlite:")
            File(path).parentFile?.mkdirs()
        }
        
        val driver = JdbcSqliteDriver(jdbcUrl)
        
        // Initialize schema if needed (SQLDelight 2.0.x pattern)
        try {
            LogseqDatabase.Schema.create(driver)
        } catch (e: Exception) {
            // Already exists or other error
        }

        // Configuration
        driver.execute(null, "PRAGMA journal_mode=WAL;", 0)
        driver.execute(null, "PRAGMA synchronous=NORMAL;", 0)
        driver.execute(null, "PRAGMA foreign_keys=ON;", 0)
        
        return driver
    }

    actual fun getDatabaseUrl(graphId: String): String {
        val basePath = getDatabaseDirectory()
        return "jdbc:sqlite:$basePath/logseq-graph-$graphId.db"
    }

    actual fun getDatabaseDirectory(): String {
        val os = System.getProperty("os.name").lowercase()
        val userHome = System.getProperty("user.home")

        return when {
            os.contains("win") -> {
                val appData = System.getenv("APPDATA") ?: "$userHome\\AppData\\Roaming"
                "$appData\\Logseq"
            }
            os.contains("mac") -> {
                "$userHome/Library/Application Support/Logseq"
            }
            else -> { // Linux and others
                val xdgData = System.getenv("XDG_DATA_HOME") ?: "$userHome/.local/share"
                "$xdgData/logseq"
            }
        }
    }
}

actual val defaultDatabaseUrl: String
    get() {
        val os = System.getProperty("os.name").lowercase()
        val userHome = System.getProperty("user.home")

        val basePath = when {
            os.contains("win") -> {
                val appData = System.getenv("APPDATA") ?: "$userHome\\AppData\\Roaming"
                "$appData\\Logseq"
            }
            os.contains("mac") -> {
                "$userHome/Library/Application Support/Logseq"
            }
            else -> { // Linux and others
                val xdgData = System.getenv("XDG_DATA_HOME") ?: "$userHome/.local/share"
                "$xdgData/logseq"
            }
        }
        return "jdbc:sqlite:$basePath/logseq.db"
    }
