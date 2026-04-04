package com.logseq.kmp.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import android.content.Context

actual class DriverFactory actual constructor() {
    actual fun createDriver(jdbcUrl: String): SqlDriver {
        val dbName = jdbcUrl.substringAfter("jdbc:sqlite:")
        
        // Get Android context via reflection (since we don't have direct access in library code)
        val context = Class.forName("android.app.Application")
            .let { appClass ->
                val thread = Thread.currentThread()
                val cl = thread.contextClassLoader
                cl.loadClass("android.app.Application")
            }
            .let { appClass ->
                val getApplication = Class.forName("android.app.ActivityThread")
                    .getMethod("getApplication")
                getApplication.invoke(null) as? Context
            }
            ?: throw IllegalStateException("Cannot obtain Android Context")
        
        val driver = AndroidSqliteDriver(
            schema = LogseqDatabase.Schema,
            context = context,
            name = dbName
        )
        
        return driver
    }
}

actual val defaultDatabaseUrl: String
    get() {
        return "jdbc:sqlite:logseq.db"
    }
