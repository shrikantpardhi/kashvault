package com.kashvault.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val PurpleDeep = Color(0xFF6D28D9)
val PurpleMid = Color(0xFF6D28D9)
val PurpleNight = Color(0xFF3B0764)
val PurpleBright = Color(0xFFC4B5FD)
val PurpleSoft = Color(0xFFF5F3FF)
val Ink = Color(0xFF1C1917)
val Paper = Color(0xFFF8F9FC)
val Cream = Color(0xFFFFFFFF)
val WhiteCanvas = Color(0xFFF8F9FC)
val WhiteCard = Color(0xFFFFFFFF)
val DarkSurface = Color(0xFF1C1326)
val DarkFieldOutline = Color(0xFFC4B5E8)
val DarkFieldOutlineVariant = Color(0xFF9B86C4)
val Coral = Color(0xFFDC2626)
val Sage = Color(0xFF16A34A)
val Mist = Color(0xFFEEF0F5)

// Neo-Fintech Vibrant Banking Palette
val NeoCyan = Color(0xFF00F2FE)
val NeoCyanGlow = Color(0xFF4FACFE)
val NeoEmerald = Color(0xFF10B981)
val NeoEmeraldGlow = Color(0xFF34D399)
val NeoIndigo = Color(0xFF6366F1)
val NeoViolet = Color(0xFF8B5CF6)
val NeoElectric = Color(0xFF00E5FF)
val NeoDarkBg = Color(0xFF0B0E14)
val NeoCardBg = Color(0xFF141923)
val NeoCardBorder = Color(0xFF1E293B)
val NeoAmber = Color(0xFFF59E0B)

val NeoFintechMeshGradient = Brush.linearGradient(
    listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364))
)

val NeoEmeraldCardGradient = Brush.linearGradient(
    listOf(Color(0xFF064E3B), Color(0xFF065F46), Color(0xFF047857))
)

val NeoCyanCardGradient = Brush.linearGradient(
    listOf(Color(0xFF083344), Color(0xFF0E7490), Color(0xFF0284C7))
)

val NeoIndigoCardGradient = Brush.linearGradient(
    listOf(Color(0xFF1E1B4B), Color(0xFF312E81), Color(0xFF4338CA))
)

val NeoVoucherSealGradient = Brush.radialGradient(
    listOf(Color(0xFF10B981), Color(0xFF047857), Color(0xFF064E3B))
)

data class VaultPalette(
    val income: Color,
    val incomeContainer: Color,
    val spend: Color,
    val spendContainer: Color,
    val leftover: Color,
    val leftoverContainer: Color,
    val wealth: Color,
    val wealthContainer: Color,
    val lending: Color,
    val lendingContainer: Color,
    val due: Color,
    val dueContainer: Color,
    val inbox: Color,
    val inboxContainer: Color,
    val bank: Color,
    val bankContainer: Color,
    val goal: Color,
    val goalContainer: Color,
    val ai: Color,
    val aiContainer: Color,
)

val LightVaultPalette = VaultPalette(
    income = Color(0xFF16A34A),
    incomeContainer = Color(0xFFF0FDF4),
    spend = Color(0xFFDC2626),
    spendContainer = Color(0xFFFEF2F2),
    leftover = PurpleDeep,
    leftoverContainer = PurpleSoft,
    wealth = PurpleDeep,
    wealthContainer = Color.White,
    lending = PurpleDeep,
    lendingContainer = Color.White,
    due = Color(0xFFDC2626),
    dueContainer = Color.White,
    inbox = PurpleDeep,
    inboxContainer = Color.White,
    bank = PurpleDeep,
    bankContainer = Color.White,
    goal = PurpleDeep,
    goalContainer = Color.White,
    ai = PurpleDeep,
    aiContainer = Color.White,
)

val DarkVaultPalette = VaultPalette(
    income = Color(0xFF4ADE80),
    incomeContainer = Color(0xFF14532D),
    spend = Color(0xFFF87171),
    spendContainer = Color(0xFF7F1D1D),
    leftover = PurpleBright,
    leftoverContainer = Color(0xFF4C1D95),
    wealth = PurpleBright,
    wealthContainer = DarkSurface,
    lending = PurpleBright,
    lendingContainer = DarkSurface,
    due = Color(0xFFF87171),
    dueContainer = DarkSurface,
    inbox = PurpleBright,
    inboxContainer = DarkSurface,
    bank = PurpleBright,
    bankContainer = DarkSurface,
    goal = PurpleBright,
    goalContainer = DarkSurface,
    ai = PurpleBright,
    aiContainer = DarkSurface,
)

val LocalVaultPalette = staticCompositionLocalOf { LightVaultPalette }

@Composable
fun vaultPalette(): VaultPalette = LocalVaultPalette.current

