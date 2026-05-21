package com.memorycurator.app.feature.viewer.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun ZoomableImage(
    imageUri: Any,
    onTap: () -> Unit,
    onZoomChanged: (Boolean) -> Unit
) {

    var scale by remember {
        mutableFloatStateOf(1f)
    }

    var offsetX by remember {
        mutableFloatStateOf(0f)
    }

    var offsetY by remember {
        mutableFloatStateOf(0f)
    }

    LaunchedEffect(scale) {
        onZoomChanged(scale > 1f)
    }

    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(imageUri)
            .crossfade(true)
            .build(),

        contentDescription = null,

        contentScale = ContentScale.Fit,

        modifier = Modifier

            .pointerInput(Unit) {

                detectTapGestures(

                    onDoubleTap = {

                        if (scale > 1f) {

                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f

                        } else {

                            scale = 2.5f
                        }
                    },

                    onTap = {
                        onTap()
                    }
                )
            }

            .pointerInput(scale) {

                if (scale > 1f) {

                    detectTransformGestures { _, pan, zoom, _ ->

                        val newScale =
                            (scale * zoom).coerceIn(1f, 5f)

                        offsetX += pan.x
                        offsetY += pan.y

                        if (newScale == 1f) {

                            offsetX = 0f
                            offsetY = 0f
                        }

                        scale = newScale
                    }
                }
            }
            .graphicsLayer {

                scaleX = scale
                scaleY = scale

                translationX = offsetX
                translationY = offsetY
            }
    )
}