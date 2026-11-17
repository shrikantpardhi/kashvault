package com.kashvault.ui.theme

const val THEME_LIGHT = "light"
const val THEME_DARK = "dark"
const val THEME_SYSTEM = "system"
const val THEME_DYNAMIC = "dynamic"

fun normalizeTheme(theme: String?): String = when (theme?.trim()?.lowercase()) {
    THEME_DARK -> THEME_DARK
    THEME_SYSTEM -> THEME_SYSTEM
    THEME_DYNAMIC, "material_you", "materialyou" -> THEME_DYNAMIC
    else -> THEME_LIGHT
}

fun isDarkThemeEnabled(theme: String?, systemDark: Boolean): Boolean = when (normalizeTheme(theme)) {
    THEME_DARK -> true
    THEME_SYSTEM, THEME_DYNAMIC -> systemDark
    else -> false
}

fun usesMaterialYou(theme: String?): Boolean = normalizeTheme(theme) == THEME_DYNAMIC

fun themeLabel(theme: String?): String = when (normalizeTheme(theme)) {
    THEME_DARK -> "Dark"
    THEME_SYSTEM -> "System"
    THEME_DYNAMIC -> "Material You"
    else -> "White"
}
