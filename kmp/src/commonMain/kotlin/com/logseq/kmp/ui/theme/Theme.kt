package com.logseq.kmp.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0066CC),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001B3D),
    secondary = Color(0xFF535F70),
    onSecondary = Color.White,
    background = Color(0xFFFDFCFF),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFDFCFF),
    onSurface = Color(0xFF1A1C1E)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFA0C9FF),
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF00497D),
    background = Color(0xFF1A1C1E),
    onBackground = Color(0xFFE2E2E6),
    surface = Color(0xFF1A1C1E),
    onSurface = Color(0xFFE2E2E6)
)

enum class LogseqThemeMode {
    LIGHT, DARK, SYSTEM
}

private var isDarkThemeSystem: Boolean = false

fun setSystemDarkTheme(dark: Boolean) {
    isDarkThemeSystem = dark
}

@Composable
fun LogseqTheme(
    themeMode: LogseqThemeMode = LogseqThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        LogseqThemeMode.LIGHT -> false
        LogseqThemeMode.DARK -> true
        LogseqThemeMode.SYSTEM -> isDarkThemeSystem
    }

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
