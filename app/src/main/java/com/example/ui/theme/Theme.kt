package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val LightColors = lightColorScheme(
    primary = EmeraldPrimaryLight,
    onPrimary = OnEmeraldLight,
    primaryContainer = EmeraldContainerLight,
    onPrimaryContainer = OnEmeraldContainerLight,
    secondary = AmberSecondaryLight,
    onSecondary = OnAmberLight,
    secondaryContainer = AmberContainerLight,
    onSecondaryContainer = OnAmberContainerLight,
    tertiary = ClayTertiaryLight,
    tertiaryContainer = ClayContainerLight,
    background = CreamBackgroundLight,
    onBackground = InkTextLight,
    surface = CreamSurfaceLight,
    onSurface = InkTextLight,
    surfaceVariant = WarmCardLight,
    onSurfaceVariant = MutedInkLight,
    surfaceContainerLow = WarmCardLight,
    surfaceContainerHigh = WarmSurfaceHighestLight,
    outline = OutlineLight,
)

private val DarkColors = darkColorScheme(
    primary = EmeraldPrimaryDark,
    onPrimary = OnEmeraldDark,
    primaryContainer = EmeraldContainerDark,
    onPrimaryContainer = OnEmeraldContainerDark,
    secondary = AmberSecondaryDark,
    onSecondary = OnAmberDark,
    secondaryContainer = AmberContainerDark,
    onSecondaryContainer = OnAmberContainerDark,
    tertiary = ClayTertiaryDark,
    tertiaryContainer = ClayContainerDark,
    background = PastureBackgroundDark,
    onBackground = CreamTextDark,
    surface = PastureSurfaceDark,
    onSurface = CreamTextDark,
    surfaceVariant = PastureCardDark,
    onSurfaceVariant = MutedTextDark,
    surfaceContainerLow = PastureCardDark,
    surfaceContainerHigh = PastureSurfaceHighestDark,
    outline = OutlineDark,
)

@Composable
fun FlockLedgerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    isSindhiRtl: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    val layoutDirection = if (isSindhiRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = buildAppTypography(isSindhi = isSindhiRtl),
            content = content,
        )
    }
}
