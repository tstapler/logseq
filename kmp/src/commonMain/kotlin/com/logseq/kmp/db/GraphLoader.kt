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

/**
 * GraphLoader handles loading markdown files from disk into the database.
 * 
 * Updated to use UUID-native storage for all references.
 * Includes file system watching for auto-reload.
 */
class GraphLoader(
    private val fileSystem: FileSystem,
    private val pageRepository: PageRepository,
    private val blockRepository: BlockRepository
) {
    private val logger = Logger("GraphLoader")
    private val outlinerPipeline = OutlinerPipeline()
    private val markdownParser = MarkdownParser()

    // Tracks the currently loaded graph path so on-demand loads can resolve file paths
    var currentGraphPath: String = ""
        private set
    
    // Platform-agnostic parallelism configuration
    private val ioThreads = 4
    private val computationThreads = 2
    
    // Platform-agnostic coroutine scope for parallel processing
    private val parallelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    // Watcher job
    private var watcherJob: Job? = null
    private val knownFilesModTimes = mutableMapOf<String, Long>()

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
        val seed = "$pagePath:$blockIndex:${parsedBlock.content}"
        return UuidGenerator.generateDeterministic(seed)
    }

    /**
     * Tries to find the .md file for a page by searching pages/ and journals/ directories.
     */
    fun resolvePageFilePath(pageName: String): String? {
        if (currentGraphPath.isEmpty()) return null
        val candidates = listOf(
            "$currentGraphPath/pages/$pageName.md",
            "$currentGraphPath/journals/$pageName.md"
        )
        return candidates.firstOrNull { fileSystem.fileExists(it) }
    }

    /**
     * Priority-loads a page by name directly from disk.
     */
    suspend fun loadPageByName(pageName: String): Page? {
        val filePath = resolvePageFilePath(pageName) ?: return null
        if (!tryAddPriorityFile(filePath)) {
            // Already loading — wait for it by polling the DB
            repeat(10) {
                val page = pageRepository.getPageByName(pageName).first().getOrNull()
                if (page?.isContentLoaded == true) return page
                kotlinx.coroutines.delay(200)
            }
            return pageRepository.getPageByName(pageName).first().getOrNull()
        }
        try {
            val content = fileSystem.readFile(filePath) ?: return null
            parseAndSavePage(filePath, content, ParseMode.FULL)
            return pageRepository.getPageByName(pageName).first().getOrNull()
        } finally {
            removePriorityFile(filePath)
        }
    }

    suspend fun loadGraph(graphPath: String, onProgress: (String) -> Unit) {
        currentGraphPath = graphPath
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

            sanitizeDirectory(pagesDir)
            sanitizeDirectory(journalsDir)

            coroutineScope {
                val loadPagesJob = async {
                    loadDirectory(pagesDir, onProgress, ParseMode.FULL)
                }
                
                val loadJournalsJob = async {
                    loadDirectory(journalsDir, onProgress, ParseMode.FULL)
                }
                
                awaitAll(loadPagesJob, loadJournalsJob)
            }

            val duration = Clock.System.now() - startTime
            logger.info("Graph load complete. Duration: $duration")
            
            // Start watching after initial load
            startWatching(graphPath)
        } finally {
            PerformanceMonitor.endTrace("loadGraph")
        }
    }

    suspend fun loadGraphProgressive(
        graphPath: String,
        immediateJournalCount: Int = 10,
        onProgress: (String) -> Unit,
        onPhase1Complete: () -> Unit,
        onFullyLoaded: () -> Unit
    ) {
        currentGraphPath = graphPath
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

            sanitizeDirectory(pagesDir)
            sanitizeDirectory(journalsDir)

            val phase1Start = Clock.System.now()
            val loadedImmediateCount = loadJournalsImmediate(journalsDir, immediateJournalCount, onProgress)
            val phase1Duration = Clock.System.now() - phase1Start
            logger.info("Phase 1 complete: Loaded $loadedImmediateCount journals in $phase1Duration")

            onProgress("Ready - loading remaining content...")
            onPhase1Complete()

            coroutineScope {
                launch(Dispatchers.Default) {
                    loadRemainingJournals(journalsDir, immediateJournalCount, onProgress)
                }

                launch(Dispatchers.Default) {
                    loadDirectory(pagesDir, onProgress, ParseMode.METADATA_ONLY)
                }
            }

            val totalDuration = Clock.System.now() - startTime
            logger.info("Progressive graph load complete. Total duration: $totalDuration")
            onProgress("Graph loaded completely.")
            onFullyLoaded()
            
            // Start watching after initial load
            startWatching(graphPath)
        } finally {
            PerformanceMonitor.endTrace("loadGraphProgressive")
        }
    }

    fun startWatching(graphPath: String) {
        watcherJob?.cancel()
        watcherJob = parallelScope.launch {
            logger.info("Started watching graph for changes: $graphPath")
            while (isActive) {
                try {
                    delay(5000) // Poll every 5 seconds
                    val pagesDir = "$graphPath/pages"
                    val journalsDir = "$graphPath/journals"
                    
                    checkDirectoryForChanges(pagesDir)
                    checkDirectoryForChanges(journalsDir)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    logger.error("Error in graph watcher", e)
                }
            }
        }
    }

    private suspend fun checkDirectoryForChanges(dirPath: String) {
        if (!fileSystem.directoryExists(dirPath)) return
        
        val files = fileSystem.listFiles(dirPath).filter { it.endsWith(".md") }
        for (fileName in files) {
            val filePath = "$dirPath/$fileName"
            val modTime = fileSystem.getLastModifiedTime(filePath) ?: 0L
            val lastKnownTime = knownFilesModTimes[filePath]
            
            if (lastKnownTime == null) {
                // New file detected
                logger.info("New file detected: $filePath")
                knownFilesModTimes[filePath] = modTime
                val content = fileSystem.readFile(filePath)
                if (content != null) {
                    parseAndSavePage(filePath, content, ParseMode.FULL)
                }
            } else if (modTime > lastKnownTime) {
                // Modified file detected
                logger.info("File modification detected: $filePath")
                knownFilesModTimes[filePath] = modTime
                val content = fileSystem.readFile(filePath)
                if (content != null) {
                    // Check if content actually changed before re-parsing
                    // This prevents re-parsing if mod time changed but content is same (e.g. from our own write)
                    parseAndSavePage(filePath, content, ParseMode.FULL)
                }
            }
        }
        
        // Also check for deleted files
        val currentFiles = files.map { "$dirPath/$it" }.toSet()
        val deletedFiles = knownFilesModTimes.keys.filter { it.startsWith(dirPath) && it !in currentFiles }
        for (filePath in deletedFiles) {
            logger.info("File deletion detected: $filePath")
            knownFilesModTimes.remove(filePath)
            // Handle deletion in DB if needed (optional for MVP)
        }
    }

    fun stopWatching() {
        watcherJob?.cancel()
        watcherJob = null
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

            filePath = page.filePath ?: resolvePageFilePath(page.name)
            if (filePath == null) {
                logger.warn("Page has no file path and could not be found on disk: ${page.name}")
                return
            }

            if (page.isContentLoaded) {
                val fileModTime = fileSystem.getLastModifiedTime(filePath)
                
                val blocksResult = blockRepository.getBlocksForPage(page.uuid).first()
                val blocks = blocksResult.getOrNull() ?: emptyList()
                val allBlocksLoaded = blocks.isNotEmpty() && blocks.all { it.isLoaded }

                if (fileModTime != null && 
                    page.updatedAt.toEpochMilliseconds() >= fileModTime &&
                    allBlocksLoaded) {
                    logger.debug("Skipping loadFullPage, already up to date: $filePath")
                    return
                }
            }

            if (!tryAddPriorityFile(filePath)) {
                logger.debug("Coalescing load request for $filePath")
                return
            }

            val content = fileSystem.readFile(filePath)
            if (content == null) {
                logger.error("Failed to read file: $filePath")
                return
            }

            parseAndSavePage(filePath, content, ParseMode.FULL)
        } finally {
            filePath?.let { removePriorityFile(it) }
            PerformanceMonitor.endTrace("loadFullPage")
        }
    }

    private suspend fun loadJournalsImmediate(
        journalsDir: String,
        count: Int,
        onProgress: (String) -> Unit
    ): Int {
        PerformanceMonitor.startTrace("loadJournalsImmediate")
        try {
            if (!fileSystem.directoryExists(journalsDir)) return 0

            val allFiles = fileSystem.listFiles(journalsDir)
                .filter { it.endsWith(".md") && JournalUtils.isJournalName(it.removeSuffix(".md")) }
            val immediateFiles = allFiles.sortedDescending().take(count)

            onProgress("Loading recent journals...")

            var loadedCount = 0
            for (fileName in immediateFiles) {
                val filePath = "$journalsDir/$fileName"
                val modTime = fileSystem.getLastModifiedTime(filePath) ?: 0L
                knownFilesModTimes[filePath] = modTime
                val content = fileSystem.readFile(filePath) ?: continue
                try {
                    parseAndSavePage(filePath, content, ParseMode.FULL)
                    loadedCount++
                } catch (e: Exception) {
                    logger.error("Failed to parse journal: $filePath", e)
                }
            }
            return loadedCount
        } finally {
            PerformanceMonitor.endTrace("loadJournalsImmediate")
        }
    }

    private suspend fun loadRemainingJournals(
        journalsDir: String,
        skipCount: Int,
        onProgress: (String) -> Unit
    ) {
        PerformanceMonitor.startTrace("loadRemainingJournals")
        try {
            if (!fileSystem.directoryExists(journalsDir)) return

            val allFiles = fileSystem.listFiles(journalsDir)
                .filter { it.endsWith(".md") && JournalUtils.isJournalName(it.removeSuffix(".md")) }
            val remainingFiles = allFiles.sortedDescending().drop(skipCount).take(30 - skipCount)

            if (remainingFiles.isEmpty()) return

            coroutineScope {
                var processedCount = 0
                val total = remainingFiles.size

                remainingFiles.chunked(50).map { chunk ->
                    async(Dispatchers.Default) {
                        val count = chunk.count { fileName ->
                            val filePath = "$journalsDir/$fileName"
                            val modTime = fileSystem.getLastModifiedTime(filePath) ?: 0L
                            knownFilesModTimes[filePath] = modTime
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
        } finally {
            PerformanceMonitor.endTrace("loadRemainingJournals")
        }
    }

    private suspend fun sanitizeDirectory(path: String) {
        if (!fileSystem.directoryExists(path)) return
        
        val files = fileSystem.listFiles(path).filter { it.endsWith(".md") }
        for (fileName in files) {
            val nameWithoutExt = fileName.removeSuffix(".md")
            val decodedName = FileUtils.decodeFileName(nameWithoutExt)
            val expectedName = FileUtils.sanitizeFileName(decodedName)
            
            if (nameWithoutExt != expectedName) {
                val oldPath = "$path/$fileName"
                val newPath = "$path/$expectedName.md"
                
                if (!fileSystem.fileExists(newPath)) {
                    try {
                        val content = fileSystem.readFile(oldPath)
                        if (content != null) {
                            if (fileSystem.writeFile(newPath, content)) {
                                if (fileSystem.deleteFile(oldPath)) {
                                    logger.info("Sanitized filename: '$fileName' -> '$expectedName.md'")
                                }
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
            if (!fileSystem.directoryExists(path)) return
            
            var files = fileSystem.listFiles(path).filter { it.endsWith(".md") }
            if (path.endsWith("/journals")) {
                files = files.sortedDescending().take(30)
            } else {
                files = files.sorted()
            }

            val loadedCount = coroutineScope {
                var processedCount = 0
                val total = files.size
                
                val chunkSize = if (ioThreads >= 8) 100 else 50
                files.chunked(chunkSize).map { chunk ->
                    async(parallelScope.coroutineContext) {
                        PerformanceMonitor.startTrace("processChunk")
                        try {
                            val pagesToSave = mutableListOf<Page>()
                            val blocksToSaveByPage = mutableMapOf<String, MutableList<Block>>()
                            val pageUuidsToDelete = mutableSetOf<String>()
                            
                            val count = chunk.count { fileName ->
                                val filePath = "$path/$fileName"
                                val fileModTime = fileSystem.getLastModifiedTime(filePath) ?: 0L
                                knownFilesModTimes[filePath] = fileModTime
                                
                                val fileNameOnly = fileName.removeSuffix(".md")
                                val isJournalFile = path.endsWith("/journals")
                                val existingPage = if (isJournalFile) {
                                    val journalDate = JournalUtils.parseJournalDate(fileNameOnly)
                                    val allPagesResult = pageRepository.getAllPages().first()
                                    allPagesResult.getOrNull()?.find { it.journalDate == journalDate }
                                } else {
                                    pageRepository.getPageByName(fileNameOnly).first().getOrNull()
                                }
                                val shouldSkip = existingPage != null && fileModTime != 0L && 
                                    existingPage.updatedAt.toEpochMilliseconds() >= fileModTime
                                
                                if (shouldSkip) return@count true

                                if (isPriorityFile(filePath)) return@count true
                                
                                val content = fileSystem.readFile(filePath) ?: return@count false
                                try {
                                    val parseResult = parsePageWithoutSaving(filePath, content, mode)
                                    val updatedPage = parseResult.page
                                    pagesToSave.add(updatedPage)
                                    if (parseResult.blocks.isNotEmpty()) {
                                        blocksToSaveByPage[updatedPage.uuid] = parseResult.blocks.toMutableList()
                                    }
                                    pageUuidsToDelete.add(updatedPage.uuid)
                                    true
                                } catch (e: Exception) {
                                    logger.error("Failed to parse file: $filePath", e)
                                    false
                                }
                            }
                            
                            if (pagesToSave.isNotEmpty()) {
                                pagesToSave.forEach { pageRepository.savePage(it) }
                            }
                            
                            if (pageUuidsToDelete.isNotEmpty()) {
                                pageUuidsToDelete.forEach { pageUuid ->
                                    blockRepository.deleteBlocksForPage(pageUuid)
                                }
                            }
                            
                            if (blocksToSaveByPage.isNotEmpty()) {
                                blocksToSaveByPage.values.forEach { blocks ->
                                    blockRepository.saveBlocks(blocks)
                                }
                            }
                            
                            processedCount += chunk.size
                            onProgress("Loading $path... ($processedCount/$total)")
                            count
                        } finally {
                            PerformanceMonitor.endTrace("processChunk")
                        }
                    }
                }.awaitAll().sum()
            }
        } finally {
            PerformanceMonitor.endTrace("loadDirectory")
        }
    }
    
    private val fileLocksMutex = Mutex()
    private val fileLocks = mutableMapOf<String, Mutex>()
    private val priorityFilesMutex = Mutex()
    private val priorityFiles = mutableSetOf<String>()

    private suspend fun getFileLock(path: String): Mutex {
        return fileLocksMutex.withLock { fileLocks.getOrPut(path) { Mutex() } }
    }

    private suspend fun tryAddPriorityFile(path: String): Boolean {
        return priorityFilesMutex.withLock {
            if (priorityFiles.contains(path)) false else {
                priorityFiles.add(path)
                true
            }
        }
    }

    private suspend fun removePriorityFile(path: String) {
        priorityFilesMutex.withLock { priorityFiles.remove(path) }
    }

    private suspend fun isPriorityFile(path: String): Boolean {
        return priorityFilesMutex.withLock { priorityFiles.contains(path) }
    }

    private data class ParseResult(
        val page: Page,
        val blocks: List<Block>
    )
    
    private suspend fun parsePageWithoutSaving(filePath: String, content: String, mode: ParseMode = ParseMode.FULL): ParseResult {
        val fileName = filePath.replace("\\", "/").substringAfterLast("/")
        val name = fileName.removeSuffix(".md")
        val isJournal = filePath.contains("/journals/")
        val journalDate = if (isJournal) JournalUtils.parseJournalDate(name) else null
        
        val now = Clock.System.now()
        val existingPageResult = pageRepository.getPageByName(name).first()
        val existingPage = existingPageResult.getOrNull()
        
        val pageUuid = existingPage?.uuid ?: UuidGenerator.generateV7()
        val createdAt = existingPage?.createdAt ?: now
        val currentVersion = existingPage?.version ?: 0L
        
        val parsedPage = markdownParser.parsePage(content)
        val pageWithMetadata = Page(
            uuid = pageUuid,
            name = name,
            namespace = null,
            filePath = filePath,
            createdAt = createdAt,
            updatedAt = now,
            version = currentVersion,
            properties = parsedPage.properties,
            isFavorite = false,
            isJournal = isJournal,
            journalDate = journalDate,
            isContentLoaded = mode == ParseMode.FULL
        )
        
        val existingBlocksResult = blockRepository.getBlocksForPage(pageUuid).first()
        val existingBlocks = existingBlocksResult.getOrNull() ?: emptyList()
        val existingVersions = existingBlocks.associate { it.uuid to it.version }
        val existingContent = existingBlocks.associate { it.uuid to it.content }

        val blocks = when (mode) {
            ParseMode.METADATA_ONLY -> {
                val rootBlocks = parsedPage.blocks.filter { it.level == 0 }
                val blocksList = mutableListOf<Block>()
                processParsedBlocks(
                    rootBlocks, filePath, pageUuid, null, 0, now,
                    blocksList, ParseMode.METADATA_ONLY,
                    existingVersions, existingContent
                )
                blocksList
            }
            ParseMode.FULL -> {
                val blocksList = mutableListOf<Block>()
                processParsedBlocks(
                    parsedPage.blocks, filePath, pageUuid, null, 0, now,
                    blocksList, ParseMode.FULL,
                    existingVersions, existingContent
                )
                blocksList
            }
        }
        
        return ParseResult(page = pageWithMetadata, blocks = blocks)
    }
    
    private suspend fun parseAndSavePage(filePath: String, content: String, mode: ParseMode = ParseMode.FULL) {
        if (mode == ParseMode.METADATA_ONLY && isPriorityFile(filePath)) return

        val lock = getFileLock(filePath)
        lock.withLock {
            if (mode == ParseMode.METADATA_ONLY && isPriorityFile(filePath)) return

            PerformanceMonitor.startTrace("parseAndSavePage")
            try {
                val fileName = filePath.replace("\\", "/").substringAfterLast("/")
                val name = fileName.removeSuffix(".md")
                val journalDate = if (filePath.contains("/journals/")) JournalUtils.parseJournalDate(name) else null
                val isJournal = journalDate != null
                val now = Clock.System.now()
                
                val existingPageResult = pageRepository.getPageByName(name).first()
                val existingPage = existingPageResult.getOrNull()

                if (mode == ParseMode.METADATA_ONLY && existingPage?.isContentLoaded == true) return

                if (mode == ParseMode.FULL && existingPage?.isContentLoaded == true) {
                     val fileModTime = fileSystem.getLastModifiedTime(filePath) ?: 0L
                     val blocksResult = blockRepository.getBlocksForPage(existingPage.uuid).first()
                     val blocks = blocksResult.getOrNull() ?: emptyList()
                     val allBlocksLoaded = blocks.all { it.isLoaded }

                     if (fileModTime != 0L && 
                         existingPage.updatedAt.toEpochMilliseconds() >= fileModTime &&
                         allBlocksLoaded) return
                }

                val pageUuid = existingPage?.uuid ?: UuidGenerator.generateV7()
                val createdAt = existingPage?.createdAt ?: now
                
                var page = Page(
                    uuid = pageUuid,
                    name = name,
                    createdAt = createdAt,
                    updatedAt = now,
                    version = existingPage?.version ?: 0L,
                    properties = emptyMap(),
                    isFavorite = existingPage?.isFavorite ?: false,
                    isJournal = isJournal,
                    journalDate = journalDate,
                    filePath = filePath,
                    isContentLoaded = mode == ParseMode.FULL
                )
                
                val parsedPage = markdownParser.parsePage(content, mode)
                val blocksToSave = mutableListOf<Block>()
                var firstBlockSkipped = false
                
                if (parsedPage.blocks.isNotEmpty()) {
                    val firstBlock = parsedPage.blocks.first()
                    if (firstBlock.content.trim().isEmpty() && firstBlock.properties.isNotEmpty()) {
                        page = page.copy(properties = firstBlock.properties)
                        firstBlockSkipped = true
                    }
                }
                
                pageRepository.savePage(page)
                
                val existingBlocksResult = blockRepository.getBlocksForPage(pageUuid).first()
                val existingBlocks = existingBlocksResult.getOrNull() ?: emptyList()
                val existingVersions = existingBlocks.associate { it.uuid to it.version }
                val existingContent = existingBlocks.associate { it.uuid to it.content }

                val rootBlocks = if (firstBlockSkipped) parsedPage.blocks.drop(1) else parsedPage.blocks
                processParsedBlocks(
                    parsedBlocks = rootBlocks,
                    pagePath = filePath,
                    pageUuid = pageUuid,
                    parentUuid = null,
                    baseLevel = 0,
                    now = now,
                    destinationList = blocksToSave,
                    mode = mode,
                    existingVersions = existingVersions,
                    existingContent = existingContent
                )
                
                if (blocksToSave.isNotEmpty()) {
                    blockRepository.deleteBlocksForPage(pageUuid)
                    blockRepository.saveBlocks(blocksToSave)
                }
                
                // Update mod time in watcher cache so we don't re-trigger from our own write
                val updatedModTime = fileSystem.getLastModifiedTime(filePath) ?: 0L
                if (updatedModTime != 0L) {
                    knownFilesModTimes[filePath] = updatedModTime
                }
            } finally {
                PerformanceMonitor.endTrace("parseAndSavePage")
            }
        }
    }

    private suspend fun processParsedBlocks(
        parsedBlocks: List<ParsedBlock>,
        pagePath: String,
        pageUuid: String,
        parentUuid: String?,
        baseLevel: Int,
        now: kotlinx.datetime.Instant,
        destinationList: MutableList<Block>,
        mode: ParseMode,
        existingVersions: Map<String, Long> = emptyMap(),
        existingContent: Map<String, String> = emptyMap()
    ) {
        parsedBlocks.forEachIndexed { index, parsedBlock ->
            val blockUuid = generateUuid(parsedBlock, pagePath, index)
            val currentVersion = existingVersions[blockUuid] ?: 0L
            val oldContent = existingContent[blockUuid]

            val versionToSave = if (oldContent == parsedBlock.content) currentVersion else {
                if (currentVersion > 0) currentVersion + 1 else 0L
            }

            val mergedProperties = parsedBlock.properties.toMutableMap()
            parsedBlock.scheduled?.let { mergedProperties["scheduled"] = it }
            parsedBlock.deadline?.let { mergedProperties["deadline"] = it }

            val block = Block(
                uuid = blockUuid,
                pageUuid = pageUuid,
                parentUuid = parentUuid,
                leftUuid = null,
                content = parsedBlock.content,
                level = baseLevel,
                position = index,
                createdAt = now,
                updatedAt = now,
                version = versionToSave,
                properties = mergedProperties,
                isLoaded = mode == ParseMode.FULL
            )

            destinationList.add(block)
            
            if (parsedBlock.children.isNotEmpty()) {
                processParsedBlocks(
                    parsedBlocks = parsedBlock.children,
                    pagePath = pagePath,
                    pageUuid = pageUuid,
                    parentUuid = blockUuid,
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
