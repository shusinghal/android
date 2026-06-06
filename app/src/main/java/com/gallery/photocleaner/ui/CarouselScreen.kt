package com.gallery.photocleaner.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.gallery.photocleaner.model.PhotoItem
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CarouselScreen(
    viewModel: CleanerViewModel,
    onExitNavigation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val goodPhotos by viewModel.goodPhotos.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (isProcessing) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Analyzing & Filtering Photos...", color = Color.White)
            }
        } else if (goodPhotos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No clean items remaining.", color = Color.LightGray)
            }
        } else {
            val pagerState = rememberPagerState(pageCount = { goodPhotos.size })

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val photo = goodPhotos[page]
                var offsetX by remember { mutableStateOf(0f) }
                var offsetY by remember { mutableStateOf(0f) }
                val scale = remember { Animatable(1f) }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                        .scale(scale.value)
                        .pointerInput(photo.id) {
                            detectDragGestures(
                                onDragEnd = {
                                    val swipeThreshold = 300f
                                    if (abs(offsetX) > abs(offsetY)) {
                                        // Standard horizontal swiping is handled by HorizontalPager
                                        offsetX = 0f
                                        offsetY = 0f
                                    } else {
                                        if (offsetY > swipeThreshold) {
                                            // Swipe Down: Exit Flow
                                            onExitNavigation()
                                        } else if (offsetY < -swipeThreshold) {
                                            // Swipe Up: Remove Item
                                            coroutineScope.launch {
                                                scale.animateTo(0f, animationSpec = tween(250))
                                                viewModel.userRemovePhoto(photo)
                                            }
                                        } else {
                                            offsetX = 0f
                                            offsetY = 0f
                                        }
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    // Limit horizontal axis slightly to let Pager capture pagings
                                    offsetX += dragAmount.x * 0.2f
                                    offsetY += dragAmount.y
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = photo.uri,
                        contentDescription = "Evaluated Photo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                // Add vertical movement fading
                                alpha = (1f - (abs(offsetY) / 1000f)).coerceIn(0f, 1f)
                            }
                    )

                    // Score display badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(24.dp)
                            .background(Color.Black.copy(alpha = 0.6f), shape = MaterialTheme.shapes.small)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "Score: ${"%.2f".format(photo.score)}",
                            color = Color.Green,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}