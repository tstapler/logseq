package com.logseq.kmp.benchmark

import com.logseq.kmp.model.Block
import com.logseq.kmp.model.Page
import com.logseq.kmp.repository.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlin.time.Duration
import kotlin.time.measureTime

/**
 * Simple benchmarking suite for graph database performance evaluation.
 * Tests basic operations across different repository implementations.
 */
class GraphBenchmark(
    private val blockRepo: BlockRepository,
    private val pageRepo: PageRepository,
    private val referenceRepo: ReferenceRepository
) {

    data class BenchmarkResult(
        val operation: String,
        val duration: Duration,
        val success: Boolean,
        val notes: String = ""
    )

    private val results = mutableListOf<BenchmarkResult>()

    /**
     * Run all benchmark tests
     */
    fun runAllBenchmarks(): List<BenchmarkResult> {
        results.clear()

        runBlocking {
            benchmarkBlockOperations()
            benchmarkHierarchyOperations()
            benchmarkReferenceOperations()
            benchmarkPageOperations()
        }

        return results.toList()
    }

    private suspend fun benchmarkBlockOperations() {
        // Create test blocks
        val testBlocks = (1..100).map { i ->
            Block(
                id = i.toLong(),
                uuid = "test-block-$i",
                pageId = 1L,
                parentId = if (i > 1) (i - 1).toLong() else null,
                leftId = null,
                content = "Test block content $i with some text to make it realistic",
                level = if (i > 1) 1 else 0,
                position = i,
                createdAt = Clock.System.now(),
                updatedAt = Clock.System.now(),
                properties = mapOf("test" to "value$i")
            )
        }

        // Benchmark block creation
        measureOperation("Create 100 blocks") {
            testBlocks.forEach { block ->
                blockRepo.saveBlock(block).getOrThrow()
            }
        }

        // Benchmark block retrieval
        measureOperation("Retrieve single block by UUID") {
            blockRepo.getBlockByUuid("test-block-50").first().getOrThrow()
        }

        // Benchmark bulk retrieval
        measureOperation("Retrieve block children") {
            blockRepo.getBlockChildren("test-block-1").first().getOrThrow()
        }
    }

    private suspend fun benchmarkHierarchyOperations() {
        // Create a deep hierarchy (5 levels, branching factor 3)
        createDeepHierarchy()

        measureOperation("Retrieve block hierarchy (50 blocks)") {
            val hierarchy = blockRepo.getBlockHierarchy("root-block").first().getOrThrow()
            check(hierarchy.size >= 50) { "Expected at least 50 blocks in hierarchy" }
        }

        measureOperation("Retrieve block ancestors") {
            blockRepo.getBlockAncestors("deep-block-5-3-2").first().getOrThrow()
        }

        measureOperation("Retrieve block siblings") {
            blockRepo.getBlockSiblings("deep-block-3-2-1").first().getOrThrow()
        }
    }

    private suspend fun benchmarkReferenceOperations() {
        // Create references between blocks
        (1..50).forEach { i ->
            referenceRepo.addReference("test-block-$i", "test-block-${i % 10 + 1}").getOrThrow()
        }

        measureOperation("Retrieve outgoing references") {
            referenceRepo.getOutgoingReferences("test-block-1").first().getOrThrow()
        }

        measureOperation("Retrieve incoming references") {
            referenceRepo.getIncomingReferences("test-block-1").first().getOrThrow()
        }

        measureOperation("Retrieve all references for block") {
            referenceRepo.getAllReferences("test-block-1").first().getOrThrow()
        }

        measureOperation("Find most connected blocks") {
            val connected = referenceRepo.getMostConnectedBlocks(10).first().getOrThrow()
            check(connected.isNotEmpty()) { "Expected some connected blocks" }
        }
    }

    private suspend fun benchmarkPageOperations() {
        // Create test pages
        val testPages = (1..20).map { i ->
            Page(
                id = i.toLong(),
                uuid = "test-page-$i",
                name = "Test Page $i",
                namespace = if (i % 2 == 0) "test" else null,
                filePath = "/test/page$i.md",
                createdAt = Clock.System.now(),
                updatedAt = Clock.System.now(),
                properties = mapOf("type" to "test")
            )
        }

        // Benchmark page operations
        measureOperation("Create 20 pages") {
            testPages.forEach { page ->
                pageRepo.savePage(page).getOrThrow()
            }
        }

        measureOperation("Retrieve page by UUID") {
            pageRepo.getPageByUuid("test-page-10").first().getOrThrow()
        }

        measureOperation("Retrieve pages in namespace") {
            pageRepo.getPagesInNamespace("test").first().getOrThrow()
        }

        measureOperation("Retrieve recent pages") {
            pageRepo.getRecentPages(10).first().getOrThrow()
        }
    }

    private suspend fun createDeepHierarchy() {
        // Create root
        val rootBlock = Block(
            id = 1000L,
            uuid = "root-block",
            pageId = 1L,
            parentId = null,
            leftId = null,
            content = "Root block",
            level = 0,
            position = 0,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
            properties = emptyMap()
        )
        blockRepo.saveBlock(rootBlock).getOrThrow()

        // Create 5 levels deep with branching factor 3
        createHierarchyLevel("root-block", 1, 3, 5)
    }

    private suspend fun createHierarchyLevel(parentUuid: String, level: Int, branching: Int, maxDepth: Int) {
        if (level > maxDepth) return

        for (i in 1..branching) {
            val block = Block(
                id = (1000 + level * 100 + i).toLong(),
                uuid = "deep-block-$level-$i-${parentUuid.hashCode()}",
                pageId = 1L,
                parentId = null, // Will be set by saveBlock
                leftId = null,
                content = "Block at level $level, branch $i",
                level = level,
                position = i,
                createdAt = Clock.System.now(),
                updatedAt = Clock.System.now(),
                properties = emptyMap()
            )
            blockRepo.saveBlock(block).getOrThrow()

            // Recursively create children
            createHierarchyLevel(block.uuid, level + 1, branching, maxDepth)
        }
    }

    private suspend fun measureOperation(operationName: String, block: suspend () -> Unit) {
        try {
            val duration = measureTime {
                block()
            }
            results.add(BenchmarkResult(operationName, duration, true))
            println("✓ $operationName: ${duration.inWholeMilliseconds}ms")
        } catch (e: Exception) {
            results.add(BenchmarkResult(operationName, Duration.ZERO, false, e.message ?: "Unknown error"))
            println("✗ $operationName: FAILED - ${e.message}")
        }
    }

    /**
     * Get summary statistics
     */
    fun getSummary(): BenchmarkSummary {
        val successful = results.filter { it.success }
        val failed = results.filter { !it.success }

        return BenchmarkSummary(
            totalOperations = results.size,
            successfulOperations = successful.size,
            failedOperations = failed.size,
            totalDuration = successful.sumOf { it.duration.inWholeMilliseconds },
            averageDuration = if (successful.isNotEmpty()) {
                successful.sumOf { it.duration.inWholeMilliseconds } / successful.size
            } else 0,
            failureReasons = failed.map { it.notes }
        )
    }
}

