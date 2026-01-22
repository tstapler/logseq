package com.logseq.kmp

import com.logseq.kmp.db.LogseqDatabase
// import com.logseq.kmp.repository.Repositories
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File

/**
 * Production database configuration for Logseq KMP application.
 * Handles SQLDelight setup, migration, and repository initialization.
 */
object DatabaseConfig {

    private const val DATABASE_NAME = "logseq.db"
    private const val SCHEMA_VERSION = 1L

    /**
     * Initialize the production database with SQLDelight
     */
    fun initializeDatabase(): LogseqDatabase {
        val driver = createDriver()
        val database = LogseqDatabase(driver)

        // Run migrations if needed
        migrateDatabase(driver, database)

        // Configure repositories to use SQLDelight
        // TODO: Repositories.configure(database)

        return database
    }

    /**
     * Create SQLDelight driver for SQLite
     */
    private fun createDriver(): SqlDriver {
        val databasePath = getDatabasePath()
        val driver = JdbcSqliteDriver("jdbc:sqlite:$databasePath")

        // Enable WAL mode for better concurrency
        driver.execute(null, "PRAGMA journal_mode=WAL;", 0)
        driver.execute(null, "PRAGMA synchronous=NORMAL;", 0)
        driver.execute(null, "PRAGMA cache_size=1000000;", 0) // 1GB cache
        driver.execute(null, "PRAGMA temp_store=MEMORY;", 0)
        driver.execute(null, "PRAGMA mmap_size=268435456;", 0) // 256MB mmap

        return driver
    }

    /**
     * Get database file path (platform-specific)
     */
    private fun getDatabasePath(): String {
        val os = System.getProperty("os.name").lowercase()
        val userHome = System.getProperty("user.home")

        return when {
            os.contains("win") -> {
                val appData = System.getenv("APPDATA") ?: "$userHome\\AppData\\Roaming"
                "$appData\\Logseq\\logseq.db"
            }
            os.contains("mac") -> {
                "$userHome/Library/Application Support/Logseq/logseq.db"
            }
            else -> { // Linux and others
                val xdgData = System.getenv("XDG_DATA_HOME") ?: "$userHome/.local/share"
                "$xdgData/logseq/logseq.db"
            }
        }
    }

    /**
     * Run database migrations
     */
    private fun migrateDatabase(driver: SqlDriver, database: LogseqDatabase) {
        try {
            // Create tables if they don't exist
            LogseqDatabase.Schema.create(driver)

            // Check current version
            val currentVersion = getCurrentVersion(driver)

            if (currentVersion < SCHEMA_VERSION) {
                println("Migrating database from version $currentVersion to $SCHEMA_VERSION")

                // For now, we'll recreate tables on schema changes
                // In production, you'd implement proper migration scripts
                recreateTables(driver, database)

                setCurrentVersion(driver, SCHEMA_VERSION)
                println("Database migration completed")
            }

        } catch (e: Exception) {
            println("Error during database migration: ${e.message}")
            throw e
        }
    }

    /**
     * Get current database version
     */
    private fun getCurrentVersion(driver: SqlDriver): Long {
        // TODO: Implement with correct SQLDelight 2.0 API
        return 1L // Assume version 1 for now
    }

    /**
     * Set database version
     */
    private fun setCurrentVersion(driver: SqlDriver, version: Long) {
        // TODO: Implement with correct SQLDelight 2.0 API
    }

    /**
     * Recreate all tables (for development - in production use proper migrations)
     */
    private fun recreateTables(driver: SqlDriver, database: LogseqDatabase) {
        // TODO: Implement with correct SQLDelight 2.0 API
        // Recreate schema
        LogseqDatabase.Schema.create(driver)
    }

    /**
     * Get database statistics
     */
    fun getDatabaseStats(database: LogseqDatabase): DatabaseStats {
        // Simplified for now - return basic stats
        return DatabaseStats(0L, 0L, 0L, 0L, 0L)
    }

    /**
     * Get database file size
     */
    private fun getDatabaseSize(): Long {
        return try {
            File(getDatabasePath()).length()
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Clean shutdown
     */
    fun shutdown(driver: SqlDriver) {
        try {
            driver.close()
        } catch (e: Exception) {
            println("Error closing database: ${e.message}")
        }
    }
}

data class DatabaseStats(
    val pageCount: Long,
    val blockCount: Long,
    val propertyCount: Long,
    val referenceCount: Long,
    val databaseSizeBytes: Long
) {
    val databaseSizeMB: Double
        get() = databaseSizeBytes / (1024.0 * 1024.0)

    override fun toString(): String {
        return """
        Database Statistics:
        - Pages: $pageCount
        - Blocks: $blockCount
        - Properties: $propertyCount
        - References: $referenceCount
        - Database Size: ${"%.2f".format(databaseSizeMB)} MB
		""".trimIndent()
	}
}
