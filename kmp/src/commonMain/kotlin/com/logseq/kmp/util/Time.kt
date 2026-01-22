package com.logseq.kmp.util

expect object PlatformTime {
    fun now(): Long
    fun currentThreadName(): String
}
