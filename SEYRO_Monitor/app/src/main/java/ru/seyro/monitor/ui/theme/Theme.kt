package ru.seyro.monitor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(primary = Color(0xFF83B8FF), secondary = Color(0xFFB9C7DD), surface = Color(0xFF10141C), background = Color(0xFF0B0F16))
private val LightColors = lightColorScheme(primary = Color(0xFF245E9F), secondary = Color(0xFF4F6179))

@Composable
fun SeyroMonitorTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
