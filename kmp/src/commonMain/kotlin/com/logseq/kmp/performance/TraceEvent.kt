package com.logseq.kmp.performance

data class TraceEvent(
    val name: String,
    val startTime: Long,
    val duration: Long,
    val type: String,
    val thread: String
)
