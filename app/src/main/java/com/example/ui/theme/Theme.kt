package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = VibrantPrimary,
    secondary = VibrantSecondary,
    tertiary = VibrantTertiary,
    background = VibrantBgDark,
    surface = VibrantSurfaceDark,
    onPrimary = VibrantOnBgDark,
    onSecondary = VibrantOnBgDark,
    onTertiary = VibrantOnBgDark,
    onBackground = VibrantOnBgDark,
    onSurface = VibrantOnBgDark,
    surfaceVariant = VibrantSurfaceVariantDark,
    onSurfaceVariant = VibrantOnBgDark
  )

private val LightColorScheme =
  lightColorScheme(
    primary = VibrantPrimary,
    secondary = VibrantSecondary,
    tertiary = VibrantTertiary,
    background = VibrantBgLight,
    surface = VibrantSurfaceLight,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = VibrantOnBgLight,
    onSurface = VibrantOnBgLight,
    surfaceVariant = VibrantSurfaceVariantLight,
    onSurfaceVariant = VibrantOnBgLight
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Disable dynamic color by default to maintain Conecta5 unique look
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
