package com.logseq.kmp.db

import com.logseq.kmp.model.Block
import com.logseq.kmp.model.Page
import com.logseq.kmp.model.ParsedBlock
import com.logseq.kmp.outliner.JournalUtils
import com.logseq.kmp.outliner.OutlinerPipeline
import com.logseq.kmp.parser.MarkdownParser
import com.logseq.kmp.platform.FileSystem
import com.logseq.kmp.repository.BlockRepository
import com.logseq.kmp.repository.SimplePageRepository
import com.logseq.kmp.logging.Logger
import com.logseq.kmp.performance.PerformanceMonitor
import kotlinx.datetime.Clock
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class GraphLoader(
    private val fileSystem: FileSystem,
    private val pageRepository: SimplePageRepository,
    private val blockRepository: BlockRepository
) {
    private val logger = Logger("GraphLoader")
    private val outlinerPipeline = OutlinerPipeline()
    private val markdownParser = MarkdownParser()

    // ID Generation
    private val idMutex = Mutex()
    // Start with a time-based offset to reduce collision risk and ensure positivity
    private var idCounter = Clock.System.now().toEpochMilliseconds()

    private suspend fun generateId(): Long = idMutex.withLock {
        // Ensure strictly positive and monotonically increasing
        if (idCounter <= 0) idCounter = 1L
        idCounter++
    }

    // Basic UUID generator until we have a shared common lib
    private fun generateUuid(): String {
        val chars = "0123456789abcdef"
        fun randomHex(length: Int) = (1..length).map { chars.random() }.joinToString("")
        return "${randomHex(8)}-${randomHex(4)}-${randomHex(4)}-${randomHex(4)}-${randomHex(12)}"
    }

    suspend fun loadGraph(graphPath: String, onProgress: (String) -> Unit) {
        PerformanceMonitor.startTrace("loadGraph")
        try {
            if (!fileSystem.directoryExists(graphPath)) {
                logger.warn("Graph directory not found: $graphPath")
                return
            }

            logger.info("Starting graph load from: $graphPath")
            val startTime = Clock.System.now()

            val pagesDir = "$graphPath/pages"
            val journalsDir = "$graphPath/journals"

            loadDirectory(pagesDir, onProgress)
            loadDirectory(journalsDir, onProgress)

            val duration = Clock.System.now() - startTime
            logger.info("Graph load complete. Duration: $duration")
        } finally {
            PerformanceMonitor.endTrace("loadGraph")
        }
    }

    /**
     * Progressive graph loading that prioritizes journals for fast startup.
     *
     * Phase 1 (< 500ms): Loads most recent journals to fill viewport immediately
     * Phase 2 (background): Loads remaining journals and all pages concurrently
     *
     * @param graphPath Path to the graph directory
     * @param immediateJournalCount Number of journals to load in Phase 1 (default 10)
     * @param onProgress Callback for progress updates
     * @param onPhase1Complete Callback when Phase 1 completes (UI can become interactive)
     * @param onFullyLoaded Callback when all background loading completes
     */
    suspend fun loadGraphProgressive(
        graphPath: String,
        immediateJournalCount: Int = 10,
        onProgress: (String) -> Unit,
        onPhase1Complete: () -> Unit,
        onFullyLoaded: () -> Unit
    ) {
        PerformanceMonitor.startTrace("loadGraphProgressive")
        try {
            if (!fileSystem.directoryExists(graphPath)) {
                logger.warn("Graph directory not found: $graphPath")
                onPhase1Complete()
                onFullyLoaded()
                return
            }

            logger.info("Starting progressive graph load from: $graphPath")
            val startTime = Clock.System.now()

            val pagesDir = "$graphPath/pages"
            val journalsDir = "$graphPath/journals"

            // Phase 1: Load immediate journals for fast startup
            val phase1Start = Clock.System.now()
            val loadedImmediateCount = loadJournalsImmediate(journalsDir, immediateJournalCount, onProgress)
            val phase1Duration = Clock.System.now() - phase1Start
            logger.info("Phase 1 complete: Loaded $loadedImmediateCount journals in $phase1Duration")

            // Signal UI is ready - user can start interacting
            onProgress("Ready - loading remaining content...")
            onPhase1Complete()

            // Phase 2: Load remaining content in background
            coroutineScope {
                // Load remaining journals
                launch(Dispatchers.Default) {
                    loadRemainingJournals(journalsDir, immediateJournalCount, onProgress)
                }

                // Load pages in parallel
                launch(Dispatchers.Default) {
                    loadDirectory(pagesDir, onProgress)
                }
            }

            val totalDuration = Clock.System.now() - startTime
            logger.info("Progressive graph load complete. Total duration: $totalDuration")
            onProgress("Graph loaded completely.")
            onFullyLoaded()
        } finally {
            PerformanceMonitor.endTrace("loadGraphProgressive")
        }
    }

    /**
     * Loads the most recent journals immediately for fast startup.
     * Journals are sorted by filename (descending) to get most recent first.
     */
    private suspend fun loadJournalsImmediate(
        journalsDir: String,
        count: Int,
        onProgress: (String) -> Unit
    ): Int {
        PerformanceMonitor.startTrace("loadJournalsImmediate")
        try {
            if (!fileSystem.directoryExists(journalsDir)) {
                logger.debug("Journals directory not found (skipping): $journalsDir")
                return 0
            }

            logger.debug("Loading $count most recent journals from: $journalsDir")
            val allFiles = fileSystem.listFiles(journalsDir).filter { it.endsWith(".md") }
            val immediateFiles = allFiles.sortedDescending().take(count)

            logger.debug("Found ${allFiles.size} total journals, loading ${immediateFiles.size} immediately")
            onProgress("Loading recent journals...")

            var loadedCount = 0
            for (fileName in immediateFiles) {
                val filePath = "$journalsDir/$fileName"
                logger.debug("Processing journal: $filePath")

                val content = fileSystem.readFile(filePath) ?: continue
                try {
                    parseAndSavePage(filePath, content)
                    loadedCount++
                } catch (e: Exception) {
                    logger.error("Failed to parse journal: $filePath", e)
                }
            }

            logger.debug("Loaded $loadedCount immediate journals")
            return loadedCount
        } finally {
            PerformanceMonitor.endTrace("loadJournalsImmediate")
        }
    }

    /**
     * Loads remaining journals after the immediate ones, in background.
     */
    private suspend fun loadRemainingJournals(
        journalsDir: String,
        skipCount: Int,
        onProgress: (String) -> Unit
    ) {
        PerformanceMonitor.startTrace("loadRemainingJournals")
        try {
            if (!fileSystem.directoryExists(journalsDir)) {
                return
            }

            val allFiles = fileSystem.listFiles(journalsDir).filter { it.endsWith(".md") }
            // Take up to 30 total journals, but skip the ones already loaded
            val remainingFiles = allFiles.sortedDescending().drop(skipCount).take(30 - skipCount)

            if (remainingFiles.isEmpty()) {
                logger.debug("No remaining journals to load")
                return
            }

            logger.debug("Loading ${remainingFiles.size} remaining journals in background")

            val loadedCount = coroutineScope {
                var processedCount = 0
                val total = remainingFiles.size

                remainingFiles.chunked(50).map { chunk ->
                    async(Dispatchers.Default) {
                        val count = chunk.count { fileName ->
                            val filePath = "$journalsDir/$fileName"
                            logger.debug("Background loading journal: $filePath")

                            val content = fileSystem.readFile(filePath) ?: return@count false
                            try {
                                parseAndSavePage(filePath, content)
                                true
                            } catch (e: Exception) {
                                logger.error("Failed to parse journal: $filePath", e)
                                false
                            }
                        }

                        processedCount += chunk.size
                        onProgress("Loading journals... ($processedCount/$total remaining)")
                        count
                    }
                }.awaitAll().sum()
            }

            logger.debug("Background loaded $loadedCount remaining journals")
        } finally {
            PerformanceMonitor.endTrace("loadRemainingJournals")
        }
    }

    private suspend fun loadDirectory(path: String, onProgress: (String) -> Unit) {
        PerformanceMonitor.startTrace("loadDirectory")
        try {
            if (!fileSystem.directoryExists(path)) {
                logger.debug("Directory not found (skipping): $path")
                return
            }
            
            logger.debug("Loading directory: $path")
            var files = fileSystem.listFiles(path).filter { it.endsWith(".md") }

            if (path.endsWith("/journals")) {
                // Sort journals in reverse chronological order (descending) and limit to recent 30
                files = files.sortedDescending().take(30)
                logger.debug("Optimized journal loading: selected ${files.size} most recent journals")
            } else {
                // Sort other files alphabetically
                files = files.sorted()
            }

            logger.debug("Found ${files.size} markdown files in $path")

            val loadedCount = coroutineScope {
                // Process in chunks to avoid overwhelming the dispatcher
                var processedCount = 0
                val total = files.size
                
                files.chunked(50).map { chunk ->
                    async(Dispatchers.Default) {
                        PerformanceMonitor.startTrace("processChunk")
                        try {
                            val count = chunk.count { fileName ->
                                val filePath = "$path/$fileName"
                                logger.debug("Processing file: $filePath")
                                
                                val content = fileSystem.readFile(filePath) ?: return@count false
                                try {
                                    parseAndSavePage(filePath, content)
                                    true
                                } catch (e: Exception) {
                                    logger.error("Failed to parse file: $filePath", e)
                                    false
                                }
                            }
                            
                            // Update progress (approximate due to concurrency)
                            processedCount += chunk.size
                            onProgress("Loading $path... ($processedCount/$total)")
                            count
                        } finally {
                            PerformanceMonitor.endTrace("processChunk")
                        }
                    }
                }.awaitAll().sum()
            }
            
            logger.debug("Loaded $loadedCount files from $path")
        } finally {
            PerformanceMonitor.endTrace("loadDirectory")
        }
    }
    
    private suspend fun parseAndSavePage(filePath: String, content: String) {
        PerformanceMonitor.startTrace("parseAndSavePage")
        try {
            // Basic path separator handling
            val fileName = filePath.replace("\\", "/").substringAfterLast("/")
            val name = fileName.removeSuffix(".md")
            val isJournal = filePath.contains("/journals/")
            val journalDate = if (isJournal) JournalUtils.parseJournalDate(name) else null
            
            val now = Clock.System.now()
            val pageUuid = generateUuid()
            
            // Generate a unique ID safely
            val pageId = generateId()
            if (pageId <= 0) {
                logger.error("Generated invalid pageId: $pageId for $filePath")
                throw IllegalArgumentException("Generated invalid pageId: $pageId")
            }
            
            // Initial Page object
            var page = Page(
                id = pageId, 
                uuid = pageUuid,
                name = name,
                createdAt = now,
                updatedAt = now,
                properties = emptyMap(),
                isFavorite = false,
                isJournal = isJournal,
                journalDate = journalDate
            )
            
            // Parse using MarkdownParser
            val parsedPage = markdownParser.parsePage(content)
            
            // Extract page properties if any (often in the first block or pre-block)
            // For Logseq, the first block CAN be page properties.
            // But MarkdownParser splits strictly by blocks.
            // We check the first block for properties.
            
            val blocksToSave = mutableListOf<Block>()
            var firstBlockSkipped = false
            
            if (parsedPage.blocks.isNotEmpty()) {
                val firstBlock = parsedPage.blocks.first()
                // If first block has properties but no content, treat as page properties
                if (firstBlock.content.trim().isEmpty() && firstBlock.properties.isNotEmpty()) {
                    page = page.copy(properties = firstBlock.properties)
                    // Also check for ID
                    val pagePropsId = firstBlock.properties["id"]
                    if (pagePropsId != null) {
                        // page = page.copy(uuid = pagePropsId) // TODO: Handle ID migration
                    }
                    firstBlockSkipped = true
                }
            }
            
            pageRepository.savePage(page)
            
            // Recursively process blocks
            val rootBlocks = if (firstBlockSkipped) parsedPage.blocks.drop(1) else parsedPage.blocks
            
            processParsedBlocks(
                parsedBlocks = rootBlocks,
                pageId = pageId,
                parentId = null,
                baseLevel = 0,
                now = now,
                destinationList = blocksToSave
            )
            
            if (blocksToSave.isNotEmpty()) {
                // Clear existing blocks for this page to prevent duplicates/ordering issues on reload
                blockRepository.deleteBlocksForPage(pageId)
                blockRepository.saveBlocks(blocksToSave)
            }
        } finally {
            PerformanceMonitor.endTrace("parseAndSavePage")
        }
    }

    private suspend fun processParsedBlocks(
        parsedBlocks: List<ParsedBlock>,
        pageId: Long,
        parentId: Long?,
        baseLevel: Int,
        now: kotlinx.datetime.Instant,
        destinationList: MutableList<Block>
    ) {
        var previousSiblingId: Long? = null
        
        parsedBlocks.forEachIndexed { index, parsedBlock ->
            val blockId = generateId()
            val blockUuid = parsedBlock.properties["id"] ?: generateUuid()
            
            // Merge parsed metadata into properties
            val mergedProperties = parsedBlock.properties.toMutableMap()
            parsedBlock.scheduled?.let { mergedProperties["scheduled"] = it }
            parsedBlock.deadline?.let { mergedProperties["deadline"] = it }
            
            // Create Block entity
            val block = Block(
                id = blockId,
                uuid = blockUuid,
                pageId = pageId,
                parentId = parentId,
                leftId = previousSiblingId,
                content = parsedBlock.content, // Content usually includes properties text in Logseq
                level = baseLevel, // Or use parsedBlock.level if relative to root
                position = index,
                createdAt = now,
                updatedAt = now,
                properties = mergedProperties
            )
            
            if (block.content.contains("Rediscovering Paper")) {
                println("GraphLoader DEBUG: Saving 'Rediscovering Paper' - ID: $blockId, Parent: $parentId, Level: $baseLevel")
            }
            
            destinationList.add(block)
            previousSiblingId = blockId
            
            // Debug log for specific files to trace level issue
            if (baseLevel > 0) {
                // logger.debug("Saving child block: '${block.content.take(20)}' at level $baseLevel, parent=$parentId")
            }
            
            // Process children
            if (parsedBlock.children.isNotEmpty()) {
                processParsedBlocks(
                    parsedBlocks = parsedBlock.children,
                    pageId = pageId,
                    parentId = blockId,
                    baseLevel = baseLevel + 1,
                    now = now,
                    destinationList = destinationList
                )
            }
        }
    }
}
