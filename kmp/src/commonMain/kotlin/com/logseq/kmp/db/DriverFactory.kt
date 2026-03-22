package com.logseq.kmp.db

import app.cash.sqldelight.db.SqlDriver

/**
 * Platform-specific driver factory for SQLDelight.
 */
expect class DriverFactory() {
    /**
     * Create a SQLDelight driver for the platform.
     * @param jdbcUrl The JDBC connection string (e.g. "jdbc:sqlite:logseq.db" or "jdbc:sqlite::memory:")
     */
    fun createDriver(jdbcUrl: String): SqlDriver
}

/**
 * Returns the default JDBC URL for the platform.
 */
expect val defaultDatabaseUrl: String

/**
 * Extension function to initialize the database with platform-specific driver.
 */
fun createDatabase(driverFactory: DriverFactory, jdbcUrl: String): LogseqDatabase {
    val driver = driverFactory.createDriver(jdbcUrl)
    return LogseqDatabase(driver)
}
