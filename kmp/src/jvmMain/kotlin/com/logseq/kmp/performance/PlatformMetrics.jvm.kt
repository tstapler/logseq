package com.logseq.kmp.performance

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.common.AttributesBuilder
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.metrics.LongCounter
import io.opentelemetry.api.metrics.DoubleHistogram
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.exporter.logging.LoggingMetricExporter
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader
import java.time.Duration as JDuration
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/**
 * OpenTelemetry implementation of PlatformMetrics for JVM targets.
 */
class JvmPlatformMetrics : PlatformMetrics {
    private val meter: Meter = GlobalOpenTelemetry.get().getMeter("com.logseq.kmp")
    
    // Cache common instruments
    private val counters = mutableMapOf<String, LongCounter>()
    private val histograms = mutableMapOf<String, DoubleHistogram>()

    override fun recordLatency(name: String, duration: Duration, attributes: Map<String, String>) {
        val histogram = histograms.getOrPut(name) {
            meter.histogramBuilder(name)
                .setDescription("Latency of $name")
                .setUnit("ms")
                .build()
        }
        histogram.record(duration.toDouble(kotlin.time.DurationUnit.MILLISECONDS), toAttributes(attributes))
    }

    override fun incrementCounter(name: String, value: Long, attributes: Map<String, String>) {
        val counter = counters.getOrPut(name) {
            meter.counterBuilder(name)
                .setDescription("Count of $name")
                .build()
        }
        counter.add(value, toAttributes(attributes))
    }

    private fun toAttributes(map: Map<String, String>): Attributes {
        val builder = Attributes.builder()
        map.forEach { (k, v) ->
            builder.put(k, v)
        }
        return builder.build()
    }

    companion object {
        private var initialized = false

        fun init() {
            if (initialized) return
            
            // Basic OTel SDK setup for logging metrics (can be replaced with OTLP later)
            val resource = Resource.getDefault().toBuilder()
                .put(AttributeKey.stringKey("service.name"), "logseq-kmp")
                .build()

            val meterProvider = SdkMeterProvider.builder()
                .setResource(resource)
                .registerMetricReader(PeriodicMetricReader.builder(LoggingMetricExporter.create()).setInterval(JDuration.ofSeconds(60)).build())
                .build()

            OpenTelemetrySdk.builder()
                .setMeterProvider(meterProvider)
                .buildAndRegisterGlobal()
            
            initialized = true
        }
    }
}

actual class PlatformMetricsProvider actual constructor() {
    init {
        JvmPlatformMetrics.init()
    }
    actual fun getMetrics(): PlatformMetrics = JvmPlatformMetrics()
}
