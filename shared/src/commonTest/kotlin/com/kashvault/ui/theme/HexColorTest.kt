package com.kashvault.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HexColorTest {
    @Test
    fun parsesSixDigitHex() {
        assertEquals(Color(0xFF7C3AED), hexToColor("#7C3AED"))
        assertEquals(Color(0xFF51CF66), hexToColor("51CF66"))
    }

    @Test
    fun fallsBackWhenBlank() {
        assertEquals(PurpleDeep, hexToColor(""))
        assertEquals(PurpleDeep, hexToColor("xyz"))
    }

    @Test
    fun whiteThemeIsCleanCanvasWithFixedPurple() {
        assertEquals(Color(0xFFF8F9FC), WhiteCanvas)
        assertEquals(Color(0xFFFFFFFF), WhiteCard)
        assertEquals(Color(0xFF6D28D9), PurpleDeep)
    }

    @Test
    fun darkInputOutlineIsVisibleAgainstSurface() {
        assertTrue(relativeLuminance(DarkFieldOutline) > relativeLuminance(DarkSurface) + 0.08f)
        assertTrue(relativeLuminance(DarkFieldOutlineVariant) > relativeLuminance(DarkSurface) + 0.04f)
    }
}

private fun relativeLuminance(color: Color): Float {
    fun channel(value: Float): Float =
        if (value <= 0.04045f) value / 12.92f else ((value + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
    return 0.2126f * channel(color.red) + 0.7152f * channel(color.green) + 0.0722f * channel(color.blue)
}
