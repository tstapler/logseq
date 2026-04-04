package com.logseq.kmp.editor

import androidx.compose.runtime.*
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isCtrlPressed
import com.logseq.kmp.model.Page
import com.logseq.kmp.model.CursorState
import com.logseq.kmp.editor.commands.*
import com.logseq.kmp.model.Block
import com.logseq.kmp.repository.BlockRepository
import com.logseq.kmp.db.GraphWriter
import com.logseq.kmp.editor.text.ITextOperations
import com.logseq.kmp.editor.state.EditorState
import com.logseq.kmp.editor.state.EditorConfig
import com.logseq.kmp.editor.state.EditorMode
import com.logseq.kmp.editor.blocks.IBlockOperations
import com.logseq.kmp.editor.blocks.DeleteStrategy
import com.logseq.kmp.editor.format.IFormatProcessor
import com.logseq.kmp.performance.PerformanceMonitor
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.Result

/**
 * Main editor implementation that orchestrates all editing operations
 */
class Editor(
    private val blockRepository: BlockRepository,
    private val graphWriter: GraphWriter,
    private val textOperations: ITextOperations,
    private val blockOperations: IBlockOperations,
    private val commandSystem: ICommandSystem,
    private val formatProcessor: IFormatProcessor
) : IEditor {

    private val scope = mutableStateOf<kotlinx.coroutines.CoroutineScope?>(null)
    private val _editorState = MutableStateFlow(EditorState(textOperations = textOperations))
    private val _currentPage = MutableStateFlow<Page?>(null)
    private val _cursorState = MutableStateFlow(CursorState())

    override val editorState: StateFlow<EditorState> = _editorState.asStateFlow()
    override val currentPage: StateFlow<Page?> = _currentPage.asStateFlow()
    override val cursorState: StateFlow<CursorState> = _cursorState.asStateFlow()

    override val config: EditorConfig = EditorConfig()

    override suspend fun initialize(page: Page): Result<Unit> {
        return try {
            val traceId = com.logseq.kmp.performance.PerformanceMonitor.startTrace("editor-initialize")
            
            // Set current page
            _currentPage.value = page
            
            // Load blocks for this page
            val blocks = blockRepository.getBlocksForPage(page.uuid).first().getOrNull().orEmpty()
            
            // Update editor state
            _editorState.update { it.copy(
                isLoading = false,
                blocks = blocks,
                isEditing = true
            ) }
            
            // Initialize text states for all blocks with their content
            blocks.forEach { block: Block ->
                textOperations.initializeBlock(block.uuid, block.content)
            }
            
            com.logseq.kmp.performance.PerformanceMonitor.endTrace(traceId)
            Result.success(Unit)
        } catch (e: Exception) {
            _editorState.update { it.copy(isLoading = false) }
            Result.failure(e)
        }
    }

    override suspend fun dispose() {
        _editorState.update { EditorState(textOperations = textOperations) }
        _currentPage.value = null
        _cursorState.value = CursorState()
    }

    override fun handleKeyEvent(keyEvent: KeyEvent): Boolean {
        val key = keyEvent.key
        when {
            keyEvent.isCtrlPressed && key == Key.S -> {
                // Save
                scope.value?.launch {
                    executeCommand("system.save", emptyMap())
                }
                return true
            }
            keyEvent.isCtrlPressed && key == Key.Z -> {
                // Undo
                scope.value?.launch {
                    executeCommand("system.undo", emptyMap())
                }
                return true
            }
            keyEvent.isCtrlPressed && key == Key.Y -> {
                // Redo
                scope.value?.launch {
                    executeCommand("system.redo", emptyMap())
                }
                return true
            }
            keyEvent.isCtrlPressed && key == Key.F -> {
                // Search
                scope.value?.launch {
                    executeCommand("navigation.search", emptyMap())
                }
                return true
            }
            key == Key.Escape -> {
                // Command palette
                scope.value?.launch {
                    _editorState.update { it.copy(mode = EditorMode.VIEW) }
                }
                return true
            }
        }
        
        return false
    }

    override suspend fun executeCommand(command: EditorCommand): Result<Unit> {
        return try {
            val traceId = com.logseq.kmp.performance.PerformanceMonitor.startTrace("execute-command")
            
            val currentBlockId = _cursorState.value.blockId
            val textState = currentBlockId?.let { blockId: String -> textOperations.getTextState(blockId).value }
            val content = textState?.content ?: ""
            val selection = textState?.selection
            
            val context = CommandContext(
                currentText = content,
                selectionStart = selection?.range?.start ?: _cursorState.value.position,
                selectionEnd = selection?.range?.end ?: _cursorState.value.position,
                currentBlockId = currentBlockId,
                currentBlockContent = content,
                currentPageId = _currentPage.value?.uuid,
                cursorPosition = _cursorState.value.position
            )
            
            val result = command.execute(context)
            
            com.logseq.kmp.performance.PerformanceMonitor.endTrace(traceId)
            if (result is CommandResult.Success) Result.success(Unit) else Result.failure(Exception((result as CommandResult.Error).message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun executeCommand(commandId: String, args: Map<String, Any>): Result<Any?> {
        return try {
            val currentBlockId = _cursorState.value.blockId
            val textState = currentBlockId?.let { textOperations.getTextState(it).value }
            val content = textState?.content ?: ""
            val selection = textState?.selection
            
            val context = CommandContext(
                currentText = content,
                selectionStart = selection?.range?.start ?: _cursorState.value.position,
                selectionEnd = selection?.range?.end ?: _cursorState.value.position,
                currentBlockId = currentBlockId,
                currentBlockContent = content,
                currentPageId = _currentPage.value?.uuid,
                cursorPosition = _cursorState.value.position,
                additionalData = args
            )

            val result = commandSystem.executeCommand(commandId, context)
            
            when (result) {
                is CommandResult.Success -> Result.success(result.data)
                is CommandResult.Error -> Result.failure(result.exception ?: Exception(result.message))
                is CommandResult.Partial -> Result.success(mapOf("completed" to result.completed, "total" to result.total))
                is CommandResult.Nothing -> Result.success(null)
                else -> Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Editor-specific helper methods

    suspend fun insertText(text: String): Result<Unit> {
        val currentBlockId = _cursorState.value.blockId
        return if (currentBlockId != null) {
            textOperations.insertText(currentBlockId, text)
        } else {
            Result.failure(IllegalStateException("No block focused"))
        }
    }

    suspend fun createNewBlock(content: String = ""): Result<Block> {
        val currentPage = _currentPage.value
        val focusedBlockId = _cursorState.value.blockId
        
        return if (currentPage != null) {
            // Create new block after focused block or at root
            var parentUuid: String? = null
            
            if (focusedBlockId != null) {
                val focusedBlock = blockRepository.getBlockByUuid(focusedBlockId).first().getOrNull()
                // If we have a focused block, we likely want to create a sibling (same parent)
                parentUuid = focusedBlock?.parentUuid
            }
            
            blockOperations.createBlock(
                pageUuid = currentPage.uuid,
                content = content,
                parentId = parentUuid
            ).also { result ->
                if (result.isSuccess) {
                    // Update editor state
                    val newBlock = result.getOrNull()
                    if (newBlock != null) {
                        _editorState.update { current ->
                        current.copy(
                            blocks = current.blocks + listOf(newBlock)
                        )
                        }
                        // Focus new block
                        _cursorState.value = CursorState(
                            blockId = newBlock.uuid,
                            position = content.length
                        )
                    }
                }
            }
        } else {
            Result.failure(IllegalStateException("No page loaded"))
        }
    }

    suspend fun deleteCurrentBlock(): Result<Unit> {
        val currentBlockId = _cursorState.value.blockId
        return if (currentBlockId != null) {
            blockOperations.deleteBlock(currentBlockId, false).also { result ->
                if (result.isSuccess) {
                    // Update editor state
                    _editorState.update { current ->
                        current.copy(
                            blocks = current.blocks.filter { block -> block.uuid != currentBlockId }
                        )
                    }
                    // Move focus to next sibling or parent
                    moveToNextBlockOrParent()
                }
            }
        } else {
            Result.failure(IllegalStateException("No block focused"))
        }
    }

    suspend fun indentCurrentBlock(): Result<Unit> {
        val currentBlockId = _cursorState.value.blockId
        return if (currentBlockId != null) {
            blockOperations.indentBlock(currentBlockId).also { result ->
                if (result.isSuccess) {
                    refreshBlocks()
                }
            }
        } else {
            Result.failure(IllegalStateException("No block focused"))
        }
    }

    suspend fun outdentCurrentBlock(): Result<Unit> {
        val currentBlockId = _cursorState.value.blockId
        return if (currentBlockId != null) {
            blockOperations.outdentBlock(currentBlockId).also { result ->
                if (result.isSuccess) {
                    refreshBlocks()
                }
            }
        } else {
            Result.failure(IllegalStateException("No block focused"))
        }
    }

    suspend fun moveBlockUp(): Result<Unit> {
        val currentBlockId = _cursorState.value.blockId
        return if (currentBlockId != null) {
            blockOperations.moveBlockUp(currentBlockId).also { result ->
                if (result.isSuccess) {
                    refreshBlocks()
                }
            }
        } else {
            Result.failure(IllegalStateException("No block focused"))
        }
    }

    suspend fun moveBlockDown(): Result<Unit> {
        val currentBlockId = _cursorState.value.blockId
        return if (currentBlockId != null) {
            blockOperations.moveBlockDown(currentBlockId).also { result ->
                if (result.isSuccess) {
                    refreshBlocks()
                }
            }
        } else {
            Result.failure(IllegalStateException("No block focused"))
        }
    }

    suspend fun splitCurrentBlock(): Result<Block> {
        val currentBlockId = _cursorState.value.blockId
        val position = _cursorState.value.position
        
        return if (currentBlockId != null) {
            blockOperations.splitBlock(currentBlockId, position).also { result ->
                if (result.isSuccess) {
                    refreshBlocks()
                    // Focus the new block
                    val newBlock = result.getOrNull()
                    if (newBlock != null) {
                        _cursorState.value = CursorState(
                            blockId = newBlock.uuid,
                            position = 0
                        )
                    }
                }
            }
        } else {
            Result.failure(IllegalStateException("No block focused"))
        }
    }

    // Private helper methods

    private fun getSelectedText(): String? {
        val currentBlockId = _cursorState.value.blockId
        return if (currentBlockId != null) {
            textOperations.getTextState(currentBlockId).value.selection.let { selection ->
                selection?.let { textSelection ->
                    val textState = textOperations.getTextState(currentBlockId).value
                    if (textSelection.range.start >= 0 && textSelection.range.end <= textState.content.length) {
                        textState.content.substring(textSelection.range.start, textSelection.range.end)
                    } else null
                }
            }
        } else {
            null
        }
    }

    private suspend fun refreshBlocks() {
        val currentPage = _currentPage.value
        if (currentPage != null) {
            val blocks = blockRepository.getBlocksForPage(currentPage.uuid).first().getOrNull().orEmpty()
            _editorState.update { it.copy(blocks = blocks) }
        }
    }

    private suspend fun moveToNextBlockOrParent() {
        val currentBlockId = _cursorState.value.blockId ?: return
        
        // Try to get next sibling
        val nextSibling = blockOperations.getBlockSiblings(currentBlockId).first().getOrNull()
            ?.filter { sibling -> sibling.uuid != currentBlockId }
            ?.sortedBy { sibling -> sibling.position }
            ?.firstOrNull()
        
        if (nextSibling != null) {
            _cursorState.value = CursorState(
                blockId = nextSibling.uuid,
                position = 0
            )
        } else {
            // Move to parent
            val parent = blockOperations.getBlockParent(currentBlockId).first().getOrNull()
            if (parent != null) {
                _cursorState.value = CursorState(
                    blockId = parent.uuid,
                    position = 0
                )
            }
        }
    }
}
