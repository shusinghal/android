package com.memorycurator.app.feature.home.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeHeroSection() {

    Column(

        modifier = Modifier

            .fillMaxWidth()

            .padding(
                horizontal = 16.dp,
                vertical = 20.dp
            )

            .background(

                brush = Brush.verticalGradient(

                    colors = listOf(

                        Color.White.copy(alpha = 0.10f),

                        Color.White.copy(alpha = 0.03f)
                    )
                ),

                shape = RoundedCornerShape(32.dp)
            )

            .border(

                width = 1.dp,

                color = Color.White.copy(alpha = 0.08f),

                shape = RoundedCornerShape(32.dp)
            )

            .padding(24.dp),

        verticalArrangement =
            Arrangement.spacedBy(10.dp)
    ) {

        Text(

            text = "Memory Curator",

            color = Color.White,

            fontSize = 28.sp,

            fontWeight = FontWeight.Bold
        )

        Text(

            text =
                "Smart gallery for memories, cleanup and best shots",

            color =
                Color.White.copy(alpha = 0.70f),

            fontSize = 15.sp
        )

        Text(

            text = "12,482 Photos",

            color = Color.White,

            fontSize = 18.sp,

            fontWeight = FontWeight.SemiBold
        )
    }
}