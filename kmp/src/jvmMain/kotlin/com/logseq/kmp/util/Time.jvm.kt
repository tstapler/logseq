package com.logseq.kmp.util

actual object PlatformTime {
    actual fun now(): Long = System.nanoTime()
    actual fun currentThreadName(): String = Thread.currentThread().name
}
