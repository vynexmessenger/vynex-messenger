package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VynexColorScheme =
  darkColorScheme(
    primary = VynexPrimary,
    onPrimary = VynexOnPrimary,
    background = VynexBackground,
    onBackground = VynexTextPrimary,
    surface = VynexSurface,
    onSurface = VynexTextPrimary,
    surfaceVariant = VynexSurface,
    onSurfaceVariant = VynexTextSecondary,
    error = VynexError
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = VynexColorScheme,
    typography = Typography,
    content = content
  )
}
