package com.rmediatech.sovereignremote.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.Color

object ThemeRegistry {

    fun getPalette(preset: ThemePreset): ThemePalette {
        return when (preset) {
            ThemePreset.CYBER_TACTICAL -> ThemePalette(
                background = Color(0xFF080C14),
                surface = Color(0xFF111726),
                surfaceElevated = Color(0xFF1B2338),
                border = Color(0xFF28354D),
                primaryAccent = Color(0xFF00E5FF),     // Neon Cyan
                secondaryAccent = Color(0xFFFF9900),   // Amazon Flame Amber
                powerColor = Color(0xFFFF1744),        // Tactical Crimson
                textPrimary = Color(0xFFF0F6FC),
                textSecondary = Color(0xFF8B949E),
                textMuted = Color(0xFF484F58)
            )

            ThemePreset.NORDIC_MINIMALIST -> ThemePalette(
                background = Color(0xFF12141A),        // Scandi Slate
                surface = Color(0xFF1C202B),
                surfaceElevated = Color(0xFF262C3B),
                border = Color(0xFF384155),
                primaryAccent = Color(0xFFE2E8F0),     // Frosted Platinum
                secondaryAccent = Color(0xFF94A3B8),   // Cool Titanium
                powerColor = Color(0xFFFB7185),        // Soft Rose
                textPrimary = Color(0xFFF8FAFC),
                textSecondary = Color(0xFF94A3B8),
                textMuted = Color(0xFF64748B)
            )

            ThemePreset.RETRO_CONSOLE -> ThemePalette(
                background = Color(0xFF0F0721),        // Midnight Synthwave Purple
                surface = Color(0xFF1E0E40),
                surfaceElevated = Color(0xFF2E1763),
                border = Color(0xFF4E26A4),
                primaryAccent = Color(0xFFFF007F),     // Hot Arcade Magenta
                secondaryAccent = Color(0xFF00F5D4),   // Electric Vaporwave Teal
                powerColor = Color(0xFFFF0055),        // Retro Neon Red
                textPrimary = Color(0xFFFAF5FF),
                textSecondary = Color(0xFFD8B4FE),
                textMuted = Color(0xFF9333EA)
            )

            ThemePreset.OBSIDIAN_STEALTH -> ThemePalette(
                background = Color(0xFF000000),        // 100% OLED Pure Pitch Black
                surface = Color(0xFF0D0D0D),
                surfaceElevated = Color(0xFF171717),
                border = Color(0xFF262626),
                primaryAccent = Color(0xFFE4E4E7),     // High-contrast Pure Silver
                secondaryAccent = Color(0xFF71717A),   // Graphite Steel
                powerColor = Color(0xFFEF4444),        // Warning Red
                textPrimary = Color(0xFFFAFAFA),
                textSecondary = Color(0xFFA1A1AA),
                textMuted = Color(0xFF52525B)
            )
        }
    }

    fun getIcons(preset: ThemePreset): ThemeIcons {
        return when (preset) {
            ThemePreset.CYBER_TACTICAL -> ThemeIcons(
                power = Icons.Rounded.PowerSettingsNew,
                back = Icons.AutoMirrored.Rounded.ArrowBack,
                home = Icons.Rounded.Home,
                options = Icons.Rounded.Tune,
                up = Icons.Rounded.KeyboardArrowUp,
                down = Icons.Rounded.KeyboardArrowDown,
                left = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                right = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                selectLabel = "OK",
                playPause = Icons.Rounded.PlayArrow,
                rewind = Icons.Rounded.FastRewind,
                fastForward = Icons.Rounded.FastForward,
                volUp = Icons.AutoMirrored.Rounded.VolumeUp,
                volDown = Icons.AutoMirrored.Rounded.VolumeDown,
                mute = Icons.AutoMirrored.Rounded.VolumeMute,
                themeSwitcher = Icons.Rounded.Palette
            )

            ThemePreset.NORDIC_MINIMALIST -> ThemeIcons(
                power = Icons.Rounded.RadioButtonUnchecked, // Sleek hairline circle
                back = Icons.Rounded.ChevronLeft,
                home = Icons.Rounded.GridView,
                options = Icons.Rounded.MoreHoriz,
                up = Icons.Rounded.North,
                down = Icons.Rounded.South,
                left = Icons.Rounded.West,
                right = Icons.Rounded.East,
                selectLabel = "•",                         // Minimal dot
                playPause = Icons.Rounded.PlayCircle,
                rewind = Icons.Rounded.Replay10,
                fastForward = Icons.Rounded.Forward10,
                volUp = Icons.Rounded.Add,
                volDown = Icons.Rounded.Remove,
                mute = Icons.AutoMirrored.Rounded.VolumeOff,
                themeSwitcher = Icons.Rounded.Contrast
            )

            ThemePreset.RETRO_CONSOLE -> ThemeIcons(
                power = Icons.Rounded.Power,
                back = Icons.AutoMirrored.Rounded.Undo,
                home = Icons.Rounded.SportsEsports,
                options = Icons.Rounded.Games,
                up = Icons.Rounded.ArrowDropUp,
                down = Icons.Rounded.ArrowDropDown,
                left = Icons.AutoMirrored.Rounded.ArrowBack,
                right = Icons.AutoMirrored.Rounded.ArrowForward,
                selectLabel = "A",                         // Retro Arcade Action A
                playPause = Icons.Rounded.ChangeHistory,   // Geometric retro play
                rewind = Icons.Rounded.FastRewind,
                fastForward = Icons.Rounded.FastForward,
                volUp = Icons.AutoMirrored.Rounded.VolumeUp,
                volDown = Icons.AutoMirrored.Rounded.VolumeDown,
                mute = Icons.AutoMirrored.Rounded.VolumeMute,
                themeSwitcher = Icons.Rounded.ColorLens
            )

            ThemePreset.OBSIDIAN_STEALTH -> ThemeIcons(
                power = Icons.Rounded.PowerSettingsNew,
                back = Icons.Rounded.Close,
                home = Icons.Rounded.Menu,
                options = Icons.Rounded.Settings,
                up = Icons.Rounded.KeyboardArrowUp,
                down = Icons.Rounded.KeyboardArrowDown,
                left = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                right = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                selectLabel = "ENTER",
                playPause = Icons.Rounded.PlayArrow,
                rewind = Icons.Rounded.FastRewind,
                fastForward = Icons.Rounded.FastForward,
                volUp = Icons.AutoMirrored.Rounded.VolumeUp,
                volDown = Icons.AutoMirrored.Rounded.VolumeDown,
                mute = Icons.AutoMirrored.Rounded.VolumeMute,
                themeSwitcher = Icons.Rounded.DarkMode
            )
        }
    }

    fun getLayout(preset: ThemePreset): ThemeLayoutType {
        return when (preset) {
            ThemePreset.CYBER_TACTICAL -> ThemeLayoutType.RADAR_CIRCULAR
            ThemePreset.NORDIC_MINIMALIST -> ThemeLayoutType.GEOMETRIC_PAD
            ThemePreset.RETRO_CONSOLE -> ThemeLayoutType.RETRO_CROSS
            ThemePreset.OBSIDIAN_STEALTH -> ThemeLayoutType.STEALTH_COLUMNS
        }
    }
}
