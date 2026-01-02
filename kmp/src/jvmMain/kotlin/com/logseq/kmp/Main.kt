package com.logseq.kmp

import com.logseq.kmp.benchmark.GraphBenchmark
import com.logseq.kmp.benchmark.compareBackends
import com.logseq.kmp.loader.LogseqDataLoader
import com.logseq.kmp.repository.*
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Main entry point for Graph Database Performance Evaluation.
 * Loads personal Logseq data and runs benchmarks across different backends.
 */
fun main() = runBlocking {
    println("🚀 Graph Database Performance Evaluation")
    println("========================================")

    val personalWikiPath = System.getProperty("user.home") + "/Documents/personal-wiki/logseq"

    if (!File(personalWikiPath).exists()) {
        println("❌ Personal Logseq wiki not found at: $personalWikiPath")
        println("Please ensure your Logseq graph is at ~/Documents/personal-wiki/logseq")
        return@runBlocking
    }

    println("📁 Found personal wiki at: $personalWikiPath")

    // Initialize backends
    val backends = initializeBackends()

    // Load test data
    println("\n📥 Loading test data...")
    val dataLoader = LogseqDataLoader(
        backends["in-memory"]!!.blockRepository,
        backends["in-memory"]!!.pageRepository,
        backends["in-memory"]!!.propertyRepository,
        backends["in-memory"]!!.referenceRepository
    )

    val loadResult = dataLoader.loadGraph(personalWikiPath)
    loadResult.fold(
        onSuccess = { stats ->
            println("✅ Data loaded successfully!")
            println("   Pages: ${stats.pagesLoaded}")
            println("   Blocks: ${stats.blocksLoaded}")
            println("   References: ${stats.referencesFound}")
            println("   Properties: ${stats.propertiesFound}")

            if (stats.errors.isNotEmpty()) {
                println("   ⚠️  Errors: ${stats.errors.size}")
                stats.errors.forEach { println("      - $it") }
            }
        },
        onFailure = { error ->
            println("❌ Failed to load data: ${error.message}")
            return@runBlocking
        }
    )

    // Run benchmarks
    println("\n🏃 Running performance benchmarks...")

    val benchmarkResults = compareBackends(backends)

    // Generate report
    println("\n📊 PERFORMANCE EVALUATION RESULTS")
    println("==================================")

    val sortedResults = benchmarkResults.entries.sortedBy { it.value.averageDuration }

    sortedResults.forEach { (backend, summary) ->
        println("\n$backend:")
        println("  Success Rate: ${summary.successfulOperations}/${summary.totalOperations} (${(summary.successfulOperations.toDouble() / summary.totalOperations * 100).toInt()}%)")
        println("  Total Time: ${summary.totalDuration}ms")
        println("  Average Operation Time: ${summary.averageDuration}ms")

        if (summary.failedOperations > 0) {
            println("  Failed Operations:")
            summary.failureReasons.forEach { reason ->
                println("    - $reason")
            }
        }
    }

    // Recommendations
    println("\n🎯 RECOMMENDATIONS")
    println("==================")

    if (sortedResults.isNotEmpty()) {
        val fastest = sortedResults.first()
        val slowest = sortedResults.last()

        println("🏆 Fastest Backend: ${fastest.key} (${fastest.value.averageDuration}ms avg)")
        println("🐌 Slowest Backend: ${slowest.key} (${slowest.value.averageDuration}ms avg)")

        val improvement = if (fastest.value.averageDuration > 0) {
            ((slowest.value.averageDuration - fastest.value.averageDuration).toDouble() / slowest.value.averageDuration * 100).toInt()
        } else 0

        println("📈 Potential Improvement: $improvement% faster with ${fastest.key}")

        // Backend-specific recommendations
        when (fastest.key) {
            "in-memory" -> println("💡 In-memory is fastest but not persistent. Consider for read-heavy workloads.")
            "kuzu" -> println("💡 Kuzu shows strong graph database performance. Recommended for production.")
            "neo4j" -> println("💡 Neo4j provides industry-standard Cypher support with good performance.")
            "sqldelight" -> println("💡 SQLDelight performs well for relational operations but may need CTE optimizations.")
        }
    }

    println("\n✅ Evaluation complete! Check the detailed results above for data-driven decisions.")
}

/**
 * Initialize all backend implementations
 */
private fun initializeBackends(): Map<String, RepositorySet> {
    val backends = mutableMapOf<String, RepositorySet>()

    try {
        // In-memory (baseline)
        backends["in-memory"] = Repositories.set(GraphBackend.IN_MEMORY)
        println("✅ In-memory backend initialized")
    } catch (e: Exception) {
        println("❌ Failed to initialize in-memory backend: ${e.message}")
    }

    // TODO: Add other backends when implementations are complete
    // try {
    //     // SQLDelight
    //     val sqlDelightDb = ... // Initialize SQLDelight database
    //     Repositories.configure(sqlDelightDb)
    //     backends["sqldelight"] = Repositories.set(GraphBackend.SQLDELIGHT)
    //     println("✅ SQLDelight backend initialized")
    // } catch (e: Exception) {
    //     println("❌ Failed to initialize SQLDelight backend: ${e.message}")
    // }

    // try {
    //     // Kuzu
    //     val kuzuConnection = ... // Initialize Kuzu connection
    //     backends["kuzu"] = RepositorySet(
    //         blockRepository = KuzuBlockRepository(kuzuConnection),
    //         pageRepository = KuzuPageRepository(kuzuConnection),
    //         propertyRepository = KuzuPropertyRepository(kuzuConnection),
    //         referenceRepository = KuzuReferenceRepository(kuzuConnection),
    //         searchRepository = KuzuSearchRepository(kuzuConnection)
    //     )
    //     println("✅ Kuzu backend initialized")
    // } catch (e: Exception) {
    //     println("❌ Failed to initialize Kuzu backend: ${e.message}")
    // }

    // try {
    //     // Neo4j
    //     val neo4jDriver = ... // Initialize Neo4j driver
    //     backends["neo4j"] = RepositorySet(
    //         blockRepository = Neo4jBlockRepository(neo4jDriver),
    //         pageRepository = Neo4jPageRepository(neo4jDriver),
    //         propertyRepository = Neo4jPropertyRepository(neo4jDriver),
    //         referenceRepository = Neo4jReferenceRepository(neo4jDriver),
    //         searchRepository = Neo4jSearchRepository(neo4jDriver)
    //     )
    //     println("✅ Neo4j backend initialized")
    // } catch (e: Exception) {
    //     println("❌ Failed to initialize Neo4j backend: ${e.message}")
    // }

    return backends
}</content>
<parameter name="filePath">kmp/src/jvmMain/kotlin/com/logseq/kmp/Main.kt