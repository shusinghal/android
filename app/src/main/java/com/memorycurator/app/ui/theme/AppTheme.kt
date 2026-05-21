package com.memorycurator.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object AppTheme {

    object Colors {

        val BackgroundTop =
            Color(0xFF0F1115)

        val BackgroundMiddle =
            Color(0xFF161A22)

        val BackgroundBottom =
            Color(0xFF10131A)

        val GlassPrimary =
            Color.White.copy(alpha = 0.10f)

        val GlassSecondary =
            Color.White.copy(alpha = 0.03f)

        val GlassBorder =
            Color.White.copy(alpha = 0.08f)

        val TextPrimary =
            Color.White

        val TextSecondary =
            Color.White.copy(alpha = 0.65f)
    }

    object Radius {

        val Large = 30.dp

        val Medium = 22.dp

        val Small = 16.dp
    }

    object Spacing {

        val Screen = 16.dp

        val Grid = 12.dp

        val Card = 14.dp
    }
}