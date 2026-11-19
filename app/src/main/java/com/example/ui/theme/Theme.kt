package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val JarvisColorScheme =
  darkColorScheme(
    primary = JarvisCyan,
    onPrimary = JarvisBgDeep,
    primaryContainer = JarvisSurfaceElevated,
    onPrimaryContainer = JarvisCyanGlow,
    secondary = JarvisAmber,
    onSecondary = JarvisBgDeep,
    secondaryContainer = JarvisSurface,
    onSecondaryContainer = JarvisAmber,
    tertiary = JarvisEmerald,
    onTertiary = JarvisBgDeep,
    background = JarvisBg,
    onBackground = JarvisText,
    surface = JarvisSurface,
    onSurface = JarvisText,
    surfaceVariant = JarvisSurfaceElevated,
    onSurfaceVariant = JarvisTextDim,
    outline = JarvisBorder,
    error = JarvisDanger,
    onError = JarvisText,
  )

@Composable
fun MyApplicationTheme(content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = JarvisColorScheme, typography = Typography, content = content)
}