data class BenchmarkSummary(
    val totalOperations: Int,
    val successfulOperations: Int,
    val failedOperations: Int,
    val totalDuration: Long, // milliseconds
    val averageDuration: Long, // milliseconds
    val failureReasons: List<String>
)

/**
 * Run benchmarks across multiple repository backends
 */
suspend fun compareBackends(
    backends: Map<String, RepositorySet>,
    operations: List<String> = listOf("block-hierarchy", "references", "pages")
): Map<String, BenchmarkSummary> {

    val results = mutableMapOf<String, BenchmarkSummary>()

    for ((backendName, repoSet) in backends) {
        println("\n=== Running benchmarks for $backendName ===")

        val benchmark = GraphBenchmark(
            repoSet.blockRepository,
            repoSet.pageRepository,
            repoSet.referenceRepository
        )

        val benchmarkResults = benchmark.runAllBenchmarks()
        val summary = benchmark.getSummary()

        results[backendName] = summary

        println("$backendName Summary:")
        println("  Total operations: ${summary.totalOperations}")
        println("  Successful: ${summary.successfulOperations}")
        println("  Failed: ${summary.failedOperations}")
        println("  Total time: ${summary.totalDuration}ms")
        println("  Average time: ${summary.averageDuration}ms")

        if (summary.failureReasons.isNotEmpty()) {
            println("  Failures:")
            summary.failureReasons.forEach { reason ->
                println("    - $reason")
            }
        }
    }

    return results
}</content>
<parameter name="filePath">kmp/src/jvmTest/kotlin/com/logseq/kmp/benchmark/GraphBenchmark.kt