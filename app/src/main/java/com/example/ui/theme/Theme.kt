package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class BrokerPulseExtendedColors(
    val bullish: Color,
    val onBullish: Color,
    val bullishContainer: Color,
    val bearish: Color,
    val onBearish: Color,
    val bearishContainer: Color,
    val amber: Color,
    val onAmber: Color,
    val amberContainer: Color,
    val cyanAccent: Color,
    val cardBorder: Color,
    val isDark: Boolean
)

val LocalBrokerPulseColors = staticCompositionLocalOf {
    BrokerPulseExtendedColors(
        bullish = Emerald400,
        onBullish = Obsidian950,
        bullishContainer = EmeraldDarkContainer,
        bearish = Crimson400,
        onBearish = PureWhite,
        bearishContainer = CrimsonDarkContainer,
        amber = Amber400,
        onAmber = Obsidian950,
        amberContainer = AmberDarkContainer,
        cyanAccent = Cyan400,
        cardBorder = Slate700,
        isDark = true
    )
}

private val DarkColorScheme = darkColorScheme(
    primary = Emerald400,
    onPrimary = Obsidian950,
    primaryContainer = EmeraldDarkContainer,
    onPrimaryContainer = EmeraldLightContainer,
    secondary = Cyan400,
    onSecondary = Obsidian950,
    secondaryContainer = Slate800,
    onSecondaryContainer = Slate100,
    tertiary = Amber400,
    onTertiary = Obsidian950,
    tertiaryContainer = AmberDarkContainer,
    onTertiaryContainer = AmberLightContainer,
    background = Obsidian950,
    onBackground = Slate100,
    surface = Obsidian900,
    onSurface = Slate100,
    surfaceVariant = Slate850,
    onSurfaceVariant = Slate400,
    surfaceContainerLowest = Obsidian950,
    surfaceContainerLow = Obsidian900,
    surfaceContainer = Slate850,
    surfaceContainerHigh = Slate800,
    surfaceContainerHighest = Slate700,
    outline = Slate700,
    outlineVariant = Color(0xFF1E293B),
    error = Crimson400,
    onError = PureWhite,
    errorContainer = CrimsonDarkContainer,
    onErrorContainer = CrimsonLightContainer
)

private val LightColorScheme = lightColorScheme(
    primary = Emerald700,
    onPrimary = PureWhite,
    primaryContainer = EmeraldLightContainer,
    onPrimaryContainer = Color(0xFF022C22),
    secondary = Indigo500,
    onSecondary = PureWhite,
    secondaryContainer = Color(0xFFE0E7FF),
    onSecondaryContainer = Color(0xFF1E1B4B),
    tertiary = Amber700,
    onTertiary = PureWhite,
    tertiaryContainer = AmberLightContainer,
    onTertiaryContainer = Color(0xFF451A03),
    background = Ivory50,
    onBackground = Ink900,
    surface = PureWhite,
    onSurface = Ink900,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Ink600,
    surfaceContainerLowest = PureWhite,
    surfaceContainerLow = Ivory50,
    surfaceContainer = PureWhite,
    surfaceContainerHigh = Color(0xFFF1F5F9),
    surfaceContainerHighest = Color(0xFFE2E8F0),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    error = Crimson700,
    onError = PureWhite,
    errorContainer = CrimsonLightContainer,
    onErrorContainer = Color(0xFF450A0A)
)

private val BrokerPulseShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun BrokerPulseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) {
        BrokerPulseExtendedColors(
            bullish = Emerald400,
            onBullish = Obsidian950,
            bullishContainer = EmeraldDarkContainer,
            bearish = Crimson400,
            onBearish = PureWhite,
            bearishContainer = CrimsonDarkContainer,
            amber = Amber400,
            onAmber = Obsidian950,
            amberContainer = AmberDarkContainer,
            cyanAccent = Cyan400,
            cardBorder = Color(0xFF233252),
            isDark = true
        )
    } else {
        BrokerPulseExtendedColors(
            bullish = Emerald700,
            onBullish = PureWhite,
            bullishContainer = EmeraldLightContainer,
            bearish = Crimson700,
            onBearish = PureWhite,
            bearishContainer = CrimsonLightContainer,
            amber = Amber700,
            onAmber = PureWhite,
            amberContainer = AmberLightContainer,
            cyanAccent = Cyan500,
            cardBorder = Color(0xFFE2E8F0),
            isDark = false
        )
    }

    CompositionLocalProvider(LocalBrokerPulseColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = BrokerPulseTypography,
            shapes = BrokerPulseShapes,
            content = content
        )
    }
}

object BrokerPulseTheme {
    val extendedColors: BrokerPulseExtendedColors
        @Composable
        get() = LocalBrokerPulseColors.current
}
