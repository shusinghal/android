package com.memorycurator.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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

@Preview(showBackground = true)
@Composable
fun GlassScaffoldPreview() {
    GlassScaffold(
        bottomBar = {
            Box(
                modifier = Modifier
                    .background(AppTheme.Colors.GlassPrimary)
                    .padding(16.dp)
            ) {
                Text(
                    text = "Bottom Bar",
                    color = AppTheme.Colors.TextPrimary
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Content Area",
                color = AppTheme.Colors.TextPrimary
            )
        }
    }
}
