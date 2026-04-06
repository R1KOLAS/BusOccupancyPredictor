package com.example.busoccupancypredictor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = Color.White,
    surface = Color.White,
    onBackground = Color(0xFF111111),
    onSurface = Color(0xFF111111)
)

@Composable
fun BusOccupancyPredictorTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
      colorScheme = LightColorScheme,
      typography = Typography,
      content = content
    )
}
