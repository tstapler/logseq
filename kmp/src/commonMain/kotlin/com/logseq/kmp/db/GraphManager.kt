package com.logseq.kmp.db

import com.logseq.kmp.model.GraphInfo
import com.logseq.kmp.model.GraphRegistry
import com.logseq.kmp.platform.FileSystem
import com.logseq.kmp.platform.PlatformSettings
import com.logseq.kmp.repository.GraphBackend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

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
    private val _graphRegistry = MutableStateFlow(GraphRegistry())
    val graphRegistry: StateFlow<GraphRegistry> = _graphRegistry.asStateFlow()
    
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
        }
    }
    
    private fun saveRegistry() {
        val registryJson = json.encodeToString(_graphRegistry.value)
        platformSettings.putString("graph_registry", registryJson)
    }
    
    fun graphIdFromPath(path: String): String = 
        sha256Hex(path).take(16)
    
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
    
    fun switchGraph(id: String) {
        val registry = _graphRegistry.value
        val graphInfo = registry.graphs.firstOrNull { it.id == id }
        if (graphInfo == null) return
        
        // Cancel any existing coroutines for the previous graph
        val currentGraphId = registry.activeGraphId
        currentGraphId?.let { activeGraphJobs.remove(it)?.cancel() }
        
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
    
    /**
     * Clean up all resources when shutting down
     */
    fun shutdown() {
        // Cancel all graph-specific coroutines
        activeGraphJobs.values.forEach { it.cancel() }
        activeGraphJobs.clear()
    }
    
    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}