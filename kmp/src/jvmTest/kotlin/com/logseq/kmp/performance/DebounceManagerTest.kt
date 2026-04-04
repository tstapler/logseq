package com.logseq.kmp.performance

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceTimeBy
import kotlin.test.*
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class DebounceManagerTest {
    
    @Test
    fun testFlushAllExecutesPending() = runTest {
        val manager = DebounceManager(this, 1000L)
        val counter = AtomicInteger(0)
        
        manager.debounce("test") {
            counter.incrementAndGet()
        }
        
        // Before delay, counter should be 0
        // We need to advance time slightly to let the outer launch run
        advanceTimeBy(1)
        assertEquals(0, counter.get())
        
        // Flush should execute it immediately
        val flushedCount = manager.flushAll()
        assertEquals(1, flushedCount)
        assertEquals(1, counter.get())
    }

    @Test
    fun testDebounceDelay() = runTest {
        val manager = DebounceManager(this, 100L)
        val counter = AtomicInteger(0)
        
        manager.debounce("test") { counter.incrementAndGet() }
        
        advanceTimeBy(50)
        assertEquals(0, counter.get(), "Should not execute before delay")
        
        advanceTimeBy(60)
        assertEquals(1, counter.get(), "Should execute after delay")
    }

    @Test
    fun testDebounceOverridesPrevious() = runTest {
        val manager = DebounceManager(this, 100L)
        val counter = AtomicInteger(0)
        
        manager.debounce("test") { counter.addAndGet(1) }
        advanceTimeBy(50)
        manager.debounce("test") { counter.addAndGet(10) }
        
        advanceTimeBy(110)
        assertEquals(10, counter.get(), "Only the latest action should have executed")
    }

    @Test
    fun testFlushAllClearsPending() = runTest {
        val manager = DebounceManager(this, 100L)
        val counter = AtomicInteger(0)
        
        manager.debounce("test") { counter.incrementAndGet() }
        advanceTimeBy(1)
        
        val flushedCount = manager.flushAll()
        assertEquals(1, flushedCount)
        assertEquals(1, counter.get())
        
        // Subsequent delay should not execute anything
        advanceTimeBy(200L)
        assertEquals(1, counter.get(), "Should not execute again after flush")
    }
}
