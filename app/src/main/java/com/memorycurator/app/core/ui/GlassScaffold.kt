package com.memorycurator.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import com.memorycurator.app.ui.theme.AppTheme

@Composable
fun GlassScaffold(

    bottomBar: @Composable () -> Unit = {},

    content: @Composable (
        PaddingValues
    ) -> Unit
) {

    Box(

        modifier = Modifier

            .fillMaxSize()

            .background(

                brush = Brush.verticalGradient(

                    colors = listOf(

                        AppTheme
                            .Colors
                            .BackgroundTop,

                        AppTheme
                            .Colors
                            .BackgroundMiddle,

                        AppTheme
                            .Colors
                            .BackgroundBottom
                    )
                )
            )
    ) {

        Scaffold(

            containerColor =
                androidx.compose.ui.graphics.Color.Transparent,

            bottomBar = bottomBar
        ) { padding ->

            Box(
                modifier =
                    Modifier.padding(
                        padding
                    )
            ) {

                content(padding)
            }
        }
    }
}