package com.memorycurator.app.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.DeleteForever
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
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { results.size }
    )

    var offsetY by remember { mutableFloatStateOf(0f) }
    var bottomBarOffsetY by remember { mutableFloatStateOf(0f) }
    var isBottomBarVisible by remember { mutableStateOf(true) }

    val currentItem = if (results.isNotEmpty() && pagerState.currentPage < results.size) results[pagerState.currentPage] else null
    val isBestTake = currentItem?.isBestTake ?: false
    
    // Find cluster members
    val clusterMembers = remember(currentItem, allResults) {
        if (currentItem?.clusterId != null) {
            allResults.filter { it.clusterId == currentItem.clusterId }
        } else {
            emptyList()
        }
    }

    val overlayAlpha by animateFloatAsState(
        targetValue = if (offsetY < 0) (-offsetY / 600f).coerceIn(0f, 0.8f) else 0f,
        label = "overlayAlpha"
    )

    val bottomBarAnimatedOffset by animateDpAsState(
        targetValue = if (isBottomBarVisible) 0.dp else 120.dp,
        label = "bottomBarOffset"
    )

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
            // Main Content Area
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(pagerState.currentPage) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                offsetY += dragAmount
                            },
                            onDragEnd = {
                                if (offsetY < -300f) {
                                    currentItem?.let { onToggleAction(it.photo.id) }
                                    if (results.size <= 1) onDismiss()
                                } else if (offsetY > 300f) {
                                    onDismiss()
                                }
                                offsetY = 0f
                            }
                        )
                    }
                    .graphicsLayer {
                        translationY = offsetY
                        alpha = (1f - (kotlin.math.abs(offsetY) / 1000f)).coerceIn(0.2f, 1f)
                    }
            ) { page ->
                Box(
                    modifier = Modifier.fillMaxSize()
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

            // Action Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(overlayColor.copy(alpha = overlayAlpha)),
                contentAlignment = Alignment.Center
            ) {
                if (overlayAlpha > 0.2f) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (isBestTake) Icons.Default.DeleteForever else Icons.Default.AddPhotoAlternate,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = if (isBestTake) "Removing from Best Takes" else "Including in Best Takes",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Cluster Carousel at bottom
            if (clusterMembers.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = bottomBarAnimatedOffset)
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(Color.Black.copy(alpha = 0.7f))
                        .draggable(
                            orientation = Orientation.Vertical,
                            state = rememberDraggableState { delta ->
                                bottomBarOffsetY += delta
                            },
                            onDragStopped = {
                                if (bottomBarOffsetY > 50f) {
                                    isBottomBarVisible = false
                                } else if (bottomBarOffsetY < -50f) {
                                    isBottomBarVisible = true
                                }
                                bottomBarOffsetY = 0f
                            }
                        )
                ) {
                    LazyRow(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(clusterMembers) { member ->
                            val isSelected = member.photo.id == currentItem?.photo?.id
                            Box(
                                modifier = Modifier
                                    .size(70.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(
                                        width = if (isSelected) 3.dp else 0.dp,
                                        color = if (isSelected) Color.White else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        // Find index in results list to scroll pager
                                        val index = results.indexOfFirst { it.photo.id == member.photo.id }
                                        if (index != -1) {
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(index)
                                            }
                                        }
                                    }
                            ) {
                                AsyncImage(
                                    model = member.photo.contentUri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }

            // Page Indicator
            Text(
                text = "${pagerState.currentPage + 1} / ${results.size}",
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 60.dp),
                color = Color.White,
                fontSize = 16.sp
            )
        }
    }
}
