package com.memorycurator.app.feature.viewer.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.memorycurator.app.data.media.MediaPhoto

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ViewerScreen(
    photos: List<MediaPhoto>,
    initialIndex: Int,
    onDismiss: () -> Unit
) {

    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { photos.size }
    )

    Dialog(
        onDismissRequest = {
            onDismiss()
        }
    ) {

        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            var scale by remember {
                mutableFloatStateOf(1f)
            }

            var isZoomed by remember {
            mutableStateOf(false)
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !isZoomed
            ) { page ->

                ZoomableImage(
                    imageUri = photos[page].contentUri,

                    onTap = {
                        onDismiss()
                    },

                    onZoomChanged = { zoomed ->
                        isZoomed = zoomed
                    }
                )
            }

            Text(
                text = "${pagerState.currentPage + 1} / ${photos.size}",

                modifier = Modifier.align(Alignment.TopCenter),

                fontSize = 18.sp
            )
        }
    }
}