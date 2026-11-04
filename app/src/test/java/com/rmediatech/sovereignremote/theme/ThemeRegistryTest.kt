package com.rmediatech.sovereignremote.theme

import androidx.compose.ui.graphics.Color
import com.rmediatech.sovereignremote.ui.theme.ThemeLayoutType
import com.rmediatech.sovereignremote.ui.theme.ThemePreset
import com.rmediatech.sovereignremote.ui.theme.ThemeRegistry
import org.junit.Assert.*
import org.junit.Test

class ThemeRegistryTest {

    @Test
    fun testAllPresetsHaveCompleteConfiguration() {
        for (preset in ThemePreset.entries) {
            val palette = ThemeRegistry.getPalette(preset)
            val icons = ThemeRegistry.getIcons(preset)
            val layout = ThemeRegistry.getLayout(preset)

            assertNotNull("Palette for $preset must not be null", palette)
            assertNotNull("Icons for $preset must not be null", icons)
            assertNotNull("Layout for $preset must not be null", layout)
            assertTrue("Select label for $preset must not be blank", icons.selectLabel.isNotBlank())
        }
    }

    @Test
    fun testEveryThemeHasDistinctLayoutAndPersonality() {
        assertEquals(ThemeLayoutType.NATURAL_CIRCULAR, ThemeRegistry.getLayout(ThemePreset.SST_NATURAL))
        assertEquals(ThemeLayoutType.RADAR_CIRCULAR, ThemeRegistry.getLayout(ThemePreset.CYBER_TACTICAL))
        assertEquals(ThemeLayoutType.GEOMETRIC_PAD, ThemeRegistry.getLayout(ThemePreset.NORDIC_MINIMALIST))
        assertEquals(ThemeLayoutType.RETRO_CROSS, ThemeRegistry.getLayout(ThemePreset.RETRO_CONSOLE))
        assertEquals(ThemeLayoutType.STEALTH_COLUMNS, ThemeRegistry.getLayout(ThemePreset.OBSIDIAN_STEALTH))

        // Assert distinct action button labels
        assertEquals("OK", ThemeRegistry.getIcons(ThemePreset.SST_NATURAL).selectLabel)
        assertEquals("OK", ThemeRegistry.getIcons(ThemePreset.CYBER_TACTICAL).selectLabel)
        assertEquals("•", ThemeRegistry.getIcons(ThemePreset.NORDIC_MINIMALIST).selectLabel)
        assertEquals("A", ThemeRegistry.getIcons(ThemePreset.RETRO_CONSOLE).selectLabel)
        assertEquals("ENTER", ThemeRegistry.getIcons(ThemePreset.OBSIDIAN_STEALTH).selectLabel)
    }

    @Test
    fun testThemePresetFromIdResilience() {
        assertEquals(ThemePreset.SST_NATURAL, ThemePreset.fromId("sst_natural"))
        assertEquals(ThemePreset.CYBER_TACTICAL, ThemePreset.fromId("cyber_tactical"))
        assertEquals(ThemePreset.NORDIC_MINIMALIST, ThemePreset.fromId("NORDIC_MINIMALIST"))
        assertEquals(ThemePreset.RETRO_CONSOLE, ThemePreset.fromId("retro_console"))
        assertEquals(ThemePreset.OBSIDIAN_STEALTH, ThemePreset.fromId("obsidian_stealth"))

        // Adversarial inputs must fall back to default SST_NATURAL without throwing
        assertEquals(ThemePreset.SST_NATURAL, ThemePreset.fromId(null))
        assertEquals(ThemePreset.SST_NATURAL, ThemePreset.fromId(""))
        assertEquals(ThemePreset.SST_NATURAL, ThemePreset.fromId("invalid_non_existent_theme"))
        assertEquals(ThemePreset.SST_NATURAL, ThemePreset.fromId("; DROP TABLE themes;"))
    }

    @Test
    fun testThemeErgonomicsAndContrastStandards() {
        // Assert every preset provides distinct background and surface colors for glassmorphic depth
        for (preset in ThemePreset.entries) {
            val palette = ThemeRegistry.getPalette(preset)
            assertNotEquals("Surface must have depth relative to background in $preset", palette.background, palette.surface)
            assertNotEquals("Elevated surface must differ from base surface in $preset", palette.surface, palette.surfaceElevated)
            assertFalse("Accent must not match background in $preset", palette.primaryAccent == palette.background)
        }
    }
}
