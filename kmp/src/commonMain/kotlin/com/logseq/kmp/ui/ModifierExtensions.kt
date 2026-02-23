package com.logseq.kmp.ui

import androidx.compose.ui.Modifier

/**
 * Platform-specific pointer input handling for navigation (e.g., mouse back/forward buttons).
 */
expect fun Modifier.platformNavigationInput(
    onBack: () -> Unit,
    onForward: () -> Unit
): Modifier
