package com.logseq.kmp.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory

actual class DriverFactory actual constructor() {
    companion object {
        internal var staticContext: Context? = null

        fun setContext(context: Context) {
            staticContext = context.applicationContext
        }
    }

    actual fun init(context: Any) {
        if (context is Context) {
            setContext(context)
        }
    }

    actual fun createDriver(jdbcUrl: String): SqlDriver {
        val dbName = jdbcUrl.substringAfter("jdbc:sqlite:")
        
        val context = staticContext ?: throw IllegalStateException("DriverFactory must be initialized with a Context before creating a driver. Call DriverFactory().init(context) first.")
        
        // Ensure parent directory exists for absolute paths
        if (dbName.startsWith("/")) {
            java.io.File(dbName).parentFile?.mkdirs()
        }

        val driver = AndroidSqliteDriver(
            schema = LogseqDatabase.Schema,
            context = context,
            name = dbName,
            factory = RequerySQLiteOpenHelperFactory()
        )
        
        return driver
    }
}

actual val defaultDatabaseUrl: String
    get() {
        return "jdbc:sqlite:logseq.db"
    }
