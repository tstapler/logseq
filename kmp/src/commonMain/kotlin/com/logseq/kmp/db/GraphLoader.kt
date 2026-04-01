package com.logseq.kmp.db

import com.logseq.kmp.model.Block
import com.logseq.kmp.model.Page
import com.logseq.kmp.model.ParsedBlock
import kotlinx.datetime.Instant
import com.logseq.kmp.outliner.JournalUtils
import com.logseq.kmp.outliner.OutlinerPipeline
import com.logseq.kmp.parser.MarkdownParser
import com.logseq.kmp.parsing.ParseMode
import com.logseq.kmp.platform.FileSystem
import com.logseq.kmp.repository.BlockRepository
import com.logseq.kmp.repository.PageRepository
import com.logseq.kmp.logging.Logger
import com.logseq.kmp.performance.PerformanceMonitor
import com.logseq.kmp.util.FileUtils
import com.logseq.kmp.util.UuidGenerator
import kotlinx.datetime.Clock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class GraphLoader(
    private val fileSystem: FileSystem,
    private val pageRepository: PageRepository,
    private val blockRepository: BlockRepository
) {
    private val logger = Logger("GraphLoader")
    private val outlinerPipeline = OutlinerPipeline()
    private val markdownParser = MarkdownParser()

    // ID Generation
    private val idMutex = Mutex()
    // Start with a time-based offset to reduce collision risk and ensure positivity
    private var idCounter = Clock.System.now().toEpochMilliseconds()
    
    // Platform-agnostic parallelism configuration
    // Use conservative defaults that work well across all platforms
    private val ioThreads = 4  // Conservative for mobile/desktop/web
    private val computationThreads = 2  // Conservative for CPU-intensive work
    
    // Platform-agnostic coroutine scope for parallel processing
    private val parallelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private suspend fun generateId(): Long = idMutex.withLock {
        // Ensure strictly positive and monotonically increasing
        if (idCounter <= 0) idCounter = 1L
        idCounter++
    }

    private fun generateUuid(
        parsedBlock: ParsedBlock, 
        pagePath: String, 
        blockIndex: Int
    ): String {
        // If block has an ID property, use it
        val existingId = parsedBlock.properties["id"]
        if (existingId != null && existingId.isNotBlank()) {
            return existingId
        }
        
        // Otherwise generate a deterministic UUID based on file location + content
        // Seed: "filePath:blockIndex:content"
        val seed = "$pagePath:$blockIndex:${parsedBlock.content}"
        return UuidGenerator.generateDeterministic(seed)
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

            // Pre-scan cleanup: Sanitize filenames to ensure cross-platform compatibility
            sanitizeDirectory(pagesDir)
            sanitizeDirectory(journalsDir)

            // Load pages and journals in parallel for better performance
            PerformanceMonitor.startTrace("parallelFullLoad")
            
            coroutineScope {
                val loadPagesJob = async {
                    loadDirectory(pagesDir, onProgress, ParseMode.FULL)
                }
                
                val loadJournalsJob = async {
                    loadDirectory(journalsDir, onProgress, ParseMode.FULL)
                }
                
                // Wait for both to complete
                awaitAll(loadPagesJob, loadJournalsJob)
            }
            PerformanceMonitor.endTrace("parallelFullLoad")

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

            // Pre-scan cleanup: Sanitize filenames
            // We do this synchronously/sequentially before loading to avoid race conditions
            sanitizeDirectory(pagesDir)
            sanitizeDirectory(journalsDir)

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
                    loadDirectory(pagesDir, onProgress, ParseMode.METADATA_ONLY)
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

    suspend fun loadFullPage(pageUuid: String) {
        PerformanceMonitor.startTrace("loadFullPage")
        var filePath: String? = null
        try {
            val pageResult = pageRepository.getPageByUuid(pageUuid).first()
            val page = pageResult.getOrNull()

            if (page == null) {
                logger.error("Page not found for UUID: $pageUuid")
                return
            }

            filePath = page.filePath
            if (filePath == null) {
                logger.error("Page has no file path: ${page.name}")
                return
            }

            // OPTIMIZATION: If page is already loaded and file hasn't changed, skip reload
            if (page.isContentLoaded) {
                val fileModTime = fileSystem.getLastModifiedTime(filePath)
                
                // Verify blocks are actually loaded (handle inconsistency)
                val blocksResult = blockRepository.getBlocksForPage(page.id).first()
                val blocks = blocksResult.getOrNull() ?: emptyList()
                // A page is only fully up-to-date if blocks are present (or the file is empty) 
                // and all blocks are loaded.
                val allBlocksLoaded = blocks.isNotEmpty() && blocks.all { it.isLoaded }

                if (fileModTime != null && 
                    page.updatedAt.toEpochMilliseconds() >= fileModTime &&
                    allBlocksLoaded) {
                    logger.debug("Skipping loadFullPage, already up to date: $filePath")
                    return
                }
                
                if (!allBlocksLoaded) {
                     logger.warn("Force reloading page ${page.name} because blocks are not fully loaded (inconsistency detected)")
                }
            }

            // Mark this file as priority (and coalesce requests)
            if (!tryAddPriorityFile(filePath)) {
                logger.debug("Coalescing load request for $filePath")
                return
            }
            logger.debug("Added priority file for on-demand loading: $filePath")

            val content = fileSystem.readFile(filePath)
            if (content == null) {
                logger.error("Failed to read file: $filePath")
                return
            }

            parseAndSavePage(filePath, content, ParseMode.FULL)
        } finally {
            // Remove from priority set after loading completes
            filePath?.let {
                removePriorityFile(it)
                logger.debug("Removed priority file after loading: $it")
            }
            PerformanceMonitor.endTrace("loadFullPage")
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
                    parseAndSavePage(filePath, content, ParseMode.FULL)
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
                                parseAndSavePage(filePath, content, ParseMode.METADATA_ONLY)
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

    private suspend fun sanitizeDirectory(path: String) {
        if (!fileSystem.directoryExists(path)) return
        
        val files = fileSystem.listFiles(path).filter { it.endsWith(".md") }
        for (fileName in files) {
            val nameWithoutExt = fileName.removeSuffix(".md")
            
            // Roundtrip check: Decode -> Sanitize
            // If the current filename matches the sanitized version of its decoded self, it is stable/safe.
            // If not, it means the filename contains unsafe characters (like :) that need migration.
            
            val decodedName = FileUtils.decodeFileName(nameWithoutExt)
            val expectedName = FileUtils.sanitizeFileName(decodedName)
            
            // Note: On case-insensitive file systems (Windows/Mac), case differences might strictly match or not.
            // FileUtils handles content chars.
            
            if (nameWithoutExt != expectedName) {
                // Filename needs sanitization
                val oldPath = "$path/$fileName"
                val newPath = "$path/$expectedName.md"
                
                if (fileSystem.fileExists(newPath)) {
                    logger.warn("Skipping sanitization for '$fileName' -> '$expectedName.md' because target already exists.")
                } else {
                    try {
                        val content = fileSystem.readFile(oldPath)
                        if (content != null) {
                            if (fileSystem.writeFile(newPath, content)) {
                                if (fileSystem.deleteFile(oldPath)) {
                                    logger.info("Sanitized filename: '$fileName' -> '$expectedName.md'")
                                } else {
                                    logger.error("Failed to delete old file: $oldPath")
                                }
                            } else {
                                logger.error("Failed to write new file: $newPath")
                            }
                        }
                    } catch (e: Exception) {
                        logger.error("Error sanitizing file: $oldPath", e)
                    }
                }
            }
        }
    }

    private suspend fun loadDirectory(path: String, onProgress: (String) -> Unit, mode: ParseMode = ParseMode.METADATA_ONLY) {
        PerformanceMonitor.startTrace("loadDirectory")
        try {
            if (!fileSystem.directoryExists(path)) {
                logger.debug("Directory not found (skipping): $path")
                return
            }
            
            logger.debug("Loading directory: $path with mode $mode")
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

            // OPTIMIZATION: Batch database operations to eliminate per-file transaction overhead
            val loadedCount = coroutineScope {
                var processedCount = 0
                val total = files.size
                
                // Use smaller chunks for mobile platforms, larger for desktop
                val chunkSize = if (ioThreads >= 8) 100 else 50  // Larger chunks for more powerful platforms
                files.chunked(chunkSize).map { chunk ->
                    async(parallelScope.coroutineContext) {
                        PerformanceMonitor.startTrace("processChunk")
                        try {
                            // Collect all parsed pages and blocks first, then batch save
                            val pagesToSave = mutableListOf<Page>()
                            val blocksToSaveByPage = mutableMapOf<Long, MutableList<Block>>()
                            val pageIdsToDelete = mutableSetOf<Long>()
                            
                            val count = chunk.count { fileName ->
                                val filePath = "$path/$fileName"
                                
                                // OPTIMIZATION: Skip unchanged files using modification time checking
                                val fileModTime = fileSystem.getLastModifiedTime(filePath)
                                val fileNameOnly = FileUtils.decodeFileName(fileName.removeSuffix(".md"))
                                val isJournalFile = path.endsWith("/journals")
                                val existingPage = if (isJournalFile) {
                                    // For journals, we need to find page by checking if it exists
                                    // Since there's no getPageByJournalDay, we'll use getAllPages and filter
                                    val journalDate = JournalUtils.parseJournalDate(fileNameOnly)
                                    val allPagesResult = pageRepository.getAllPages().first()
                                    allPagesResult.getOrNull()?.find { it.journalDate == journalDate }
                                } else {
                                    pageRepository.getPageByName(fileNameOnly).first().getOrNull()
                                }
                                
                                // Check if blocks exist for this page to handle partially loaded/failed states
                                val hasBlocks = if (existingPage != null) {
                                    val blocksResult = blockRepository.getBlocksForPage(existingPage.id).first()
                                    val blocks = blocksResult.getOrNull() ?: emptyList()
                                    blocks.isNotEmpty()
                                } else false

                                val shouldSkip = existingPage != null && fileModTime != null && 
                                    existingPage.updatedAt.toEpochMilliseconds() >= fileModTime &&
                                    hasBlocks
                                
                                if (shouldSkip) {
                                    logger.debug("Skipping unchanged file: $filePath (file: $fileModTime, db: ${existingPage!!.updatedAt.toEpochMilliseconds()})")
                                    return@count true  // Count as processed but skip actual parsing
                                }

                                // Skip priority files (being loaded by user) to prevent overwriting with metadata-only
                                if (isPriorityFile(filePath)) {
                                    logger.debug("Skipping priority file in background load: $filePath")
                                    return@count true
                                }
                                
                                logger.debug("Processing file: $filePath")
                                
                                val content = fileSystem.readFile(filePath) ?: return@count false
                                try {
                                    val parseResult = parsePageWithoutSaving(filePath, content, mode)
                                    // Update the page's updatedAt to match file modification time
                                    val updatedPage = parseResult.page // Keep as is for now
                                    pagesToSave.add(updatedPage)
                                    if (parseResult.blocks.isNotEmpty()) {
                                        blocksToSaveByPage[updatedPage.id] = parseResult.blocks.toMutableList()
                                    }
                                    pageIdsToDelete.add(updatedPage.id)
                                    true
                                } catch (e: Exception) {
                                    logger.error("Failed to parse file: $filePath", e)
                                    false
                                }
                            }
                            
                            // BATCH DATABASE OPERATIONS - One transaction per chunk instead of per file
                            if (pagesToSave.isNotEmpty()) {
                                PerformanceMonitor.startTrace("batchSavePages")
                                pagesToSave.forEach { page ->
                                    pageRepository.savePage(page)
                                }
                                PerformanceMonitor.endTrace("batchSavePages")
                            }
                            
                            if (pageIdsToDelete.isNotEmpty()) {
                                PerformanceMonitor.startTrace("batchDeleteBlocks")
                                pageIdsToDelete.forEach { pageId ->
                                    blockRepository.deleteBlocksForPage(pageId)
                                }
                                PerformanceMonitor.endTrace("batchDeleteBlocks")
                            }
                            
                            if (blocksToSaveByPage.isNotEmpty()) {
                                PerformanceMonitor.startTrace("batchSaveBlocks")
                                blocksToSaveByPage.values.forEach { blocks ->
                                    blockRepository.saveBlocks(blocks)
                                }
                                PerformanceMonitor.endTrace("batchSaveBlocks")
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
    
    // 1. Add a map of Mutexes for file-level locking
    // Note: ConcurrentHashMap is JVM-only. Using Mutex-guarded map for KMP.
    private val fileLocksMutex = Mutex()
    private val fileLocks = mutableMapOf<String, Mutex>()

    // Priority loading: tracks files requested for on-demand FULL loading
    // Background METADATA_ONLY tasks should skip these files
    private val priorityFilesMutex = Mutex()
    private val priorityFiles = mutableSetOf<String>()

    private suspend fun getFileLock(path: String): Mutex {
        return fileLocksMutex.withLock {
            fileLocks.getOrPut(path) { Mutex() }
        }
    }

    private suspend fun tryAddPriorityFile(path: String): Boolean {
        return priorityFilesMutex.withLock {
            if (priorityFiles.contains(path)) {
                false
            } else {
                priorityFiles.add(path)
                true
            }
        }
    }

    private suspend fun removePriorityFile(path: String) {
        priorityFilesMutex.withLock {
            priorityFiles.remove(path)
        }
    }

    private suspend fun isPriorityFile(path: String): Boolean {
        return priorityFilesMutex.withLock {
            priorityFiles.contains(path)
        }
    }

    // Data class to hold parse results without database operations
    private data class ParseResult(
        val page: Page,
        val blocks: List<Block>
    )
    
    /**
     * Parse a page without saving to database - used for batch operations
     */
    private suspend fun parsePageWithoutSaving(filePath: String, content: String, mode: ParseMode = ParseMode.FULL): ParseResult {
        val fileName = filePath.replace("\\", "/").substringAfterLast("/")
        val name = FileUtils.decodeFileName(fileName.removeSuffix(".md"))
        val isJournal = filePath.contains("/journals/")
        val journalDate = if (isJournal) JournalUtils.parseJournalDate(name) else null
        
        // Use file modification time if available
        val fileModTime = fileSystem.getLastModifiedTime(filePath)
        val updatedAt = fileModTime?.let { Instant.fromEpochMilliseconds(it) } ?: Clock.System.now()
        
        // Check if page already exists to preserve ID and UUID
        val existingPageResult = pageRepository.getPageByName(name).first()
        val existingPage = existingPageResult.getOrNull()
        
        val pageId = existingPage?.id ?: generateId()
        val pageUuid = existingPage?.uuid ?: UuidGenerator.generateV7()
        val createdAt = existingPage?.createdAt ?: updatedAt
        val currentVersion = existingPage?.version ?: 0L
        
        if (pageId <= 0) {
            logger.error("Generated invalid pageId: $pageId for $filePath")
            throw IllegalArgumentException("Generated invalid pageId: $pageId")
        }
        
        // Parse markdown content
        val parsedPage = markdownParser.parsePage(content)
        
        // Extract page properties
        var firstBlockSkipped = false
        var properties = parsedPage.properties
        if (parsedPage.blocks.isNotEmpty()) {
            val firstBlock = parsedPage.blocks.first()
            if (firstBlock.content.trim().isEmpty() && firstBlock.properties.isNotEmpty()) {
                properties = firstBlock.properties
                firstBlockSkipped = true
            }
        }

        // Only mark as fully loaded if we actually got some blocks, 
        // OR if the content is truly empty (whitespace only)
        val isLoaded = if (mode == ParseMode.FULL && parsedPage.blocks.isEmpty() && content.trim().isNotEmpty()) {
            false
        } else {
            mode == ParseMode.FULL
        }

        val pageWithMetadata = Page(
            id = pageId,
            uuid = pageUuid,
            name = name,
            namespace = null,
            filePath = filePath,
            createdAt = createdAt,
            updatedAt = updatedAt,
            version = currentVersion,
            properties = properties,
            isFavorite = false,
            isJournal = isJournal,
            journalDate = journalDate,
            isContentLoaded = isLoaded
        )
        
        // Fetch existing blocks to preserve versions
        val existingBlocksResult = blockRepository.getBlocksForPage(pageId).first()
        val existingBlocks = existingBlocksResult.getOrNull() ?: emptyList()
        val existingVersions = existingBlocks.associate { it.uuid to it.version }
        val existingContent = existingBlocks.associate { it.uuid to it.content }

        // Process blocks based on mode
        val rootBlocks = if (firstBlockSkipped) parsedPage.blocks.drop(1) else parsedPage.blocks
        val blocksList = mutableListOf<Block>()

        processParsedBlocks(
            rootBlocks, filePath, pageId, null, 0, updatedAt,
            blocksList, mode,
            existingVersions, existingContent
        )
        
        return ParseResult(page = pageWithMetadata, blocks = blocksList)
    }
    
    private suspend fun parseAndSavePage(filePath: String, content: String, mode: ParseMode = ParseMode.FULL) {
        // Priority check: if this file is marked for on-demand FULL loading,
        // skip METADATA_ONLY background processing (on-demand will handle it)
        if (mode == ParseMode.METADATA_ONLY && isPriorityFile(filePath)) {
            logger.debug("Skipping METADATA_ONLY for priority file (on-demand loading): $filePath")
            return
        }

        val lock = getFileLock(filePath)

        // Prevent concurrent parses of the same file
        lock.withLock {
            // Re-check priority inside lock in case on-demand started while we were waiting
            if (mode == ParseMode.METADATA_ONLY && isPriorityFile(filePath)) {
                logger.debug("Skipping METADATA_ONLY for priority file (checked inside lock): $filePath")
                return
            }

            PerformanceMonitor.startTrace("parseAndSavePage")
            try {
                val fileName = filePath.replace("\\", "/").substringAfterLast("/")
                val name = FileUtils.decodeFileName(fileName.removeSuffix(".md"))
                val journalDate = JournalUtils.parseJournalDate(name)
                val isJournal = journalDate != null || filePath.contains("/journals/")
                
                val now = Clock.System.now()
                
                // Check if page already exists to preserve ID and UUID
                val existingPageResult = pageRepository.getPageByName(name).first()
                val existingPage = existingPageResult.getOrNull()

                // Skip METADATA_ONLY if page is already fully loaded (don't overwrite full content)
                if (mode == ParseMode.METADATA_ONLY && existingPage?.isContentLoaded == true) {
                    logger.debug("Skipping METADATA_ONLY for already-loaded page: $name")
                    return
                }

                // OPTIMIZATION: If mode is FULL, but page is already loaded and fresh (checked inside lock), skip.
                if (mode == ParseMode.FULL && existingPage?.isContentLoaded == true) {
                     val fileModTime = fileSystem.getLastModifiedTime(filePath)
                     
                     val blocksResult = blockRepository.getBlocksForPage(existingPage.id).first()
                     val blocks = blocksResult.getOrNull() ?: emptyList()
                     // A page is only fully up-to-date if blocks are present (or the file is empty) 
                     // and all blocks are loaded.
                     val allBlocksLoaded = blocks.isNotEmpty() && blocks.all { it.isLoaded }

                     if (fileModTime != null && 
                         existingPage.updatedAt.toEpochMilliseconds() >= fileModTime &&
                         allBlocksLoaded) {
                          logger.debug("Skipping FULL parse (concurrency check), already up to date: $filePath")
                          return
                     }
                }

                val pageId = existingPage?.id ?: generateId()
                val pageUuid = existingPage?.uuid ?: UuidGenerator.generateV7()
                val createdAt = existingPage?.createdAt ?: now
                
                if (pageId <= 0) {
                    logger.error("Generated invalid pageId: $pageId for $filePath")
                    throw IllegalArgumentException("Generated invalid pageId: $pageId")
                }

                // Get file modification time for updatedAt to stay in sync with disk
                val fileModTime = fileSystem.getLastModifiedTime(filePath)
                val updatedAt = fileModTime?.let { Instant.fromEpochMilliseconds(it) } ?: now
                
                // Initial Page object
                var page = Page(
                    id = pageId,
                    uuid = pageUuid,
                    name = name,
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                    version = existingPage?.version ?: 0L,
                    properties = emptyMap(),
                    isFavorite = existingPage?.isFavorite ?: false,
                    isJournal = isJournal,
                    journalDate = journalDate,
                    filePath = filePath,
                    isContentLoaded = mode == ParseMode.FULL
                )
                
                // Parse using MarkdownParser
                val parsedPage = try {
                    markdownParser.parsePage(content, mode)
                } catch (e: Exception) {
                    logger.error("Failed to parse file: $filePath (content length: ${content.length})", e)
                    throw e
                }

                // Extract page properties if any (often in the first block or pre-block)
                val blocksToSave = mutableListOf<Block>()
                var firstBlockSkipped = false
                
                if (parsedPage.blocks.isNotEmpty()) {
                    val firstBlock = parsedPage.blocks.first()
                    // If first block has properties but no content, treat as page properties
                    if (firstBlock.content.trim().isEmpty() && firstBlock.properties.isNotEmpty()) {
                        page = page.copy(properties = firstBlock.properties)
                        firstBlockSkipped = true
                    }
                }
                
                // Only consider it fully loaded if we actually got some blocks, 
                // OR if the content is truly empty (whitespace only)
                if (mode == ParseMode.FULL && parsedPage.blocks.isEmpty() && content.trim().isNotEmpty()) {
                    logger.warn("Parsed 0 blocks for non-empty file: $filePath. Marking as NOT loaded to allow retry.")
                    page = page.copy(isContentLoaded = false)
                }

                val saveResult = pageRepository.savePage(page)
                val actualPageId = saveResult.getOrNull() ?: pageId
                
                // Fetch existing blocks using actual identity to preserve versions
                val existingBlocksResult = blockRepository.getBlocksForPage(actualPageId).first()
                val existingBlocks = existingBlocksResult.getOrNull() ?: emptyList()
                val existingVersions = existingBlocks.associate { it.uuid to it.version }
                val existingContent = existingBlocks.associate { it.uuid to it.content }

                // Recursively process blocks using the actual ID from DB
                val rootBlocks = if (firstBlockSkipped) parsedPage.blocks.drop(1) else parsedPage.blocks
                
                processParsedBlocks(
                    parsedBlocks = rootBlocks,
                    pagePath = filePath,
                    pageId = actualPageId,
                    parentId = null,
                    baseLevel = 0,
                    now = updatedAt,
                    destinationList = blocksToSave,
                    mode = mode,
                    existingVersions = existingVersions,
                    existingContent = existingContent
                )
                
                // ALWAYS clear and update blocks if mode is FULL, even if blocksToSave is empty
                // (This reflects the actual state of the file on disk)
                if (mode == ParseMode.FULL) {
                    blockRepository.deleteBlocksForPage(actualPageId)
                    if (blocksToSave.isNotEmpty()) {
                        blockRepository.saveBlocks(blocksToSave)
                    }
                } else if (blocksToSave.isNotEmpty()) {
                    // For METADATA_ONLY, we only update if we found something (don't overwrite full blocks with partial ones)
                    // Wait, actually we should be careful here too.
                    // If we are in METADATA_ONLY, we should only save if isContentLoaded was false.
                    blockRepository.deleteBlocksForPage(actualPageId)
                    blockRepository.saveBlocks(blocksToSave)
                }
            } finally {
                PerformanceMonitor.endTrace("parseAndSavePage")
            }
        }
    }

    private suspend fun processParsedBlocks(
        parsedBlocks: List<ParsedBlock>,
        pagePath: String,
        pageId: Long,
        parentId: Long?,
        baseLevel: Int,
        now: kotlinx.datetime.Instant,
        destinationList: MutableList<Block>,
        mode: ParseMode,
        existingVersions: Map<String, Long> = emptyMap(),
        existingContent: Map<String, String> = emptyMap()
    ) {
        var previousSiblingId: Long? = null
        
        parsedBlocks.forEachIndexed { index, parsedBlock ->
            val blockId = generateId()
            val blockUuid = generateUuid(parsedBlock, pagePath, index)
            
            // Version Preservation:
            // If the content is identical to what we have in DB, preserve version.
            // If content changed (e.g. edited in external editor), reset version or increment.
            val currentVersion = existingVersions[blockUuid] ?: 0L
            val oldContent = existingContent[blockUuid]
            
            val versionToSave = if (oldContent == parsedBlock.content) {
                currentVersion
            } else {
                // External change detected. 
                // We should probably increment the version so UI knows to reload.
                if (currentVersion > 0) currentVersion + 1 else 0L
            }

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
                version = versionToSave,
                properties = mergedProperties,
                isLoaded = mode == ParseMode.FULL
            )
            
            destinationList.add(block)
            previousSiblingId = blockId
            
            // Process children
            if (parsedBlock.children.isNotEmpty()) {
                processParsedBlocks(
                    parsedBlocks = parsedBlock.children,
                    pagePath = pagePath,
                    pageId = pageId,
                    parentId = blockId,
                    baseLevel = baseLevel + 1,
                    now = now,
                    destinationList = destinationList,
                    mode = mode,
                    existingVersions = existingVersions,
                    existingContent = existingContent
                )
            }
        }
    }
}
