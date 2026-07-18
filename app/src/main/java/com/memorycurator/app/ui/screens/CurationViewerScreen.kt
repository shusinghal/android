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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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

    // Two-stage swipe state
    val verticalOffset = remember { Animatable(0f) }
    var isLifted by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }

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
        verticalOffset.snapTo(0f)
        isLifted = false
        isDragging = false
        focusedClusterPhoto = null
    }

    // Anchor definitions
    val liftThreshold = -150f
    val liftAnchor = -350f
    val cancelThreshold = -100f // Threshold to drop back down
    val dismissThreshold = 250f

    // Background action color alpha based on drag
    val colorAlpha by animateFloatAsState(
        targetValue = if (verticalOffset.value < 0) {
            (kotlin.math.abs(verticalOffset.value) / 600f).coerceIn(0f, 0.6f)
        } else 0f,
        label = "colorAlpha"
    )
    
    // Lock action color during the confirmation animation to prevent flickering
    var lockedActionColor by remember { mutableStateOf<Color?>(null) }
    val actionColor = lockedActionColor ?: if (isBestTake) Color.Red else Color.Green

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // LAYER 1: Dynamic Gradient Background (bottom-up)
            if (verticalOffset.value < 0) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, actionColor.copy(alpha = colorAlpha)),
                                startY = 300f
                            )
                        )
                )
            }

            // LAYER 2: Text shown UNDER the photo
            if (verticalOffset.value < -50f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (isBestTake) "SWIPE UP AGAIN TO REMOVE" else "SWIPE UP AGAIN TO INCLUDE",
                            color = actionColor.copy(alpha = 0.9f),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = if (isLifted) "Confirmed on full swipe" else "Keep swiping to lift",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // LAYER 3: Main Pager
            HorizontalPager(
                state = mainPagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = focusedClusterPhoto == null && !isLifted && !isDragging,
                pageSpacing = 16.dp
            ) { page ->
                val isCurrentPage = page == mainPagerState.currentPage
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            if (isCurrentPage && focusedClusterPhoto == null) {
                                translationY = verticalOffset.value
                            }
                        }
                        .draggable(
                            orientation = Orientation.Vertical,
                            enabled = isCurrentPage && focusedClusterPhoto == null,
                            onDragStarted = { isDragging = true },
                            state = rememberDraggableState { delta ->
                                coroutineScope.launch {
                                    val target = verticalOffset.value + delta
                                    // Lock downward movement unless dismissing or resetting from lift
                                    if (target < 0 || (target >= 0 && !isLifted)) {
                                        verticalOffset.snapTo(target)
                                    }
                                }
                            },
                            onDragStopped = { velocity ->
                                isDragging = false
                                coroutineScope.launch {
                                    val currentVal = verticalOffset.value
                                    if (currentVal < liftThreshold) {
                                        if (!isLifted) {
                                            // Stage 1: Lift
                                            isLifted = true
                                            verticalOffset.animateTo(liftAnchor, spring())
                                        } else if (currentVal < (liftAnchor - 150f) || velocity < -500f) {
                                            // Stage 2: Confirm
                                            lockedActionColor = if (isBestTake) Color.Red else Color.Green
                                            isLifted = false
                                            verticalOffset.animateTo(0f, spring())
                                            activeItem?.let { onToggleAction(it.photo.id) }
                                            lockedActionColor = null
                                            if (results.size <= 1) onDismiss()
                                        } else {
                                            // Stay lifted
                                            verticalOffset.animateTo(liftAnchor, spring())
                                        }
                                    } else if (isLifted && (currentVal > cancelThreshold || velocity > 500f)) {
                                        // Swipe down to cancel lift
                                        isLifted = false
                                        verticalOffset.animateTo(0f, spring())
                                    } else if (!isLifted && currentVal > dismissThreshold) {
                                        // Swipe down from rest to dismiss
                                        onDismiss()
                                    } else {
                                        // Reset to anchor
                                        verticalOffset.animateTo(if (isLifted) liftAnchor else 0f, spring())
                                    }
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

            // Cluster Focus Overlay (Mirroring logic)
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
                                translationY = verticalOffset.value
                            }
                            .draggable(
                                orientation = Orientation.Vertical,
                                state = rememberDraggableState { delta ->
                                    coroutineScope.launch {
                                        isDragging = true
                                        val target = verticalOffset.value + delta
                                        if (target < 0 || (target >= 0 && !isLifted)) {
                                            verticalOffset.snapTo(target)
                                        }
                                    }
                                },
                                onDragStopped = { velocity ->
                                    isDragging = false
                                    coroutineScope.launch {
                                        val currentVal = verticalOffset.value
                                        if (currentVal < liftThreshold) {
                                            if (!isLifted) {
                                                isLifted = true
                                                verticalOffset.animateTo(liftAnchor, spring())
                                            } else {
                                                lockedActionColor = if (isBestTake) Color.Red else Color.Green
                                                isLifted = false
                                                verticalOffset.animateTo(0f, spring())
                                                onToggleAction(photo.photo.id)
                                                lockedActionColor = null
                                            }
                                        } else if (isLifted && (currentVal > cancelThreshold || velocity > 500f)) {
                                            isLifted = false
                                            verticalOffset.animateTo(0f, spring())
                                        } else if (!isLifted && currentVal > dismissThreshold) {
                                            focusedClusterPhoto = null
                                        } else {
                                            verticalOffset.animateTo(if (isLifted) liftAnchor else 0f, spring())
                                        }
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

            // LAYER 4: Bottom Carousel
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
                                    .clickable { 
                                        focusedClusterPhoto = member 
                                        coroutineScope.launch {
                                            verticalOffset.snapTo(0f)
                                            isLifted = false
                                        }
                                    }
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
