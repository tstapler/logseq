package com.logseq.kmp.performance

import kotlin.time.Duration

/**
 * Interface for platform-specific metrics tracking.
 * Provides a common API for emitting metrics across different targets.
 */
interface PlatformMetrics {
    /**
     * Records a latency metric.
     * @param name The name of the metric.
     * @param duration The duration to record.
     * @param attributes Optional key-value pairs for additional context.
     */
    fun recordLatency(name: String, duration: Duration, attributes: Map<String, String> = emptyMap())

    /**
     * Increments a counter metric.
     * @param name The name of the metric.
     * @param value The amount to increment by (default 1).
     * @param attributes Optional key-value pairs for additional context.
     */
    fun incrementCounter(name: String, value: Long = 1L, attributes: Map<String, String> = emptyMap())
}

/**
 * Expect declaration for PlatformMetrics provider.
 */
expect class PlatformMetricsProvider() {
    fun getMetrics(): PlatformMetrics
}

/**
 * Global access to platform metrics.
 */
object Metrics {
    val instance: PlatformMetrics by lazy {
        PlatformMetricsProvider().getMetrics()
    }
}
