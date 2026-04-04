package com.logseq.kmp.performance

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Manages debounced execution of tasks identified by a key.
 * Used to throttle high-frequency updates like text typing before they hit the database.
 */
class DebounceManager(
    private val scope: CoroutineScope,
    private val delayMs: Long = 300L
) {
    private val jobs = mutableMapOf<String, Job>()
    private val actions = mutableMapOf<String, suspend () -> Unit>()
    private val mutex = Mutex()

    fun debounce(key: String, action: suspend () -> Unit) {
        scope.launch {
            mutex.withLock {
                jobs[key]?.cancel()
                actions[key] = action
                jobs[key] = scope.launch {
                    delay(delayMs)
                    mutex.withLock {
                        actions.remove(key)
                        action()
                        jobs.remove(key)
                    }
                }
            }
        }
    }

    suspend fun cancelAll() {
        mutex.withLock {
            jobs.values.forEach { it.cancel() }
            jobs.clear()
            actions.clear()
        }
    }

    suspend fun flushAll() {
        val pending = mutex.withLock {
            val snapshot = actions.values.toList()
            jobs.values.forEach { it.cancel() }
            jobs.clear()
            actions.clear()
            snapshot
        }
        pending.forEach { it.invoke() }
    }
}
