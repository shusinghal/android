package com.memorycurator.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.memorycurator.app.R
import com.memorycurator.app.ui.core.glassBlur

@Composable
fun BlurBackground() {

    Box {

        Image(
            painter = painterResource(
                id = R.drawable.bg_main
            ),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .glassBlur()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(

                        colors = listOf(

                            Color(0x88000000),

                            Color(0xCC000000)
                        )
                    )
                )
        )
    }
}