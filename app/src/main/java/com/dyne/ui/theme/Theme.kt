package com.dyne.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val scheme = darkColorScheme(
    primary = Color(0xFF7DB8FF), onPrimary = Color.Black,
    background = Color.Black, surface = Color(0xFF0E0E10), surfaceVariant = Color(0xFF1B1B1F),
    onBackground = Color.White, onSurface = Color.White, onSurfaceVariant = Color(0xFFB0B0B8),
    secondaryContainer = Color(0xFF26262C), onSecondaryContainer = Color.White
)

@Composable
fun DyneTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme, content = content)
