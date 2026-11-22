package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val EmberfallDarkColorScheme = darkColorScheme(
  primary = MoltenEmber,
  onPrimary = ObsidianVoid,
  primaryContainer = DeepEmber,
  onPrimaryContainer = WarmParchment,
  secondary = GildedBrass,
  onSecondary = ObsidianVoid,
  secondaryContainer = SlateStone,
  onSecondaryContainer = GildedBrass,
  tertiary = HueTide,
  onTertiary = WarmParchment,
  background = ObsidianVoid,
  onBackground = WarmParchment,
  surface = CharredBasalt,
  onSurface = WarmParchment,
  surfaceVariant = SlateStone,
  onSurfaceVariant = MutedAsh,
  outline = SmokedBorder,
  error = CrimsonRuin,
  onError = WarmParchment,
)

val EmberfallShapes = Shapes(
  small = RoundedCornerShape(8.dp),
  medium = RoundedCornerShape(14.dp),
  large = RoundedCornerShape(20.dp),
)

@Composable
fun EmberfallTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = EmberfallDarkColorScheme,
    typography = EmberfallTypography,
    shapes = EmberfallShapes,
    content = content,
  )
}
