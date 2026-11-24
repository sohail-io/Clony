package com.clony.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val BlackAndWhiteColorScheme = darkColorScheme(
    primary = MonoWhite,
    onPrimary = MonoBlack,
    primaryContainer = MonoSurfaceContainerHighest,
    onPrimaryContainer = MonoWhite,
    inversePrimary = MonoSurfaceContainerHigh,
    secondary = MonoOnSurfaceVariant,
    onSecondary = MonoBlack,
    secondaryContainer = MonoSurfaceContainerHigh,
    onSecondaryContainer = MonoWhite,
    tertiary = MonoWhite,
    onTertiary = MonoBlack,
    tertiaryContainer = MonoSurfaceContainer,
    onTertiaryContainer = MonoWhite,
    background = MonoBlack,
    onBackground = MonoWhite,
    surface = MonoSurface,
    onSurface = MonoWhite,
    surfaceVariant = MonoSurfaceVariant,
    onSurfaceVariant = MonoOnSurfaceVariant,
    surfaceTint = MonoWhite,
    inverseSurface = MonoWhite,
    inverseOnSurface = MonoBlack,
    surfaceBright = MonoSurfaceContainerHighest,
    surfaceDim = MonoBlack,
    surfaceContainerLowest = MonoBlack,
    surfaceContainerLow = MonoSurfaceContainerLow,
    surfaceContainer = MonoSurfaceContainer,
    surfaceContainerHigh = MonoSurfaceContainerHigh,
    surfaceContainerHighest = MonoSurfaceContainerHighest,
    error = MonoWhite,
    onError = MonoBlack,
    errorContainer = MonoSurfaceContainerHighest,
    onErrorContainer = MonoWhite,
    outline = MonoOutline,
    outlineVariant = MonoOutlineVariant,
    scrim = MonoBlack
)

@Composable
fun ClonyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BlackAndWhiteColorScheme,
        typography = Typography,
        content = content
    )
}
