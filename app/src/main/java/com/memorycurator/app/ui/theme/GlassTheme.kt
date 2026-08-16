package com.memorycurator.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GlassColors = darkColorScheme(

    primary = Color.White,

    background = Color.Black,

    surface = Color(0x66000000),

    onSurface = Color.White,

    onBackground = Color.White
)

@Composable
fun GlassTheme(
    content: @Composable () -> Unit
) {

    MaterialTheme(

        colorScheme = GlassColors,

        typography = Typography,

        shapes = Shapes,

        content = content
    )
}