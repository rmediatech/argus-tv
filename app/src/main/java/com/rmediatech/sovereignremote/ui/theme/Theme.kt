package com.rmediatech.sovereignremote.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

val LocalThemePreset = staticCompositionLocalOf { ThemePreset.CYBER_TACTICAL }
val LocalThemePalette = staticCompositionLocalOf { ThemeRegistry.getPalette(ThemePreset.CYBER_TACTICAL) }
val LocalThemeIcons = staticCompositionLocalOf { ThemeRegistry.getIcons(ThemePreset.CYBER_TACTICAL) }
val LocalThemeLayout = staticCompositionLocalOf { ThemeRegistry.getLayout(ThemePreset.CYBER_TACTICAL) }

@Composable
fun SovereignRemoteTheme(
    preset: ThemePreset = ThemePreset.CYBER_TACTICAL,
    content: @Composable () -> Unit
) {
    val palette = ThemeRegistry.getPalette(preset)
    val icons = ThemeRegistry.getIcons(preset)
    val layout = ThemeRegistry.getLayout(preset)

    val colorScheme = darkColorScheme(
        primary = palette.primaryAccent,
        secondary = palette.surfaceElevated,
        tertiary = palette.powerColor,
        background = palette.background,
        surface = palette.surface,
        onPrimary = palette.background,
        onSecondary = palette.textPrimary,
        onTertiary = palette.textPrimary,
        onBackground = palette.textPrimary,
        onSurface = palette.textPrimary
    )

    CompositionLocalProvider(
        LocalThemePreset provides preset,
        LocalThemePalette provides palette,
        LocalThemeIcons provides icons,
        LocalThemeLayout provides layout
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
