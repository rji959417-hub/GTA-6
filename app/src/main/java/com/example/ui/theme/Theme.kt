package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ViceColorScheme =
  darkColorScheme(
    primary = NeonPink,
    onPrimary = ViceVoid,
    primaryContainer = SunsetCoral,
    onPrimaryContainer = ViceVoid,
    secondary = ElectricCyan,
    onSecondary = ViceVoid,
    secondaryContainer = ViceSurfaceElevated,
    onSecondaryContainer = TextPrimary,
    tertiary = GoldenHour,
    onTertiary = ViceVoid,
    background = ViceVoid,
    onBackground = TextPrimary,
    surface = ViceSurfaceSolid,
    onSurface = TextPrimary,
    surfaceVariant = ViceSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = OutlineGlow,
  )

@Composable
fun ViceHorizonTheme(content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = ViceColorScheme, typography = Typography, content = content)
}
