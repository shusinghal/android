package com.memorycurator.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {

    Box(

        modifier = modifier

            .clip(
                RoundedCornerShape(28.dp)
            )

            .background(
                Color(0x22FFFFFF)
            )

            .border(
                width = 1.dp,
                color = Color(0x44FFFFFF),
                shape = RoundedCornerShape(28.dp)
            )

            .padding(16.dp)
    ) {

        content()
    }
}