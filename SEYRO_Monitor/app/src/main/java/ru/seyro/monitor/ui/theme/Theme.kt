package ru.seyro.monitor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SeyroColors = darkColorScheme(
    primary = Color(0xFF72E5C2),
    onPrimary = Color(0xFF06251E),
    secondary = Color(0xFF79BFFF),
    tertiary = Color(0xFFB6A0FF),
    background = Color(0xFF080D14),
    onBackground = Color(0xFFE8F0F5),
    surface = Color(0xFF101822),
    onSurface = Color(0xFFE8F0F5),
    surfaceVariant = Color(0xFF17232E),
    onSurfaceVariant = Color(0xFF9AABB8),
    outline = Color(0xFF2B3B47),
    error = Color(0xFFFF7D86)
)

@Composable
fun SeyroMonitorTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SeyroColors, content = content)
}
