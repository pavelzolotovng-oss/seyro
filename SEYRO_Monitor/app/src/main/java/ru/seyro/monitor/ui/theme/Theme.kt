package ru.seyro.monitor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

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

private val BaseTypography = Typography()
private val SeyroTypography = Typography(
    headlineSmall = BaseTypography.headlineSmall.copy(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.35).sp
    ),
    titleLarge = BaseTypography.titleLarge.copy(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp
    ),
    titleMedium = BaseTypography.titleMedium.copy(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium
    ),
    bodyLarge = BaseTypography.bodyLarge.copy(fontFamily = FontFamily.SansSerif, lineHeight = 25.sp),
    bodyMedium = BaseTypography.bodyMedium.copy(fontFamily = FontFamily.SansSerif, lineHeight = 22.sp),
    labelMedium = BaseTypography.labelMedium.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium),
    labelSmall = BaseTypography.labelSmall.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, letterSpacing = 0.25.sp)
)

@Composable
fun SeyroMonitorTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SeyroColors, typography = SeyroTypography, content = content)
}
