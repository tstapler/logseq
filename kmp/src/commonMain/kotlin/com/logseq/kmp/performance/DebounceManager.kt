package com.logseq.kmp.performance

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Manages debounced execution of named tasks.
 * Used to prevent excessive disk writes or heavy operations.
 */
class DebounceManager(
    private val scope: CoroutineScope,
    private val delayMs: Long = 300L
) {
    private val jobs = mutableMapOf<String, Job>()
    private val actions = mutableMapOf<String, suspend () -> Unit>()
    private val startTimes = mutableMapOf<String, kotlinx.datetime.Instant>()
    private val mutex = Mutex()

    fun debounce(key: String, action: suspend () -> Unit) {
        val now = kotlinx.datetime.Clock.System.now()
        scope.launch {
            mutex.withLock {
                jobs[key]?.cancel()
                actions[key] = action
                if (!startTimes.containsKey(key)) {
                    startTimes[key] = now
                }
                jobs[key] = scope.launch {
                    delay(delayMs)
                    val act = mutex.withLock {
                        val a = actions.remove(key)
                        jobs.remove(key)
                        a
                    }
                    val startTime = mutex.withLock { startTimes.remove(key) }
                    if (act != null) {
                        act()
                        if (startTime != null) {
                            Metrics.instance.recordLatency(
                                "debounce.latency",
                                kotlinx.datetime.Clock.System.now() - startTime,
                                mapOf("key" to key)
                            )
                        }
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
            startTimes.clear()
        }
    }

    suspend fun flushAll(): Int {
        val pending = mutex.withLock {
            val snapshot = actions.map { (k, v) -> k to (v to startTimes.remove(k)) }
            jobs.values.forEach { it.cancel() }
            jobs.clear()
            actions.clear()
            snapshot
        }
        pending.forEach { (key, pair) -> 
            val (action, startTime) = pair
            action.invoke()
            if (startTime != null) {
                Metrics.instance.recordLatency(
                    "debounce.flush_latency",
                    kotlinx.datetime.Clock.System.now() - startTime,
                    mapOf("key" to key)
                )
            }
        }
        return pending.size
    }
}
