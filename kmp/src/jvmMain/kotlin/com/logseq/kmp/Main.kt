package com.logseq.kmp

import com.logseq.kmp.benchmark.GraphBenchmark
import com.logseq.kmp.benchmark.compareBackends
import com.logseq.kmp.loader.LogseqDataLoader
import com.logseq.kmp.repository.*
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Production main entry point for Logseq KMP application.
 * Initializes SQLDelight database and loads personal Logseq data.
 */
fun main() = runBlocking {
    println("🚀 Logseq KMP - Production Database Setup")
    println("==========================================")

    try {
        // Initialize production database
        println("📊 Initializing SQLDelight database...")
        val database = DatabaseConfig.initializeDatabase()
        println("✅ Database initialized successfully")

        // Show database statistics
        val stats = DatabaseConfig.getDatabaseStats(database)
        println("\n$stats")

        // Check for personal wiki data
        val personalWikiPath = System.getProperty("user.home") + "/Documents/personal-wiki/logseq"

        if (!File(personalWikiPath).exists()) {
            println("⚠️  Personal Logseq wiki not found at: $personalWikiPath")
            println("   You can still test with sample data or create the directory structure.")

            // Test with empty database
            testDatabaseOperations(database)
            return@runBlocking
        }

        println("📁 Found personal wiki at: $personalWikiPath")

        // Load personal Logseq data
        println("\n📥 Loading personal Logseq data...")
        loadPersonalData(personalWikiPath, database)

        // Run performance validation
        println("\n🏃 Running performance validation...")
        validatePerformance(database)

        println("\n✅ Production setup complete!")
        println("   Your Logseq KMP application is ready for development.")

    } catch (e: Exception) {
        println("❌ Error during setup: ${e.message}")
        e.printStackTrace()
    } finally {
        // Note: In a real application, you'd manage database lifecycle properly
        println("\n🔄 Database connection remains active for application use")
    }
}

/**
 * Load personal Logseq data into the database
 */
private suspend fun loadPersonalData(personalWikiPath: String, database: com.logseq.kmp.db.LogseqDatabase) {
    // Create data loader with SQLDelight repositories
    val dataLoader = LogseqDataLoader(
        SqlDelightBlockRepository(database),
        SqlDelightPageRepository(database),
        SqlDelightPropertyRepository(database),
        SqlDelightReferenceRepository(database)
    )

    val loadResult = dataLoader.loadGraph(personalWikiPath)
    loadResult.fold(
        onSuccess = { stats ->
            println("✅ Personal data loaded successfully!")
            println("   Pages: ${stats.pagesLoaded}")
            println("   Blocks: ${stats.blocksLoaded}")
            println("   References: ${stats.referencesFound}")
            println("   Properties: ${stats.propertiesFound}")

            if (stats.errors.isNotEmpty()) {
                println("   ⚠️  Load errors: ${stats.errors.size}")
                stats.errors.take(5).forEach { println("      - $it") }
                if (stats.errors.size > 5) {
                    println("      ... and ${stats.errors.size - 5} more")
                }
            }

            // Show updated statistics
            val updatedStats = DatabaseConfig.getDatabaseStats(database)
            println("\n📊 Updated database statistics:")
            println("$updatedStats")
        },
        onFailure = { error ->
            println("❌ Failed to load personal data: ${error.message}")
            println("   Testing with empty database instead...")
            testDatabaseOperations(database)
        }
    )
}

/**
 * Test basic database operations with sample data
 */
private suspend fun testDatabaseOperations(database: com.logseq.kmp.db.LogseqDatabase) {
    println("\n🧪 Testing database operations with sample data...")

    // Get repositories
    val blockRepo = SqlDelightBlockRepository(database)
    val pageRepo = SqlDelightPageRepository(database)

    // Create sample page
    val samplePage = com.logseq.kmp.model.Page(
        id = 1L,
        uuid = "sample-page-uuid",
        name = "Sample Page",
        namespace = null,
        filePath = "/sample/page.md",
        createdAt = kotlinx.datetime.Clock.System.now(),
        updatedAt = kotlinx.datetime.Clock.System.now(),
        properties = mapOf("type" to "sample")
    )

    // Test page operations
    val pageResult = pageRepo.savePage(samplePage)
    pageResult.fold(
        onSuccess = { println("✅ Sample page created successfully") },
        onFailure = { println("❌ Failed to create sample page: ${it.message}") }
    )

    val retrievedPage = pageRepo.getPageByUuid("sample-page-uuid").first()
    retrievedPage.fold(
        onSuccess = { page ->
            if (page != null) {
                println("✅ Sample page retrieved successfully: ${page.name}")
            } else {
                println("❌ Sample page not found")
            }
        },
        onFailure = { println("❌ Failed to retrieve sample page: ${it.message}") }
    )

    // Create sample block
    val sampleBlock = com.logseq.kmp.model.Block(
        id = 1L,
        uuid = "sample-block-uuid",
        pageId = 1L,
        parentId = null,
        leftId = null,
        content = "This is a sample block for testing",
        level = 0,
        position = 0,
        createdAt = kotlinx.datetime.Clock.System.now(),
        updatedAt = kotlinx.datetime.Clock.System.now(),
        properties = emptyMap()
    )

    // Test block operations
    val blockResult = blockRepo.saveBlock(sampleBlock)
    blockResult.fold(
        onSuccess = { println("✅ Sample block created successfully") },
        onFailure = { println("❌ Failed to create sample block: ${it.message}") }
    )

    val retrievedBlock = blockRepo.getBlockByUuid("sample-block-uuid").first()
    retrievedBlock.fold(
        onSuccess = { block ->
            if (block != null) {
                println("✅ Sample block retrieved successfully: ${block.content.take(30)}...")
            } else {
                println("❌ Sample block not found")
            }
        },
        onFailure = { println("❌ Failed to retrieve sample block: ${it.message}") }
    )

    println("✅ Basic database operations test completed")
}

/**
 * Run performance validation tests
 */
private suspend fun validatePerformance(database: com.logseq.kmp.db.LogseqDatabase) {
    println("\n⚡ Running performance validation...")

    // Get repositories
    val blockRepo = SqlDelightBlockRepository(database)
    val pageRepo = SqlDelightPageRepository(database)
    val referenceRepo = SqlDelightReferenceRepository(database)

    // Create benchmark
    val benchmark = GraphBenchmark(blockRepo, pageRepo, referenceRepo)

    // Run basic benchmark
    val results = benchmark.runAllBenchmarks()

    println("📊 Performance validation results:")
    val successful = results.count { it.success }
    val failed = results.size - successful

    println("   Operations tested: ${results.size}")
    println("   Successful: $successful")
    println("   Failed: $failed")

    if (failed > 0) {
        println("   Failed operations:")
        results.filter { !it.success }.forEach { result ->
            println("     - ${result.operation}: ${result.notes}")
        }
    }

    // Show some timing examples
    results.filter { it.success }.take(3).forEach { result ->
        println("   ✅ ${result.operation}: ${result.duration.inWholeMilliseconds}ms")
    }

    if (successful > 0) {
        val avgTime = results.filter { it.success }
            .sumOf { it.duration.inWholeMilliseconds } / successful
        println("   📈 Average operation time: ${avgTime}ms")
    }

    println("✅ Performance validation completed")
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