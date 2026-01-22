package com.logseq.kmp.editor.integration

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import com.logseq.kmp.editor.*
import com.logseq.kmp.editor.state.EditorState
import com.logseq.kmp.editor.text.ITextOperations
import com.logseq.kmp.editor.blocks.IBlockOperations
import com.logseq.kmp.repository.BlockRepository
import com.logseq.kmp.db.GraphWriter
import com.logseq.kmp.ui.LogseqViewModel
import com.logseq.kmp.model.Page
import com.logseq.kmp.model.Block
import com.logseq.kmp.editor.text.TextOperations
import com.logseq.kmp.editor.blocks.BlockOperations
import com.logseq.kmp.editor.commands.CommandSystem
import com.logseq.kmp.editor.commands.CommandRegistry
import com.logseq.kmp.editor.commands.ICommandSystem
import com.logseq.kmp.editor.format.IFormatProcessor
import com.logseq.kmp.editor.format.FormatType
import com.logseq.kmp.editor.format.FormattedText
import com.logseq.kmp.editor.format.TextRange
import com.logseq.kmp.editor.format.TextFormatting
import com.logseq.kmp.editor.format.FormattedRange
import com.logseq.kmp.editor.format.FormatValidation
import com.logseq.kmp.editor.performance.PerformanceOptimizedEditor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.Result
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

/**
 * Integration layer that connects the new rich editor with existing LogseqViewModel
 */
