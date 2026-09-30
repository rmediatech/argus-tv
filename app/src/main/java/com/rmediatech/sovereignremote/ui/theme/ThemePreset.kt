package com.rmediatech.sovereignremote.ui.theme

enum class ThemePreset(
    val id: String,
    val displayName: String,
    val tagLine: String,
    val primaryAccentHex: Long,
    val previewBgHex: Long
) {
    CYBER_TACTICAL(
        id = "cyber_tactical",
        displayName = "Cyber Tactical",
        tagLine = "Neon HUD & Radar Telemetry",
        primaryAccentHex = 0xFF00E5FF, // Neon Cyan
        previewBgHex = 0xFF080C14
    ),
    NORDIC_MINIMALIST(
        id = "nordic_minimalist",
        displayName = "Nordic Minimalist",
        tagLine = "Scandi Studio & Fine Hi-Fi Glass",
        primaryAccentHex = 0xFFE2E8F0, // Platinum Frosted Silver
        previewBgHex = 0xFF14171F
    ),
    RETRO_CONSOLE(
        id = "retro_console",
        displayName = "Retro Console",
        tagLine = "Synthwave 90s Arcade Cross & A/B Buttons",
        primaryAccentHex = 0xFFFF007F, // Neon Magenta
        previewBgHex = 0xFF100824
    ),
    OBSIDIAN_STEALTH(
        id = "obsidian_stealth",
        displayName = "Obsidian Stealth",
        tagLine = "OLED Pure Black Ergonomic Columns",
        primaryAccentHex = 0xFF94A3B8, // Muted Titanium
        previewBgHex = 0xFF000000
    );

    companion object {
        fun fromId(id: String?): ThemePreset {
            return entries.find { it.id.equals(id, ignoreCase = true) } ?: CYBER_TACTICAL
        }
    }
}
