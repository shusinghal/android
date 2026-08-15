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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.memorycurator.app.core.ai.CuratedResult
import com.memorycurator.app.ui.preview.PreviewStockPhotos
import com.memorycurator.app.ui.components.VideoPlayer
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

import androidx.compose.ui.tooling.preview.Preview
import android.net.Uri
import com.memorycurator.app.data.media.MediaPhoto
import com.memorycurator.app.core.ai.RejectionReason
import com.memorycurator.app.ui.theme.MemoryCuratorTheme

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
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    val mainPagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { results.size }
    )

    // Device-independent gesture thresholds
    val liftThreshold = with(density) { -50.dp.toPx() }
    val liftAnchor = with(density) { -100.dp.toPx() }
    val confirmThreshold = with(density) { -150.dp.toPx() }
    val cancelThreshold = with(density) { -30.dp.toPx() }
    val dismissThreshold = with(density) { 150.dp.toPx() }

    // Swipe and Zoom States
    val verticalOffset = remember { Animatable(0f) }
    var isLifted by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var isZoomed by remember { mutableStateOf(false) }

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

    // Reset interaction states on card changes
    LaunchedEffect(mainPagerState.currentPage) {
        verticalOffset.snapTo(0f)
        isLifted = false
        isDragging = false
        focusedClusterPhoto = null
        isZoomed = false
    }

    // Dynamic background action overlay alpha
    val colorAlpha by animateFloatAsState(
        targetValue = if (verticalOffset.value < 0) {
            (verticalOffset.value.absoluteValue / with(density) { 300.dp.toPx() }).coerceIn(0f, 0.6f)
        } else 0f,
        label = "colorAlpha"
    )

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
            // LAYER 1: Action Glow Dynamic Gradient
            if (verticalOffset.value < 0) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, actionColor.copy(alpha = colorAlpha)),
                                startY = with(density) { 200.dp.toPx() }
                            )
                        )
                )
            }

            // LAYER 2: Text shown UNDER the active photo
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 110.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val isActive = verticalOffset.value < -20f || isLifted
                    
                    if (isActive) {
                        Text(
                            text = if (isBestTake) "SWIPE UP AGAIN TO REMOVE" else "SWIPE UP AGAIN TO INCLUDE",
                            color = actionColor.copy(alpha = 0.9f),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = if (isLifted) "Release at full swipe to confirm" else "Keep swiping up to stage",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 14.sp
                        )
                    } else {
                        Text(
                            text = if (isBestTake) "Swipe up to remove" else "Swipe up to include",
                            color = Color.White.copy(alpha = 0.3f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // LAYER 3: 3D Cover Flow Horizontal Pager
            HorizontalPager(
                state = mainPagerState,
                modifier = Modifier.fillMaxSize(),
                // Paging is locked if the image is zoomed, lifted, or active in gestural workflow
                userScrollEnabled = focusedClusterPhoto == null && !isLifted && !isDragging && !isZoomed,
                pageSpacing = (-8).dp, // Remove spacing as we use translation for overlap
                beyondViewportPageCount = 1, // Ensure left/right pages are rendered
                contentPadding = PaddingValues(horizontal = 16.dp) // Reduced padding to show more of side images
            ) { page ->
                val isCurrentPage = page == mainPagerState.currentPage

                // Calculate position relative to viewport center
                val pageOffset = ((mainPagerState.currentPage - page) + mainPagerState.currentPageOffsetFraction)
                val pageOffsetAbs = pageOffset.absoluteValue

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // Calculate 3D offsets
                            val rotationTarget = pageOffset * -28f
                            rotationY = rotationTarget.coerceIn(-45f, 45f)

                            val scale = 1f - (pageOffsetAbs * 0.15f).coerceIn(0f, 0.35f)
                            scaleX = scale
                            scaleY = scale

                            alpha = 1f - (pageOffsetAbs * 0.45f).coerceIn(0f, 0.75f)
                            translationX = pageOffset * -with(density) { 6.dp.toPx() }
                            translationY = (pageOffsetAbs * 40f) + if (isCurrentPage && focusedClusterPhoto == null) verticalOffset.value else 0f
                            cameraDistance = 12 * density.density

                            // ADDED: Clip to outline and render a physical elevation shadow inside the 3D space
                            shadowElevation = if (isCurrentPage) 16f else 4f
                            shape = RoundedCornerShape(16.dp)
                            clip = true
                        }
                        .zIndex(if (isCurrentPage) 1f else 0f)
                        .draggable(
                            orientation = Orientation.Vertical,
                            enabled = isCurrentPage && focusedClusterPhoto == null && !isZoomed,
                            onDragStarted = { isDragging = true },
                            state = rememberDraggableState { delta ->
                                coroutineScope.launch {
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
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            verticalOffset.animateTo(liftAnchor, spring())
                                        } else if (currentVal < confirmThreshold || velocity < -500f) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            lockedActionColor = if (isBestTake) Color.Red else Color.Green
                                            isLifted = false
                                            verticalOffset.animateTo(0f, spring())
                                            activeItem?.let { onToggleAction(it.photo.id) }
                                            lockedActionColor = null
                                        } else {
                                            verticalOffset.animateTo(liftAnchor, spring())
                                        }
                                    } else if (isLifted && (currentVal > cancelThreshold || velocity > 500f)) {
                                        isLifted = false
                                        verticalOffset.animateTo(0f, spring())
                                    } else if (!isLifted && currentVal > dismissThreshold) {
                                        onDismiss()
                                    } else {
                                        verticalOffset.animateTo(if (isLifted) liftAnchor else 0f, spring())
                                    }
                                }
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (results[page].photo.isVideo) {
                        VideoPlayer(
                            videoUri = results[page].photo.contentUri,
                            modifier = Modifier.fillMaxSize(),
                            active = isCurrentPage
                        )
                    } else {
                        ZoomableImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(results[page].photo.contentUri)
                                .setParameter("modified", results[page].photo.dateModified)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            onZoomStateChanged = { zoomed ->
                                isZoomed = zoomed
                            }
                        )
                    }
                }
            }

            // Focused Cluster Photo Overlay (Matches Zoom & Gestures)
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
                                if (verticalOffset.value < 0) {
                                    val progress = (verticalOffset.value.absoluteValue / liftAnchor.absoluteValue).coerceIn(0f, 1f)
                                    val scale = 1f - (progress * 0.08f)
                                    scaleX = scale
                                    scaleY = scale
                                }
                            }
                            .draggable(
                                orientation = Orientation.Vertical,
                                enabled = !isZoomed,
                                onDragStarted = { isDragging = true },
                                state = rememberDraggableState { delta ->
                                    coroutineScope.launch {
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
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                verticalOffset.animateTo(liftAnchor, spring())
                                            } else if (currentVal < confirmThreshold || velocity < -500f) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                lockedActionColor = if (photo.isBestTake) Color.Red else Color.Green
                                                isLifted = false
                                                verticalOffset.animateTo(0f, spring())
                                                onToggleAction(photo.photo.id)
                                                lockedActionColor = null
                                            } else {
                                                verticalOffset.animateTo(liftAnchor, spring())
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
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                    if (photo.photo.isVideo) {
                        VideoPlayer(
                            videoUri = photo.photo.contentUri,
                            modifier = Modifier.fillMaxSize(),
                            active = true
                        )
                    } else {
                        ZoomableImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(photo.photo.contentUri)
                                .setParameter("modified", photo.photo.dateModified)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            onZoomStateChanged = { zoomed ->
                                isZoomed = zoomed
                            }
                        )
                    }
                    }
                }
            }

            // LAYER 4: Bottom Carousel Thumbnail Selection
            if (clusterMembers.isNotEmpty() && !isLifted && !isZoomed) {
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
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(member.photo.contentUri)
                                        .setParameter("modified", member.photo.dateModified)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                    error = androidx.compose.ui.graphics.painter.ColorPainter(Color.DarkGray)
                                )
                                if (member.isBestTake) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(2.dp)
                                            .size(8.dp)
                                            .background(Color.White, CircleShape)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Context Indicators (Visible only during neutral exploration states)
            if (!isLifted && !isZoomed) {
                Text(
                    text = "${mainPagerState.currentPage + 1} / ${results.size}",
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 60.dp),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 48.dp, start = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

/**
 * Custom Zoomable container with bounds clamping, pinch-to-zoom, pan, 
 * and double-tap magnification behaviors.
 */
@Composable
fun ZoomableImage(
    model: ImageRequest,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    onZoomStateChanged: (isZoomed: Boolean) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Smoothly transition between zoom scaling and translations
    val scaleAnim = remember { Animatable(1f) }
    val offsetXAnim = remember { Animatable(0f) }
    val offsetYAnim = remember { Animatable(0f) }

    LaunchedEffect(scaleAnim.value) {
        scale = scaleAnim.value
        onZoomStateChanged(scale > 1.05f)
    }
    LaunchedEffect(offsetXAnim.value, offsetYAnim.value) {
        offset = Offset(offsetXAnim.value, offsetYAnim.value)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { tapCenter ->
                        coroutineScope.launch {
                            if (scaleAnim.value > 1.1f) {
                                // Reset back to default size
                                launch { scaleAnim.animateTo(1f) }
                                launch { offsetXAnim.animateTo(0f) }
                                launch { offsetYAnim.animateTo(0f) }
                            } else {
                                // Zoom in 3x
                                val targetScale = 3f
                                launch { scaleAnim.animateTo(targetScale) }

                                val width = size.width.toFloat()
                                val height = size.height.toFloat()

                                // Calculate max panning allowances for target scale
                                val maxOffsetX = (width * (targetScale - 1f)) / 2f
                                val maxOffsetY = (height * (targetScale - 1f)) / 2f

                                // Interpolate offset target to center around the point tapped
                                val targetX = ((width / 2f - tapCenter.x) * (targetScale - 1f)).coerceIn(-maxOffsetX, maxOffsetX)
                                val targetY = ((height / 2f - tapCenter.y) * (targetScale - 1f)).coerceIn(-maxOffsetY, maxOffsetY)

                                launch { offsetXAnim.animateTo(targetX) }
                                launch { offsetYAnim.animateTo(targetY) }
                            }
                        }
                    }
                )
            }
            .pointerInput(scale) {
                // IMPORTANT: Only detect transform gestures if zoomed OR starting to zoom
                // This allows the HorizontalPager to see the swipe gestures when scale is 1.0
                if (scale > 1f) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        coroutineScope.launch {
                            val currentScale = scaleAnim.value
                            val newScale = (currentScale * zoom).coerceIn(1f, 4f)
                            scaleAnim.snapTo(newScale)

                            if (newScale > 1f) {
                                val width = size.width.toFloat()
                                val height = size.height.toFloat()

                                val maxOffsetX = (width * (newScale - 1f)) / 2f
                                val maxOffsetY = (height * (newScale - 1f)) / 2f

                                val newOffsetX = (offsetXAnim.value + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                                val newOffsetY = (offsetYAnim.value + pan.y).coerceIn(-maxOffsetY, maxOffsetY)

                                offsetXAnim.snapTo(newOffsetX)
                                offsetYAnim.snapTo(newOffsetY)
                            } else {
                                offsetXAnim.snapTo(0f)
                                offsetYAnim.snapTo(0f)
                            }
                        }
                    }
                }
            }
    ) {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )
    }
}

@Preview
@Composable
fun CurationViewerScreenPreview() {
    val mockPhotos = PreviewStockPhotos.getPhotos(5)
    val mockResults = mockPhotos.mapIndexed { index, photo ->
        CuratedResult(
            photo = photo,
            score = 0.8f + (index * 0.05f),
            isBestTake = index % 2 == 0,
            clusterId = if (index < 3) "cluster_1" else null
        )
    }

    MemoryCuratorTheme {
        CurationViewerScreen(
            results = mockResults,
            allResults = mockResults,
            initialIndex = 0,
            onToggleAction = {},
            onDismiss = {}
        )
    }
}
