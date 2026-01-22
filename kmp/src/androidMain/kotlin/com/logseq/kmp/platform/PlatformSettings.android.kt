package com.logseq.kmp.platform

import android.content.Context
import android.content.SharedPreferences

object LogseqContext {
    private var _context: Context? = null
    val context: Context
        get() = _context ?: throw IllegalStateException("LogseqContext not initialized. Call init(context) first.")

    fun init(context: Context) {
        _context = context.applicationContext
    }
}

actual class PlatformSettings actual constructor() {
    private val prefs: SharedPreferences by lazy {
        try {
            LogseqContext.context.getSharedPreferences("logseq_prefs", Context.MODE_PRIVATE)
        } catch (e: Exception) {
            // Fallback for tests or if not initialized
            throw IllegalStateException("Context not initialized", e)
        }
    }

    actual fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return try {
            prefs.getBoolean(key, defaultValue)
        } catch (e: Exception) { defaultValue }
    }

    actual fun putBoolean(key: String, value: Boolean) {
        try {
            prefs.edit().putBoolean(key, value).apply()
        } catch (e: Exception) { }
    }

    actual fun getString(key: String, defaultValue: String): String {
        return try {
            prefs.getString(key, defaultValue) ?: defaultValue
        } catch (e: Exception) { defaultValue }
    }

    actual fun putString(key: String, value: String) {
        try {
            prefs.edit().putString(key, value).apply()
        } catch (e: Exception) { }
    }
}
