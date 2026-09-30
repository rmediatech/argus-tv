package com.rmediatech.sovereignremote.theme

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
        assertEquals(ThemeLayoutType.RADAR_CIRCULAR, ThemeRegistry.getLayout(ThemePreset.CYBER_TACTICAL))
        assertEquals(ThemeLayoutType.GEOMETRIC_PAD, ThemeRegistry.getLayout(ThemePreset.NORDIC_MINIMALIST))
        assertEquals(ThemeLayoutType.RETRO_CROSS, ThemeRegistry.getLayout(ThemePreset.RETRO_CONSOLE))
        assertEquals(ThemeLayoutType.STEALTH_COLUMNS, ThemeRegistry.getLayout(ThemePreset.OBSIDIAN_STEALTH))

        // Assert distinct action button labels
        assertEquals("OK", ThemeRegistry.getIcons(ThemePreset.CYBER_TACTICAL).selectLabel)
        assertEquals("•", ThemeRegistry.getIcons(ThemePreset.NORDIC_MINIMALIST).selectLabel)
        assertEquals("A", ThemeRegistry.getIcons(ThemePreset.RETRO_CONSOLE).selectLabel)
        assertEquals("ENTER", ThemeRegistry.getIcons(ThemePreset.OBSIDIAN_STEALTH).selectLabel)
    }

    @Test
    fun testThemePresetFromIdResilience() {
        assertEquals(ThemePreset.CYBER_TACTICAL, ThemePreset.fromId("cyber_tactical"))
        assertEquals(ThemePreset.NORDIC_MINIMALIST, ThemePreset.fromId("NORDIC_MINIMALIST"))
        assertEquals(ThemePreset.RETRO_CONSOLE, ThemePreset.fromId("retro_console"))
        assertEquals(ThemePreset.OBSIDIAN_STEALTH, ThemePreset.fromId("obsidian_stealth"))

        // Adversarial inputs must fall back to default CYBER_TACTICAL without throwing
        assertEquals(ThemePreset.CYBER_TACTICAL, ThemePreset.fromId(null))
        assertEquals(ThemePreset.CYBER_TACTICAL, ThemePreset.fromId(""))
        assertEquals(ThemePreset.CYBER_TACTICAL, ThemePreset.fromId("invalid_non_existent_theme"))
        assertEquals(ThemePreset.CYBER_TACTICAL, ThemePreset.fromId("; DROP TABLE themes;"))
    }
}
