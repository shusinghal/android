package com.memorycurator.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.memorycurator.app.core.ai.CuratedResult
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CurationViewerScreen(
    results: List<CuratedResult>,
    allResults: List<CuratedResult>,
    initialIndex: Int,
    onToggleAction: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val mainPagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { results.size }
    )

    // Stage 1: Lift state
    val liftOffsetY = remember { Animatable(0f) }
    var isLifted by remember { mutableStateOf(false) }
    
    // Tracking current drag for visual feedback
    var currentDragY by remember { mutableFloatStateOf(0f) }

    val currentMainItem = if (results.isNotEmpty() && mainPagerState.currentPage < results.size) results[mainPagerState.currentPage] else null
    var focusedClusterPhoto by remember { mutableStateOf<CuratedResult?>(null) }
    
    val activeItem = focusedClusterPhoto ?: currentMainItem
    val isBestTake = activeItem?.isBestTake ?: false

    val clusterMembers = remember(currentMainItem, allResults) {
        if (currentMainItem?.clusterId != null) {
            allResults.filter { it.clusterId == currentMainItem.clusterId }
        } else {
            emptyList()
        }
    }

    // Reset state when page changes
    LaunchedEffect(mainPagerState.currentPage) {
        liftOffsetY.snapTo(0f)
        isLifted = false
        currentDragY = 0f
        focusedClusterPhoto = null
    }

    val overlayAlphaState = animateFloatAsState(
        targetValue = if (currentDragY < 0 && isLifted) {
            (-currentDragY / 400f).coerceIn(0f, 0.9f)
        } else 0f,
        label = "overlayAlpha"
    )
    val overlayAlpha = overlayAlphaState.value
    val overlayColor = if (isBestTake) Color.Red else Color.Green

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Text shown UNDER the photo when lifted
            if (isLifted || liftOffsetY.value < -50f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 150.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (isBestTake) "SWIPE AGAIN TO REMOVE" else "SWIPE AGAIN TO INCLUDE",
                            color = overlayColor.copy(alpha = 0.8f),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "Confirmed on full swipe",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Main Pager - This now owns the horizontal swipes
            HorizontalPager(
                state = mainPagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = focusedClusterPhoto == null && !isLifted,
                pageSpacing = 16.dp
            ) { page ->
                val isCurrentPage = page == mainPagerState.currentPage
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            if (isCurrentPage && focusedClusterPhoto == null) {
                                translationY = liftOffsetY.value + currentDragY.coerceAtMost(0f)
                            }
                        }
                        .draggable(
                            orientation = Orientation.Vertical,
                            enabled = isCurrentPage && focusedClusterPhoto == null,
                            onDragStarted = { },
                            state = rememberDraggableState { delta ->
                                currentDragY += delta
                            },
                            onDragStopped = { velocity ->
                                coroutineScope.launch {
                                    if (currentDragY < -150f) {
                                        if (!isLifted) {
                                            isLifted = true
                                            liftOffsetY.animateTo(-250f, spring())
                                        } else {
                                            activeItem?.let { onToggleAction(it.photo.id) }
                                            isLifted = false
                                            liftOffsetY.animateTo(0f)
                                            if (results.size <= 1) onDismiss()
                                        }
                                    } else if (currentDragY > 200f) {
                                        if (isLifted) {
                                            isLifted = false
                                            liftOffsetY.animateTo(0f)
                                        } else {
                                            onDismiss()
                                        }
                                    } else {
                                        // Snap back if didn't cross threshold
                                        if (!isLifted) {
                                            liftOffsetY.animateTo(0f)
                                        } else {
                                            liftOffsetY.animateTo(-250f)
                                        }
                                    }
                                    currentDragY = 0f
                                }
                            }
                        )
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { onDismiss() })
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(results[page].photo.contentUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Cluster Focus Overlay
            AnimatedVisibility(
                visible = focusedClusterPhoto != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                focusedClusterPhoto?.let { photo ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationY = liftOffsetY.value + currentDragY.coerceAtMost(0f)
                            }
                            .draggable(
                                orientation = Orientation.Vertical,
                                state = rememberDraggableState { delta ->
                                    currentDragY += delta
                                },
                                onDragStopped = {
                                    coroutineScope.launch {
                                        if (currentDragY < -150f) {
                                            if (!isLifted) {
                                                isLifted = true
                                                liftOffsetY.animateTo(-250f, spring())
                                            } else {
                                                onToggleAction(photo.photo.id)
                                                isLifted = false
                                                liftOffsetY.animateTo(0f)
                                            }
                                        } else if (currentDragY > 200f) {
                                            if (isLifted) {
                                                isLifted = false
                                                liftOffsetY.animateTo(0f)
                                            } else {
                                                focusedClusterPhoto = null
                                            }
                                        }
                                        currentDragY = 0f
                                    }
                                }
                            )
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = { focusedClusterPhoto = null })
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(photo.photo.contentUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // Action Confirmation Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(overlayColor.copy(alpha = overlayAlpha)),
                contentAlignment = Alignment.Center
            ) {
                if (overlayAlpha > 0.3f) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CheckCircle, null, tint = Color.White, modifier = Modifier.size(80.dp))
                        Text("ACTION CONFIRMED", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Bottom Carousel
            if (clusterMembers.isNotEmpty() && !isLifted) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 32.dp)
                        .navigationBarsPadding()
                ) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(clusterMembers) { member ->
                            val isSelected = member.photo.id == (activeItem?.photo?.id)
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(
                                        width = if (isSelected) 2.dp else 0.dp,
                                        color = if (isSelected) Color.White else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { focusedClusterPhoto = member }
                            ) {
                                AsyncImage(
                                    model = member.photo.contentUri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                if (member.isBestTake) {
                                    Box(
                                        modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(8.dp)
                                            .background(Color.White, CircleShape)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Top Progress
            if (!isLifted) {
                Text(
                    text = "${mainPagerState.currentPage + 1} / ${results.size}",
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 60.dp),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )
            }
        }
    }
}
