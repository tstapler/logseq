package com.logseq.kmp.performance

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.test.*
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class DebounceManagerTest {
    
    @Test
    fun testFlushAllExecutesPending() = runTest(UnconfinedTestDispatcher()) {
        val manager = DebounceManager(this, 1000L)
        val counter = AtomicInteger(0)
        
        manager.debounce("test") {
            counter.incrementAndGet()
        }
        
        // Before delay, counter should be 0
        assertEquals(0, counter.get())
        
        // Flush should execute it immediately
        val flushedCount = manager.flushAll()
        assertEquals(1, flushedCount)
        assertEquals(1, counter.get())
    }

    @Test
    fun testDebounceOverridesPrevious() = runTest(UnconfinedTestDispatcher()) {
        val manager = DebounceManager(this, 100L)
        val counter = AtomicInteger(0)
        
        manager.debounce("test") { counter.addAndGet(1) }
        manager.debounce("test") { counter.addAndGet(10) }
        
        // Since we are using UnconfinedTestDispatcher, the outer launch runs immediately.
        // We still need to wait for the inner delay if we want to test automatic execution,
        // but here we are testing overrides.
        
        val flushedCount = manager.flushAll()
        assertEquals(1, flushedCount)
        assertEquals(10, counter.get(), "Only the latest action should have executed")
    }

    @Test
    fun testFlushAllClearsPending() = runTest(UnconfinedTestDispatcher()) {
        val manager = DebounceManager(this, 100L)
        val counter = AtomicInteger(0)
        
        manager.debounce("test") { counter.incrementAndGet() }
        
        manager.flushAll()
        assertEquals(1, counter.get())
        
        // Subsequent delay should not execute anything
        // In runTest with Unconfined, we don't even need to wait, but it's safe.
        assertEquals(1, counter.get())
    }
}
