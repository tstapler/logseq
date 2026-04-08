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
    
    // Track current factory for lifecycle management
    private var currentFactory: com.logseq.kmp.repository.RepositoryFactory? = null
    
    // Track active coroutines for cleanup during graph switches
    private val activeGraphJobs = mutableMapOf<String, CoroutineScope>()
    
    init {
        loadRegistry()
    }
    
    private fun loadRegistry() {
        val registryJson = platformSettings.getString("graph_registry", "")
        if (registryJson.isNotEmpty()) {
            try {
                val registry = json.decodeFromString<GraphRegistry>(registryJson)
                _graphRegistry.value = registry
            } catch (e: Exception) {
                // Corrupted registry - start fresh
                _graphRegistry.value = GraphRegistry()
                saveRegistry()
            }
        } else {
            // No registry exists - check for migration from single-graph setup
            migrateFromSingleGraph()
        }
    }
    
    /**
     * Migrate from the old single-graph setup to multi-graph.
     * - Checks for existing `lastGraphPath` setting
     * - Renames `logseq.db` to `logseq-graph-{hash}.db`
     * - Creates GraphRegistry with the migrated graph as active
     */
    private fun migrateFromSingleGraph() {
        val lastGraphPath = platformSettings.getString("lastGraphPath", "")
        if (lastGraphPath.isEmpty()) {
            // No previous graph - fresh install
            return
        }
        
        try {
            val expandedPath = fileSystem.expandTilde(lastGraphPath)
            val graphId = graphIdFromPath(expandedPath)
            
            // Get the database directory (platform-specific)
            val dbDir = driverFactory.getDatabaseDirectory()
            val oldDbPath = "$dbDir/logseq.db"
            val newDbPath = driverFactory.getDatabaseUrl(graphId).substringAfter("jdbc:sqlite:")
            
            // Check if old database exists and rename it
            if (fileSystem.fileExists(oldDbPath)) {
                // Try to rename the file (this is platform-specific)
                val renamed = migrateDatabaseFile(oldDbPath, newDbPath)
                if (renamed) {
                    // Also try to migrate WAL and SHM files if they exist
                    migrateWalShmFiles(dbDir, graphId)
                }
            }
            
            // Create graph registry with the migrated graph
            val displayName = lastGraphPath.substringAfterLast("/")
                .substringAfterLast("\\")
                .ifEmpty { lastGraphPath }
            
            val graphInfo = GraphInfo(
                id = graphId,
                path = expandedPath,
                displayName = displayName,
                addedAt = System.currentTimeMillis()
            )
            
            val registry = GraphRegistry(
                activeGraphId = graphId,
                graphs = listOf(graphInfo)
            )
            _graphRegistry.value = registry
            saveRegistry()
            
            println("Migration complete: graph '$displayName' migrated to ID $graphId")
        } catch (e: Exception) {
            println("Migration failed: ${e.message}")
            // Start fresh if migration fails
            _graphRegistry.value = GraphRegistry()
            saveRegistry()
        }
    }
    
    /**
     * Migrate the database file from old path to new path.
     * Returns true if successful.
     */
    private fun migrateDatabaseFile(oldPath: String, newPath: String): Boolean {
        return try {
            // Use Java File for rename (works on JVM/Android)
            val oldFile = java.io.File(oldPath)
            val newFile = java.io.File(newPath)
            
            if (!oldFile.exists()) return false
            if (newFile.exists()) return true // Already migrated
            
            // Try atomic rename first
            val renamed = oldFile.renameTo(newFile)
            if (!renamed) {
                // Fall back to copy-and-delete
                oldFile.copyTo(newFile)
                oldFile.delete()
            }
            true
        } catch (e: Exception) {
            println("Failed to migrate database file: ${e.message}")
            false
        }
    }
    
    /**
     * Migrate WAL and SHM files (SQLite write-ahead log files)
     */
    private fun migrateWalShmFiles(dbDir: String, graphId: String) {
        try {
            val walOld = java.io.File("$dbDir/logseq.db-wal")
            val walNew = java.io.File(driverFactory.getDatabaseUrl(graphId).substringAfter("jdbc:sqlite:") + "-wal")
            if (walOld.exists() && !walNew.exists()) {
                walOld.renameTo(walNew)
            }
            
            val shmOld = java.io.File("$dbDir/logseq.db-shm")
            val shmNew = java.io.File(driverFactory.getDatabaseUrl(graphId).substringAfter("jdbc:sqlite:") + "-shm")
            if (shmOld.exists() && !shmNew.exists()) {
                shmOld.renameTo(shmNew)
            }
        } catch (e: Exception) {
            // Non-critical - WAL/SHM files may not exist
        }
    }
    
    private fun saveRegistry() {
        val registryJson = json.encodeToString(_graphRegistry.value)
        platformSettings.putString("graph_registry", registryJson)
    }
    
    fun graphIdFromPath(path: String): String = 
        ContentHasher.sha256(path).take(16)
    
    fun addGraph(path: String): String {
        // Use expanded path for consistent ID generation
        val expandedPath = fileSystem.expandTilde(path)
        val graphId = graphIdFromPath(expandedPath)
        val displayName = path.substringAfterLast("/").substringAfterLast("\\").ifEmpty { path }
        
        val info = GraphInfo(
            id = graphId,
            path = expandedPath,
            displayName = displayName,
            addedAt = System.currentTimeMillis()
        )
        
        val registry = _graphRegistry.value
        if (!registry.graphIds.contains(graphId)) {
            val updated = registry.copy(
                graphs = registry.graphs + info
            )
            _graphRegistry.value = updated
            saveRegistry()
        }
        
        return graphId
    }
    
    fun removeGraph(id: String): Boolean {
        // Cancel any active coroutines for this graph
        activeGraphJobs.remove(id)?.cancel()
        
        val registry = _graphRegistry.value
        if (!registry.graphIds.contains(id)) return false
        
        // Don't allow removing active graph
        if (registry.activeGraphId == id) return false
        
        val updated = registry.copy(
            graphs = registry.graphs.filter { it.id != id }
        )
        _graphRegistry.value = updated
        saveRegistry()
        return true
    }
    
    fun renameGraph(id: String, newName: String): Boolean {
        val registry = _graphRegistry.value
        val graphIndex = registry.graphs.indexOfFirst { it.id == id }
        if (graphIndex == -1) return false
        
        val updatedGraphs = registry.graphs.toMutableList()
        updatedGraphs[graphIndex] = updatedGraphs[graphIndex].copy(displayName = newName)
        
        val updated = registry.copy(graphs = updatedGraphs)
        _graphRegistry.value = updated
        saveRegistry()
        return true
    }
    
    /**
     * Switch to a different graph.
     * Closes the current database connection and opens a new one for the target graph.
     */
    fun switchGraph(id: String) {
        val registry = _graphRegistry.value
        val graphInfo = registry.graphs.firstOrNull { it.id == id }
        if (graphInfo == null) return
        
        // Cancel any existing coroutines for the previous graph
        val currentGraphId = registry.activeGraphId
        currentGraphId?.let { activeGraphJobs.remove(it)?.cancel() }
        
        // Close current factory and its database connection
        currentFactory?.close()
        currentFactory = null
        _activeRepositorySet.value = null
        
        // Create new database for this graph (platform-agnostic URL)
        val dbUrl = driverFactory.getDatabaseUrl(id)
        val factory = com.logseq.kmp.repository.RepositoryFactoryImpl(driverFactory, dbUrl)
        currentFactory = factory
        val repoSet = factory.createRepositorySet(GraphBackend.SQLDELIGHT)
        _activeRepositorySet.value = repoSet
        
        // Update active graph
        val updatedRegistry = registry.copy(activeGraphId = id)
        _graphRegistry.value = updatedRegistry
        saveRegistry()
        
        // Create a new scope for this graph's operations
        val graphScope = CoroutineScope(coroutineScope.coroutineContext)
        activeGraphJobs[id] = graphScope
    }
    
    fun getGraphInfo(id: String): GraphInfo? {
        return _graphRegistry.value.graphs.firstOrNull { it.id == id }
    }
    
    fun getActiveGraphId(): String? {
        return _graphRegistry.value.activeGraphId
    }
    
    fun getActiveGraphInfo(): GraphInfo? {
        val activeId = _graphRegistry.value.activeGraphId ?: return null
        return getGraphInfo(activeId)
    }
    
    fun getGraphIds(): Set<String> = 
        _graphRegistry.value.graphs.map { it.id }.toSet()
    
    fun getActiveRepositorySet(): RepositorySet? = _activeRepositorySet.value
    
    /**
     * Clean up all resources when shutting down
     */
    fun shutdown() {
        // Cancel all graph-specific coroutines
        activeGraphJobs.values.forEach { it.cancel() }
        activeGraphJobs.clear()
        
        // Close database connection
        currentFactory?.close()
        
        // Clear repository set
        _activeRepositorySet.value = null
        currentFactory = null
    }
}
