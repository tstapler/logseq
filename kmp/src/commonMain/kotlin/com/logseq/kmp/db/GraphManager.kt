package com.logseq.kmp.db

import com.logseq.kmp.model.GraphInfo
import com.logseq.kmp.model.GraphRegistry
import com.logseq.kmp.platform.FileSystem
import com.logseq.kmp.platform.PlatformSettings
import com.logseq.kmp.repository.GraphBackend
import com.logseq.kmp.repository.RepositorySet
import com.logseq.kmp.util.ContentHasher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.datetime.Clock

expect class PlatformUtils() {
    fun getDatabaseDirectory(): String
    fun getDatabasePath(graphId: String? = null): String
    fun migrateDatabaseFile(oldPath: String, newPath: String): Boolean
}

/**
 * Manages multiple graphs and their respective database connections.
 * Replaces the Repositories singleton with per-graph RepositorySets.
 */
class GraphManager(
    private val platformSettings: PlatformSettings,
    private val driverFactory: DriverFactory,
    private val fileSystem: FileSystem,
    private val coroutineScope: CoroutineScope
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    
    private val _graphRegistry = MutableStateFlow(GraphRegistry())
    val graphRegistry: StateFlow<GraphRegistry> = _graphRegistry.asStateFlow()
    
    private val _activeRepositorySet = MutableStateFlow<RepositorySet?>(null)
    val activeRepositorySet: StateFlow<RepositorySet?> = _activeRepositorySet.asStateFlow()
    
    // Track current driver for lifecycle management
    private var currentFactory: com.logseq.kmp.repository.RepositoryFactoryImpl? = null
    
    // Track active coroutines for cleanup during graph switches
    private val activeGraphJobs = mutableMapOf<String, CoroutineScope>()
    
    init {
        loadRegistry()
    }
    
    private fun loadRegistry() {
        val stored = platformSettings.getString("graph_registry", "")
        if (stored.isNotEmpty()) {
            try {
                _graphRegistry.value = json.decodeFromString<GraphRegistry>(stored)
            } catch (e: Exception) {
                println("Failed to load graph registry: ${e.message}")
            }
        }
    }
    
    private suspend fun saveRegistry() {
        mutex.withLock {
            val jsonStr = json.encodeToString(_graphRegistry.value)
            platformSettings.putString("graph_registry", jsonStr)
        }
    }
    
    private fun createGraphInfo(path: String): GraphInfo? {
        val canonicalPath = fileSystem.expandTilde(path)
        val graphId = ContentHasher.sha256(canonicalPath).substring(0, 16)
        
        // Check for duplicates
        if (_graphRegistry.value.graphs.any { it.path == canonicalPath }) {
            return null
        }
        
        val info = GraphInfo(
            id = graphId,
            path = canonicalPath,
            displayName = path.substringAfterLast("/").substringAfterLast("\\"),
            addedAt = Clock.System.now().toEpochMilliseconds()
        )
        
        return info
    }
    
    suspend fun initialize(): Boolean {
        val lastGraphPath = platformSettings.getString("lastGraphPath", "")
        val stored = platformSettings.getString("graph_registry", "")
        
        return if (stored.isEmpty() && lastGraphPath.isNotEmpty()) {
            // Migration: convert single-graph to multi-graph
            val info = createGraphInfo(lastGraphPath)
            if (info != null) {
                _graphRegistry.value = _graphRegistry.value.copy(
                    graphs = _graphRegistry.value.graphs + info,
                    activeGraphId = info.id
                )
                switchGraph(info.id) != null
            } else {
                false
            }
        } else if (_graphRegistry.value.activeGraphId != null) {
            switchGraph(_graphRegistry.value.activeGraphId!!) != null
        } else {
            false
        }
    }
    
    fun getGraphs(): List<GraphInfo> = _graphRegistry.value.graphs
    
    fun getActiveGraphId(): String? = _graphRegistry.value.activeGraphId
    
    fun getActiveGraphInfo(): GraphInfo? {
        val id = _graphRegistry.value.activeGraphId ?: return null
        return _graphRegistry.value.graphs.find { it.id == id }
    }
    
    suspend fun addGraph(path: String): String? {
        val info = createGraphInfo(path) ?: return null
        _graphRegistry.value = _graphRegistry.value.copy(
            graphs = _graphRegistry.value.graphs + info
        )
        saveRegistry()
        return info.id
    }
    
    suspend fun addNewGraph(path: String): GraphInfo? {
        val info = createGraphInfo(path) ?: return null
        _graphRegistry.value = _graphRegistry.value.copy(
            graphs = _graphRegistry.value.graphs + info
        )
        saveRegistry()
        return info
    }
    
    suspend fun removeGraph(id: String) {
        _graphRegistry.value = _graphRegistry.value.copy(
            graphs = _graphRegistry.value.graphs.filter { it.id != id },
            activeGraphId = if (_graphRegistry.value.activeGraphId == id) null else _graphRegistry.value.activeGraphId
        )
        saveRegistry()
    }
    
    suspend fun renameGraph(id: String, newName: String) {
        val graphs = _graphRegistry.value.graphs.map {
            if (it.id == id) it.copy(displayName = newName) else it
        }
        _graphRegistry.value = _graphRegistry.value.copy(graphs = graphs)
        saveRegistry()
    }
    
    suspend fun switchGraph(id: String?): RepositorySet? {
        if (id == null) {
            _activeRepositorySet.value = null
            return null
        }
        
        val info = _graphRegistry.value.graphs.find { it.id == id } ?: return null
        
        // Cancel any active graph jobs
        activeGraphJobs.values.forEach { it.cancel() }
        activeGraphJobs.clear()
        
        // Create new
        val dbPath = PlatformUtils().getDatabasePath(id)
        val jdbcUrl = "jdbc:sqlite:$dbPath"
        
        currentFactory = com.logseq.kmp.repository.RepositoryFactoryImpl(driverFactory, jdbcUrl)
        
        val repoSet = RepositorySet(
            blockRepository = currentFactory!!.createBlockRepository(GraphBackend.SQLDELIGHT),
            pageRepository = currentFactory!!.createPageRepository(GraphBackend.SQLDELIGHT),
            propertyRepository = currentFactory!!.createPropertyRepository(GraphBackend.SQLDELIGHT),
            referenceRepository = currentFactory!!.createReferenceRepository(GraphBackend.SQLDELIGHT),
            searchRepository = currentFactory!!.createSearchRepository(GraphBackend.SQLDELIGHT)
        )
        
        _activeRepositorySet.value = repoSet
        _graphRegistry.value = _graphRegistry.value.copy(activeGraphId = id)
        
        // Update last path
        platformSettings.putString("lastGraphPath", info.path)
        
        saveRegistry()
        
        return repoSet
    }
}
