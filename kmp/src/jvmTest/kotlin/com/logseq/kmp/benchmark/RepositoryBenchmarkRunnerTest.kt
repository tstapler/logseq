package com.logseq.kmp.benchmark

import com.logseq.kmp.repository.RepositoryFactoryImpl
import com.logseq.kmp.repository.GraphBackend
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertTrue

class RepositoryBenchmarkRunnerTest {

    @Test
    fun runRepositoryBenchmarks() = runBlocking {
        println("\n" + "=".repeat(60))
        println("🚀 REPOSITORY BENCHMARK EXECUTION")
        println("=".repeat(60))

        val factory = RepositoryFactoryImpl()
        val blockRepo = factory.createBlockRepository(GraphBackend.IN_MEMORY)
        val pageRepo = factory.createPageRepository(GraphBackend.IN_MEMORY)

        val benchmark = RepositoryBenchmark(blockRepo, pageRepo)
        val summary = benchmark.runAllBenchmarks()

        println("\n📋 Detailed Results:")
        summary.results.forEach { result ->
            val status = if (result.success) "✅" else "❌"
            val duration = result.metadata["durationMs"] ?: "N/A"
            println("  $status ${result.operation}: ${duration}ms")
        }

        assertTrue(summary.totalOperations > 0, "Should have run benchmark operations")
        println("\n✅ Benchmark execution completed successfully!")
        println("   Total operations: ${summary.totalOperations}")
        println("   Successful: ${summary.successfulOperations}")
        println("   Average time: ${summary.averageDuration}ms")
    }
}