fun hexToColor(hex: String, fallback: Color = PurpleDeep): Color {
    val clean = hex.trim().removePrefix("#")
    val value = clean.toLongOrNull(16) ?: return fallback
    return when (clean.length) {
        6 -> Color(0xFF000000L or value)
        8 -> Color(value)
        else -> fallback
    }
}

val VaultShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

val VaultTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.SemiBold, lineHeight = 40.sp),
    headlineLarge = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.SemiBold, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp),
    labelMedium = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 14.sp),
    labelSmall = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium, lineHeight = 14.sp),
)

private val LightColors = lightColorScheme(
    primary = PurpleDeep,
    onPrimary = Color.White,
    primaryContainer = PurpleSoft,
    onPrimaryContainer = PurpleNight,
    secondary = PurpleDeep,
    onSecondary = Color.White,
    secondaryContainer = PurpleSoft,
    onSecondaryContainer = PurpleNight,
    tertiary = PurpleDeep,
    onTertiary = Color.White,
    tertiaryContainer = PurpleSoft,
    onTertiaryContainer = PurpleNight,
    background = WhiteCanvas,
    surface = WhiteCard,
    surfaceVariant = Mist,
    onBackground = Ink,
    onSurface = Ink,
    onSurfaceVariant = Color(0xFF6B7280),
    outline = Color(0xFFE2E5EE),
    outlineVariant = Color(0xFFEEF0F5),
    error = Coral,
    errorContainer = Color(0xFFFEF2F2),
    onErrorContainer = Color(0xFF991B1B),
)

private val MaterialYouLightFallback = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF625B71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    tertiary = Color(0xFF7D5260),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD8E4),
    onTertiaryContainer = Color(0xFF31111D),
    background = Color(0xFFECE6F0),
    surface = Color(0xFFFFF7FF),
    surfaceVariant = Color(0xFFE7E0EC),
    onBackground = Color(0xFF1D1B20),
    onSurface = Color(0xFF1D1B20),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
    error = Coral,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val MaterialYouDarkFallback = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E4),
    background = Color(0xFF141218),
    surface = Color(0xFF1D1B20),
    surfaceVariant = Color(0xFF49454F),
    onBackground = Color(0xFFE6E0E9),
    onSurface = Color(0xFFE6E0E9),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
    error = Color(0xFFF2B8B5),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)

private val DarkColors = darkColorScheme(
    primary = PurpleBright,
    onPrimary = PurpleNight,
    primaryContainer = Color(0xFF5B21B6),
    onPrimaryContainer = PurpleSoft,
    secondary = PurpleBright,
    onSecondary = PurpleNight,
    secondaryContainer = Color(0xFF4C1D95),
    onSecondaryContainer = PurpleSoft,
    tertiary = PurpleBright,
    onTertiary = PurpleNight,
    tertiaryContainer = Color(0xFF4C1D95),
    onTertiaryContainer = PurpleSoft,
    background = Color(0xFF120818),
    surface = DarkSurface,
    surfaceVariant = Color(0xFF2A1B3D),
    onBackground = Color(0xFFF5F3FF),
    onSurface = Color(0xFFF5F3FF),
    onSurfaceVariant = Color(0xFFD1D5DB),
    outline = DarkFieldOutline,
    outlineVariant = DarkFieldOutlineVariant,
    error = Color(0xFFFCA5A5),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2),
)

@Composable
fun KashVaultTheme(
    darkTheme: Boolean = false,
    materialYou: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dynamic = if (materialYou) platformDynamicColorScheme(darkTheme) else null
    val scheme = when {
        materialYou -> dynamic ?: if (darkTheme) MaterialYouDarkFallback else MaterialYouLightFallback
        darkTheme -> DarkColors
        else -> LightColors
    }
    val palette = when {
        materialYou -> vaultPaletteForMaterialYou(scheme, darkTheme)
        darkTheme -> DarkVaultPalette
        else -> LightVaultPalette
    }
    CompositionLocalProvider(LocalVaultPalette provides palette) {
        MaterialTheme(
            colorScheme = scheme,
            typography = VaultTypography,
            shapes = VaultShapes,
            content = content,
        )
    }
}

private fun vaultPaletteForMaterialYou(scheme: ColorScheme, darkTheme: Boolean): VaultPalette {
    val base = if (darkTheme) DarkVaultPalette else LightVaultPalette
    return base.copy(
        leftover = scheme.primary,
        leftoverContainer = scheme.primaryContainer,
        wealth = scheme.primary,
        wealthContainer = scheme.surface,
        lending = scheme.secondary,
        lendingContainer = scheme.secondaryContainer,
        inbox = scheme.primary,
        inboxContainer = scheme.surface,
        bank = scheme.primary,
        bankContainer = scheme.surface,
        goal = scheme.tertiary,
        goalContainer = scheme.tertiaryContainer,
        ai = scheme.tertiary,
        aiContainer = scheme.tertiaryContainer,
    )
}
