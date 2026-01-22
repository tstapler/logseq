package com.logseq.kmp.editor.commands

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class CommandSystemTest {
    
    private val testScope = CoroutineScope(Dispatchers.Default)
    
    @Test
    fun testCommandRegistration() = runTest {
        val registry = CommandRegistry()
        val commandSystem = CommandSystem(registry, testScope)
        
        val testCommand = EditorCommand(
            id = "test.command",
            type = CommandType.TEXT,
            label = "Test Command",
            description = "A test command",
            execute = { CommandResult.Success("Test executed") }
        )
        
        registry.register(testCommand)
        
        val retrievedCommand = registry.getCommand("test.command")
        assertNotNull(retrievedCommand)
        assertEquals("test.command", retrievedCommand?.id)
        assertEquals("Test Command", retrievedCommand?.label)
    }
    
    @Test
    fun testCommandExecution() = runTest {
        val registry = CommandRegistry()
        val commandSystem = CommandSystem(registry, testScope)
        
        var executionCount = 0
        val testCommand = EditorCommand(
            id = "test.execution",
            type = CommandType.TEXT,
            label = "Test Execution",
            execute = { 
                executionCount++
                CommandResult.Success("Executed $executionCount times") 
            }
        )
        
        registry.register(testCommand)
        
        val context = CommandContext(currentText = "test")
        val result = commandSystem.executeCommand("test.execution", context)
        
        assertEquals(1, executionCount)
        assertTrue(result is CommandResult.Success)
        assertEquals("Executed 1 times", (result as CommandResult.Success).message)
    }
    
    @Test
    fun testSlashCommandParsing() = runTest {
        val registry = CommandRegistry()
        val commandSystem = CommandSystem(registry, testScope)
        val slashHandler = SlashCommandHandler(commandSystem, testScope)
        
        // Test simple slash command
        val result1 = slashHandler.parse("/bold")
        assertTrue(result1 is SlashCommandParseResult.Success)
        assertEquals("bold", (result1 as SlashCommandParseResult.Success).command.command)
        
        // Test slash command with arguments
        val result2 = slashHandler.parse("/goto MyPage")
        assertTrue(result2 is SlashCommandParseResult.Success)
        val command2 = (result2 as SlashCommandParseResult.Success).command
        assertEquals("goto", command2.command)
        assertEquals(listOf("MyPage"), command2.arguments)
        
        // Test slash command with named arguments
        val result3 = slashHandler.parse("/search --query=test --type=page")
        assertTrue(result3 is SlashCommandParseResult.Success)
        val command3 = (result3 as SlashCommandParseResult.Success).command
        assertEquals("search", command3.command)
        assertEquals("test", command3.getNamedArgument("query"))
        assertEquals("page", command3.getNamedArgument("type"))
        
        // Test non-slash command
        val result4 = slashHandler.parse("not a slash command")
        assertTrue(result4 is SlashCommandParseResult.NotASlashCommand)
    }
    
    @Test
    fun testCommandAvailability() = runTest {
        val registry = CommandRegistry()
        val commandSystem = CommandSystem(registry, testScope)
        
        val requiresSelectionCommand = EditorCommand(
            id = "requires.selection",
            type = CommandType.TEXT,
            label = "Requires Selection",
            config = CommandConfig(requiresSelection = true),
            execute = { CommandResult.Success() }
        )
        
        val alwaysAvailableCommand = EditorCommand(
            id = "always.available",
            type = CommandType.TEXT,
            label = "Always Available",
            execute = { CommandResult.Success() }
        )
        
        registry.registerAll(listOf(requiresSelectionCommand, alwaysAvailableCommand))
        
        // Context without selection
        val contextWithoutSelection = CommandContext(
            currentText = "test",
            selectionStart = 0,
            selectionEnd = 0
        )
        val available1 = commandSystem.getAvailableCommands(contextWithoutSelection)
        assertEquals(1, available1.size)
        assertEquals("always.available", available1[0].id)
        
        // Context with selection
        val contextWithSelection = CommandContext(
            currentText = "test",
            selectionStart = 0,
            selectionEnd = 4
        )
        val available2 = commandSystem.getAvailableCommands(contextWithSelection)
        assertEquals(2, available2.size)
    }
    
    @Test
    fun testCommandSearch() = runTest {
        val registry = CommandRegistry()
        
        val boldCommand = EditorCommand(
            id = "text.bold",
            type = CommandType.TEXT,
            label = "Bold Text",
            description = "Format text as bold",
            execute = { CommandResult.Success() }
        )
        
        val italicCommand = EditorCommand(
            id = "text.italic",
            type = CommandType.TEXT,
            label = "Italic Text",
            description = "Format text as italic",
            execute = { CommandResult.Success() }
        )
        
        registry.registerAll(listOf(boldCommand, italicCommand))
        
        val context = CommandContext()
        
        // Search by exact match
        val exactResults = registry.searchCommands("Bold", context)
        assertEquals(1, exactResults.size)
        assertEquals("text.bold", exactResults[0].command.id)
        
        // Search by partial match
        val partialResults = registry.searchCommands("Text", context)
        assertEquals(2, partialResults.size)
        
        // Search by command ID
        val idResults = registry.searchCommands("text.italic", context)
        assertEquals(1, idResults.size)
        assertEquals("text.italic", idResults[0].command.id)
    }
    
    @Test
    fun testCommandHistory() = runTest {
        val registry = CommandRegistry()
        val commandSystem = CommandSystem(registry, testScope)
        
        val testCommand = EditorCommand(
            id = "history.test",
            type = CommandType.TEXT,
            label = "History Test",
            execute = { CommandResult.Success("Test executed") }
        )
        
        registry.register(testCommand)
        
        val context = CommandContext()
        
        // Execute command multiple times
        commandSystem.executeCommand("history.test", context)
        commandSystem.executeCommand("history.test", context)
        
        val history = commandSystem.getHistory().value
        assertEquals(2, history.size)
        assertEquals("history.test", history[0].command.id)
        assertEquals("history.test", history[1].command.id)
        
        // Clear history
        commandSystem.clearHistory()
        val clearedHistory = commandSystem.getHistory().value
        assertEquals(0, clearedHistory.size)
    }
}