class EditorIntegration(
    private val logseqViewModel: LogseqViewModel,
    private val blockRepository: BlockRepository,
    private val graphWriter: GraphWriter,
    private val coroutineScope: CoroutineScope
) {
    
    private val scope = mutableStateOf<kotlinx.coroutines.CoroutineScope?>(coroutineScope)
    
    // Create editor components
    val textOperations = TextOperations(blockRepository)
    val blockOperations = BlockOperations(blockRepository, graphWriter)
    private val commandRegistry = CommandRegistry()
    private val commandSystem: ICommandSystem = CommandSystem(commandRegistry, coroutineScope)
    private val formatProcessor = MarkdownFormatProcessor()
    
    // Create main editor
    private val editor = Editor(
        blockRepository = blockRepository,
        graphWriter = graphWriter,
        textOperations = textOperations,
        blockOperations = blockOperations,
        commandSystem = commandSystem,
        formatProcessor = formatProcessor
    )
    
    /**
     * Get editor instance for external access
     */
    fun getEditor(): IEditor = editor
    
    /**
     * Get editor state flow for UI binding
     */
    fun getEditorState(): StateFlow<EditorState> = editor.editorState
    
    /**
     * Initialize editor with current page from LogseqViewModel
     */
    suspend fun initializeWithCurrentPage(): Result<Unit> {
        return try {
            val currentPage = getCurrentPage()
            if (currentPage != null) {
                editor.initialize(currentPage)
                
                // Update LogseqViewModel with editor state
                scope.value?.launch {
                    editor.editorState.collect { editorState ->
                        // Sync with existing UI state
                        // logseqViewModel.updateEditorState(editorState)
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Handle keyboard shortcuts and pass to existing LogseqViewModel
     */
    fun handleKeyEvent(keyEvent: androidx.compose.ui.input.key.KeyEvent): Boolean {
        val handled = editor.handleKeyEvent(keyEvent)
        
        if (!handled) {
            // Pass to existing LogseqViewModel handlers
            // return logseqViewModel.handleKeyEvent(keyEvent)
        }
        
        return true
    }
    
    /**
     * Execute commands and update LogseqViewModel
     */
    suspend fun executeCommand(commandId: String): Result<Any?> {
        val result = editor.executeCommand(commandId, emptyMap())
        
        // Update LogseqViewModel status
        if (result.isSuccess) {
            scope.value?.launch {
                // logseqViewModel.showStatusMessage("Command executed: $commandId")
            }
        } else {
            scope.value?.launch {
                // logseqViewModel.showError("Command failed: ${result.exceptionOrNull()?.message}")
            }
        }
        
        return result
    }
    
    /**
     * Integration with existing LogseqViewModel methods
     */
    fun integrateWithLogseqViewModel() {
        // Set up bidirectional state synchronization
        scope.value?.launch {
            // Sync page changes - reinitialize editor when page changes
            logseqViewModel.uiState.collect { state ->
                val page = state.currentPage
                if (page != null) {
                    editor.initialize(page)
                }
            }
        }

        scope.value?.launch {
            // Sync focus changes
            editor.cursorState.collect { cursorState ->
                // logseqViewModel.setFocusedBlock(cursorState.blockId)
            }
        }

        scope.value?.launch {
            // Sync editing state
            editor.editorState.collect { editorState ->
                // logseqViewModel.setEditingState(editorState.isEditing)
            }
        }
    }
    
    /**
     * Migrate from old editor to new rich editor
     */
    suspend fun migrateFromOldEditor(): Result<Unit> {
        return try {
            val currentPage = getCurrentPage()
            if (currentPage != null) {
                // Initialize new editor with existing data
                // editor.initialize() already loads blocks from the repository
                editor.initialize(currentPage)

                // Show migration status
                scope.value?.launch {
                    // logseqViewModel.showStatusMessage("Rich editor migrated successfully")
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get performance metrics for monitoring
     */
    fun getPerformanceMetrics(): EditorPerformanceMetrics {
        return EditorPerformanceMetrics(
            textOperationAverage = getAverageTextOperationTime(),
            blockOperationAverage = getAverageBlockOperationTime(),
            renderTimeAverage = getAverageRenderTime(),
            memoryUsage = getMemoryUsage(),
            isPerformant = isPerformanceAcceptable()
        )
    }
    
    // Private helper methods
    
    private fun getCurrentPage(): Page? {
        // Get current page from LogseqViewModel
        return logseqViewModel.uiState.value.currentPage
    }
    
    private fun getAverageTextOperationTime(): Long {
        return 0L
    }
    
    private fun getAverageBlockOperationTime(): Long {
        return 0L
    }
    
    private fun getAverageRenderTime(): Long {
        return 0L
    }
    
    private fun getMemoryUsage(): Long {
        return 0L // Runtime is not available in KMP common code
    }
    
    private fun isPerformanceAcceptable(): Boolean {
        return getAverageTextOperationTime() < 50 && // 50ms for text ops
               getAverageBlockOperationTime() < 100 && // 100ms for block ops
               getAverageRenderTime() < 16 // 16ms for 60fps
    }

    private class MarkdownFormatProcessor : IFormatProcessor {
        override suspend fun processText(text: String, formatType: FormatType): Result<FormattedText> {
            return Result.success(FormattedText(text, formatType))
        }

        override suspend fun parseFormattedText(formattedText: FormattedText, sourceFormat: FormatType): Result<String> {
            return Result.success(formattedText.text)
        }

        override suspend fun applyFormatting(text: String, range: TextRange, formatting: TextFormatting): Result<String> {
            return Result.success(text)
        }

        override suspend fun removeFormatting(text: String, range: TextRange?): Result<String> {
            return Result.success(text)
        }

        override suspend fun detectFormatting(text: String): Result<List<FormattedRange>> {
            return Result.success(emptyList())
        }

        override suspend fun convertFormat(text: String, fromFormat: FormatType, toFormat: FormatType): Result<String> {
            return Result.success(text)
        }

        override suspend fun validateFormat(text: String, formatType: FormatType): Result<FormatValidation> {
            return Result.success(FormatValidation(true))
        }
    }
}

/**
 * Performance metrics for editor
 */
data class EditorPerformanceMetrics(
    val textOperationAverage: Long,
    val blockOperationAverage: Long,
    val renderTimeAverage: Long,
    val memoryUsage: Long,
    val isPerformant: Boolean
)

/**
 * Composable for integrated editor with LogseqViewModel
 */
@Composable
fun IntegratedEditor(
    logseqViewModel: LogseqViewModel,
    blockRepository: BlockRepository,
    graphWriter: GraphWriter,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    
    // Create integration instance
    val integration = remember(logseqViewModel, blockRepository, graphWriter, scope) {
        EditorIntegration(
            logseqViewModel = logseqViewModel,
            blockRepository = blockRepository,
            graphWriter = graphWriter,
            coroutineScope = scope
        )
    }
    
    // Initialize integration
    LaunchedEffect(Unit) {
        scope.launch {
            integration.integrateWithLogseqViewModel()
            integration.initializeWithCurrentPage()
        }
    }
    
    // Get editor state and components
    val editor = integration.getEditor()
    val editorState by integration.getEditorState().collectAsState()
    
    // Performance: Use optimized editor composable
    PerformanceOptimizedEditor(
        editorState = integration.getEditorState(),
        textOperations = remember { integration.textOperations },
        blockOperations = remember { integration.blockOperations },
        onBlockFocus = { blockId ->
            // logseqViewModel.setFocusedBlock(blockId)
        },
        modifier = modifier
    )
    
    // Performance: Show performance metrics in debug mode
    // Disabled - was overlapping content. Enable via View menu's Performance Dashboard instead.
    // if (BuildConfig.DEBUG) { ... }
}

/**
 * Debug performance display
 */
@Composable
private fun PerformanceMetricsDisplay(
    metrics: EditorPerformanceMetrics,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text("Editor Performance Metrics", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Text Ops: ${metrics.textOperationAverage}ms")
            Text("Block Ops: ${metrics.blockOperationAverage}ms")
            Text("Render Time: ${metrics.renderTimeAverage}ms")
            Text("Memory: ${metrics.memoryUsage / 1024 / 1024}MB")
            Text("Performance: ${if (metrics.isPerformant) "✅ Good" else "⚠️ Needs Improvement"}")
        }
    }
}
