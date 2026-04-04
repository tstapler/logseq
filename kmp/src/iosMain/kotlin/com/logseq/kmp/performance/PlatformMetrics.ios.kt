package com.logseq.kmp.performance

import kotlin.time.Duration

/**
 * No-op implementation of PlatformMetrics for iOS.
 */
class IosPlatformMetrics : PlatformMetrics {
    override fun recordLatency(name: String, duration: Duration, attributes: Map<String, String>) {}
    override fun incrementCounter(name: String, value: Long, attributes: Map<String, String>) {}
}

actual class PlatformMetricsProvider actual constructor() {
    actual fun getMetrics(): PlatformMetrics = IosPlatformMetrics()
}
