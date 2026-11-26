package com.studentpay.manager.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Restrained indigo palette (the legacy brand colour). Every foreground/background pair used for text
 * was checked for >= 4.5:1 (borders/icons >= 3:1) in both themes. Dynamic colour is intentionally off:
 * Paid (green) and Due (red) must keep their meaning whatever the wallpaper is.
 */
private val LightScheme = lightColorScheme(
    primary = Color(0xFF3F51B5), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0E3FF), onPrimaryContainer = Color(0xFF0B1560),
    inversePrimary = Color(0xFFB8C3FF),
    secondary = Color(0xFF5B5D72), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0E1F9), onSecondaryContainer = Color(0xFF181A2C),
    tertiary = Color(0xFF76546E), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD7F2), onTertiaryContainer = Color(0xFF2D1228),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFBF8FF), onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFBF8FF), onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE3E1EC), onSurfaceVariant = Color(0xFF46464F),
    outline = Color(0xFF777680), outlineVariant = Color(0xFFC7C5D0),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF303036), inverseOnSurface = Color(0xFFF2EFF7),
    surfaceTint = Color(0xFF3F51B5),
    surfaceDim = Color(0xFFDBD9E0), surfaceBright = Color(0xFFFBF8FF),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF5F2FA),
    surfaceContainer = Color(0xFFEFEDF4), surfaceContainerHigh = Color(0xFFE9E7EF),
    surfaceContainerHighest = Color(0xFFE4E1E9),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFB8C3FF), onPrimary = Color(0xFF0F1E80),
    primaryContainer = Color(0xFF2B3C9E), onPrimaryContainer = Color(0xFFE0E3FF),
    inversePrimary = Color(0xFF3F51B5),
    secondary = Color(0xFFC3C5DD), onSecondary = Color(0xFF2C2F42),
    secondaryContainer = Color(0xFF434559), onSecondaryContainer = Color(0xFFE0E1F9),
    tertiary = Color(0xFFE5BAD8), onTertiary = Color(0xFF44263E),
    tertiaryContainer = Color(0xFF5C3C55), onTertiaryContainer = Color(0xFFFFD7F2),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121318), onBackground = Color(0xFFE4E1E9),
    surface = Color(0xFF121318), onSurface = Color(0xFFE4E1E9),
    surfaceVariant = Color(0xFF46464F), onSurfaceVariant = Color(0xFFC7C5D0),
    outline = Color(0xFF918F9A), outlineVariant = Color(0xFF46464F),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFE4E1E9), inverseOnSurface = Color(0xFF303036),
    surfaceTint = Color(0xFFB8C3FF),
    surfaceDim = Color(0xFF121318), surfaceBright = Color(0xFF38393F),
    surfaceContainerLowest = Color(0xFF0D0E13), surfaceContainerLow = Color(0xFF1B1B21),
    surfaceContainer = Color(0xFF1F1F25), surfaceContainerHigh = Color(0xFF292A2F),
    surfaceContainerHighest = Color(0xFF34343A),
)

/** Semantic "paid" colours. Due uses the Material error roles. */
data class StatusColors(
    val paid: Color,
    val onPaid: Color,
    val paidContainer: Color,
    val onPaidContainer: Color,
)

private val LightStatus = StatusColors(Color(0xFF1B6E3C), Color(0xFFFFFFFF), Color(0xFFCDEBD6), Color(0xFF00210E))
private val DarkStatus = StatusColors(Color(0xFF7FD99B), Color(0xFF00391A), Color(0xFF0A5128), Color(0xFFCDEBD6))

val LocalStatusColors = staticCompositionLocalOf { LightStatus }

object StatusTheme {
    val colors: StatusColors
        @Composable get() = LocalStatusColors.current
}

@Composable
fun StudentPayTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStatusColors provides if (darkTheme) DarkStatus else LightStatus) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            content = content,
        )
    }
}
