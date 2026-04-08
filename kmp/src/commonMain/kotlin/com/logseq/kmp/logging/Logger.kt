package com.logseq.kmp.logging

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Clock
import kotlin.time.Instant

enum class LogLevel {
    DEBUG, INFO, WARN, ERROR
}

data class LogEntry(
    val level: LogLevel,
    val tag: String,
    val message: String,
    val timestamp: Instant = Clock.System.now(),
    val throwable: Throwable? = null
)

object LogManager {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()
    
    private const val MAX_LOGS = 1000

    fun addLog(entry: LogEntry) {
        val currentLogs = _logs.value.toMutableList()
        currentLogs.add(0, entry) // Add to beginning for newest first
        if (currentLogs.size > MAX_LOGS) {
            currentLogs.removeAt(currentLogs.lastIndex)
        }
        _logs.value = currentLogs
        
        // Also print to standard output for development
        println("[${entry.level}] ${entry.tag}: ${entry.message}")
        entry.throwable?.printStackTrace()
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }
}

class Logger(private val tag: String) {
    fun debug(message: String) = log(LogLevel.DEBUG, message)
    fun info(message: String) = log(LogLevel.INFO, message)
    fun warn(message: String, throwable: Throwable? = null) = log(LogLevel.WARN, message, throwable)
    fun error(message: String, throwable: Throwable? = null) = log(LogLevel.ERROR, message, throwable)

    private fun log(level: LogLevel, message: String, throwable: Throwable? = null) {
        LogManager.addLog(LogEntry(level, tag, message, throwable = throwable))
    }
}
