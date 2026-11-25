package com.mcbane23.parentalcontrol.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The look shared by every screen: one fixed navy and blue palette, the same on every
 * phone, with a light and a dark version that follow the system setting.
 */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}

/** Colours the Material scheme has no slot for. */
object AppColors {
    /** The band at the top of the main screens. */
    val header: Color
        @Composable get() = if (isSystemInDarkTheme()) Color(0xFF131A36) else Color(0xFF1B2A6B)
    val onHeader: Color = Color.White
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F55F4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE4E9FF),
    onPrimaryContainer = Color(0xFF1B2A6B),
    secondary = Color(0xFF1B2A6B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE4E9FF),
    onSecondaryContainer = Color(0xFF1B2A6B),
    tertiary = Color(0xFF0B6B4A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD9F5EA),
    onTertiaryContainer = Color(0xFF0B4A34),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFE8CC),
    onErrorContainer = Color(0xFF7A3A00),
    background = Color(0xFFF5F6FA),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFF5F6FA),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFE3E6F0),
    onSurfaceVariant = Color(0xFF5D6680),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF0F2F8),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFE3E6F0),
    outline = Color(0xFF8A93AD),
    outlineVariant = Color(0xFFE1E4EE),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7F9BFF),
    onPrimary = Color(0xFF0A1440),
    primaryContainer = Color(0xFF222D5C),
    onPrimaryContainer = Color(0xFFCDD7FF),
    secondary = Color(0xFFCDD7FF),
    onSecondary = Color(0xFF131A36),
    secondaryContainer = Color(0xFF222D5C),
    onSecondaryContainer = Color(0xFFCDD7FF),
    tertiary = Color(0xFF6FE0B4),
    onTertiary = Color(0xFF00382A),
    tertiaryContainer = Color(0xFF103A2C),
    onTertiaryContainer = Color(0xFF6FE0B4),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF45300F),
    onErrorContainer = Color(0xFFFFC27A),
    background = Color(0xFF0B0E17),
    onBackground = Color(0xFFE8EBF5),
    surface = Color(0xFF0B0E17),
    onSurface = Color(0xFFE8EBF5),
    surfaceVariant = Color(0xFF262D42),
    onSurfaceVariant = Color(0xFF97A0BA),
    surfaceContainerLowest = Color(0xFF080A11),
    surfaceContainerLow = Color(0xFF141927),
    surfaceContainer = Color(0xFF181E2E),
    surfaceContainerHigh = Color(0xFF1C2336),
    surfaceContainerHighest = Color(0xFF262D42),
    outline = Color(0xFF5A6382),
    outlineVariant = Color(0xFF252C40),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

private val Base = Typography()

private val AppTypography = Typography(
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
)
