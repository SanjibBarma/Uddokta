package com.uddoktahisab.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Emerald = Color(0xFF096B55);
val Gold = Color(0xFFE9A23B);
val Ink = Color(0xFF17201D);
val Mist = Color(0xFFF4F7F2)
private val Light = lightColorScheme(
    primary = Emerald,
    onPrimary = Color.White,
    secondary = Gold,
    background = Mist,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE7EEE9),
    outline = Color(0xFF74827C),
    error = Color(0xFFBA1A1A)
)
private val Dark = darkColorScheme(
    primary = Color(0xFF63D7B3),
    secondary = Color(0xFFFFBF69),
    background = Color(0xFF0F1513),
    surface = Color(0xFF17201D)
)

@Composable
fun UddoktaTheme(content: @Composable () ->Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = Typography(),
        content = content
    )
}
