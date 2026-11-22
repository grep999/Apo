package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

// Copperplate Engraving Serif (Cormorant Garamond) + Industrial Studio Sans (IBM Plex Sans) + Tabular Kiln Numerals (IBM Plex Mono)
val CormorantFamily = FontFamily(Font(R.font.cormorant_garamond, FontWeight.Normal))
val IbmPlexSansFamily = FontFamily(Font(R.font.ibm_plex_sans, FontWeight.Normal))
val IbmPlexMonoFamily = FontFamily(Font(R.font.ibm_plex_mono, FontWeight.Normal))

// Backward-compatible aliases so existing callers seamlessly adopt the new Copperplate typography
val CinzelFamily = CormorantFamily
val InterFamily = IbmPlexSansFamily
val JetBrainsMonoFamily = IbmPlexMonoFamily

val EmberfallTypography = Typography(
  displayLarge = TextStyle(
    fontFamily = CormorantFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 38.sp,
    lineHeight = 42.sp,
    letterSpacing = 1.2.sp,
  ),
  headlineMedium = TextStyle(
    fontFamily = CormorantFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 26.sp,
    lineHeight = 30.sp,
    letterSpacing = 0.6.sp,
  ),
  titleLarge = TextStyle(
    fontFamily = CormorantFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 21.sp,
    lineHeight = 26.sp,
    letterSpacing = 0.4.sp,
  ),
  titleMedium = TextStyle(
    fontFamily = IbmPlexSansFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 15.sp,
    lineHeight = 20.sp,
  ),
  bodyLarge = TextStyle(
    fontFamily = IbmPlexSansFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 15.sp,
    lineHeight = 21.sp,
  ),
  bodyMedium = TextStyle(
    fontFamily = IbmPlexSansFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
    lineHeight = 18.sp,
  ),
  labelLarge = TextStyle(
    fontFamily = IbmPlexSansFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 14.sp,
    lineHeight = 18.sp,
    letterSpacing = 0.4.sp,
  ),
  labelMedium = TextStyle(
    fontFamily = IbmPlexMonoFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 16.sp,
  ),
  labelSmall = TextStyle(
    fontFamily = IbmPlexMonoFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 11.sp,
    lineHeight = 14.sp,
  ),
)
