package com.kashvault.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThemePreferenceTest {
    @Test
    fun normalizesKnownValues() {
        assertEquals(THEME_DARK, normalizeTheme("dark"))
        assertEquals(THEME_SYSTEM, normalizeTheme("System"))
        assertEquals(THEME_DYNAMIC, normalizeTheme("material_you"))
        assertEquals(THEME_LIGHT, normalizeTheme(null))
        assertEquals(THEME_LIGHT, normalizeTheme("light"))
    }

    @Test
    fun systemFollowsPhone() {
        assertTrue(isDarkThemeEnabled(THEME_DARK, systemDark = false))
        assertFalse(isDarkThemeEnabled(THEME_LIGHT, systemDark = true))
        assertTrue(isDarkThemeEnabled(THEME_SYSTEM, systemDark = true))
        assertFalse(isDarkThemeEnabled(THEME_SYSTEM, systemDark = false))
        assertTrue(isDarkThemeEnabled(THEME_DYNAMIC, systemDark = true))
        assertTrue(usesMaterialYou(THEME_DYNAMIC))
        assertFalse(usesMaterialYou(THEME_LIGHT))
        assertFalse(usesMaterialYou(THEME_SYSTEM))
        assertEquals("White", themeLabel(THEME_LIGHT))
        assertEquals("Material You", themeLabel(THEME_DYNAMIC))
    }
}
