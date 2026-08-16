package com.memorycurator.app.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
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
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.memorycurator.app.core.ai.CuratedResult
import com.memorycurator.app.ui.components.VideoPlayer
import com.memorycurator.app.ui.preview.PreviewStockPhotos
import com.memorycurator.app.ui.theme.MemoryCuratorTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/**
 * State holder encapsulating the swipe-up to stage/confirm workflow.
 */
@Stable
class CurationSwipeState(
    val density: Density,
    val coroutineScope: CoroutineScope,
    val haptic: HapticFeedback,
    val onConfirmAction: () -> Unit,
    val onDismiss: () -> Unit
) {
    val verticalOffset = Animatable(0f)
    var isLifted by mutableStateOf(false)
        private set
    var isDragging by mutableStateOf(false)
        private set

    val liftThreshold = with(density) { -50.dp.toPx() }
    val liftAnchor = with(density) { -100.dp.toPx() }
    val confirmThreshold = with(density) { -150.dp.toPx() }
    val cancelThreshold = with(density) { -30.dp.toPx() }
    val dismissThreshold = with(density) { 150.dp.toPx() }

    fun onDrag(delta: Float) {
        coroutineScope.launch {
            val target = verticalOffset.value + delta
            if (target < 0 || (target >= 0 && !isLifted)) {
                verticalOffset.snapTo(target)
            }
        }
    }

    fun onDragStarted() {
        isDragging = true
    }

    fun onDragStopped(velocity: Float) {
        isDragging = false
        coroutineScope.launch {
            val currentVal = verticalOffset.value
            if (currentVal < liftThreshold) {
                if (!isLifted) {
                    isLifted = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    verticalOffset.animateTo(liftAnchor, spring(stiffness = Spring.StiffnessMediumLow))
                } else if (currentVal < confirmThreshold || velocity < -500f) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    isLifted = false
                    verticalOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                    onConfirmAction()
                } else {
                    verticalOffset.animateTo(liftAnchor, spring(stiffness = Spring.StiffnessMediumLow))
                }
            } else if (isLifted && (currentVal > cancelThreshold || velocity > 500f)) {
                isLifted = false
                verticalOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
            } else if (!isLifted && currentVal > dismissThreshold) {
                onDismiss()
            } else {
                verticalOffset.animateTo(if (isLifted) liftAnchor else 0f, spring(stiffness = Spring.StiffnessMediumLow))
            }
        }
    }

    fun reset() {
        coroutineScope.launch {
            verticalOffset.snapTo(0f)
            isLifted = false
            isDragging = false
        }
    }
}

@Composable
fun CurationViewerScreen(
    results: List<CuratedResult>,
    allResults: List<CuratedResult>,
    initialIndex: Int,
    onToggleAction: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    if (results.isEmpty()) return

    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    val mainPagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, (results.size - 1).coerceAtLeast(0)),
        pageCount = { results.size }
    )

    val currentMainItem = results.getOrNull(mainPagerState.currentPage)
    var focusedClusterPhoto by remember { mutableStateOf<CuratedResult?>(null) }
    var isZoomed by remember { mutableStateOf(false) }

    val activeItem = focusedClusterPhoto ?: currentMainItem
    val isBestTake = activeItem?.isBestTake ?: false

    val swipeState = remember(activeItem?.photo?.id) {
        CurationSwipeState(
            density = density,
            coroutineScope = coroutineScope,
            haptic = haptic,
            onConfirmAction = {
                activeItem?.let { onToggleAction(it.photo.id) }
            },
            onDismiss = {
                if (focusedClusterPhoto != null) {
                    focusedClusterPhoto = null
                } else {
                    onDismiss()
                }
            }
        )
    }

    val clusterMembers = remember(currentMainItem?.clusterId, allResults) {
        val clusterId = currentMainItem?.clusterId
        if (clusterId != null) {
            allResults.filter { it.clusterId == clusterId }
        } else {
            emptyList()
        }
    }

    // Reset interaction states on page turn
    LaunchedEffect(mainPagerState.currentPage) {
        swipeState.reset()
        focusedClusterPhoto = null
        isZoomed = false
    }

    // Intercept back navigation when zoomed or focusing cluster overlay
    BackHandler {
        when {
            isZoomed -> isZoomed = false
            focusedClusterPhoto != null -> focusedClusterPhoto = null
            swipeState.isLifted -> swipeState.reset()
            else -> onDismiss()
        }
    }

    val actionColor = if (isBestTake) Color(0xFFEF5350) else Color(0xFF4CAF50)

    val colorAlpha by animateFloatAsState(
        targetValue = if (swipeState.verticalOffset.value < 0) {
            (swipeState.verticalOffset.value.absoluteValue / with(density) { 300.dp.toPx() }).coerceIn(0f, 0.6f)
        } else 0f,
        label = "colorAlpha"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // LAYER 1: Action Glow Dynamic Gradient
            if (swipeState.verticalOffset.value < 0) {
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

            // LAYER 2: Text displayed under active card
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(bottom = 110.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val isActive = swipeState.verticalOffset.value < -20f || swipeState.isLifted
                    if (isActive) {
                        val isManual = (activeItem?.score ?: 0f) == -1f
                        Text(
                            text = if (isBestTake) "SWIPE UP AGAIN TO REMOVE" else if (isManual) "SWIPE UP AGAIN TO KEEP" else "SWIPE UP AGAIN TO INCLUDE",
                            color = actionColor.copy(alpha = 0.9f),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = if (swipeState.isLifted) "Release at full swipe to confirm" else "Keep swiping up to stage",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 13.sp
                        )
                    } else {
                        val isManual = (activeItem?.score ?: 0f) == -1f
                        Text(
                            text = if (isBestTake) "Swipe up to remove" else if (isManual) "Swipe up to keep" else "Swipe up to include",
                            color = Color.White.copy(alpha = 0.35f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // LAYER 3: 3D Cover Flow Horizontal Pager
            HorizontalPager(
                state = mainPagerState,
                key = { page -> results.getOrNull(page)?.photo?.id ?: page },
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = focusedClusterPhoto == null && !swipeState.isLifted && !swipeState.isDragging && !isZoomed,
                pageSpacing = (-8).dp,
                beyondViewportPageCount = 1,
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) { page ->
                val result = results.getOrNull(page) ?: return@HorizontalPager
                val isCurrentPage = page == mainPagerState.currentPage

                val pageOffset = ((mainPagerState.currentPage - page) + mainPagerState.currentPageOffsetFraction)
                val pageOffsetAbs = pageOffset.absoluteValue

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val rotationTarget = pageOffset * -28f
                            rotationY = rotationTarget.coerceIn(-45f, 45f)

                            val scale = 1f - (pageOffsetAbs * 0.15f).coerceIn(0f, 0.35f)
                            scaleX = scale
                            scaleY = scale

                            alpha = 1f - (pageOffsetAbs * 0.45f).coerceIn(0f, 0.75f)
                            translationX = pageOffset * -with(density) { 6.dp.toPx() }
                            translationY = (pageOffsetAbs * 40f) + if (isCurrentPage && focusedClusterPhoto == null) swipeState.verticalOffset.value else 0f
                            cameraDistance = 12 * density.density

                            shadowElevation = if (isCurrentPage) 16f else 4f
                            shape = RoundedCornerShape(16.dp)
                            clip = true
                        }
                        .zIndex(if (isCurrentPage) 1f else 0f)
                        .draggable(
                            orientation = Orientation.Vertical,
                            enabled = isCurrentPage && focusedClusterPhoto == null && !isZoomed,
                            onDragStarted = { swipeState.onDragStarted() },
                            state = rememberDraggableState { delta -> swipeState.onDrag(delta) },
                            onDragStopped = { velocity -> swipeState.onDragStopped(velocity) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (result.photo.isVideo) {
                        VideoPlayer(
                            videoUri = result.photo.contentUri,
                            modifier = Modifier.fillMaxSize(),
                            active = isCurrentPage && focusedClusterPhoto == null
                        )
                    } else {
                        ZoomableMediaView(
                            uri = result.photo.contentUri,
                            dateModified = result.photo.dateModified,
                            isCurrentPage = isCurrentPage,
                            onZoomStateChanged = { zoomed -> isZoomed = zoomed }
                        )
                    }
                }
            }

            // LAYER 4: Focused Cluster Detail Overlay
            AnimatedVisibility(
                visible = focusedClusterPhoto != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                focusedClusterPhoto?.let { clusterItem ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationY = swipeState.verticalOffset.value
                                if (swipeState.verticalOffset.value < 0) {
                                    val progress = (swipeState.verticalOffset.value.absoluteValue / swipeState.liftAnchor.absoluteValue).coerceIn(0f, 1f)
                                    val scale = 1f - (progress * 0.08f)
                                    scaleX = scale
                                    scaleY = scale
                                }
                            }
                            .draggable(
                                orientation = Orientation.Vertical,
                                enabled = !isZoomed,
                                onDragStarted = { swipeState.onDragStarted() },
                                state = rememberDraggableState { delta -> swipeState.onDrag(delta) },
                                onDragStopped = { velocity -> swipeState.onDragStopped(velocity) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (clusterItem.photo.isVideo) {
                            VideoPlayer(
                                videoUri = clusterItem.photo.contentUri,
                                modifier = Modifier.fillMaxSize(),
                                active = true
                            )
                        } else {
                            ZoomableMediaView(
                                uri = clusterItem.photo.contentUri,
                                dateModified = clusterItem.photo.dateModified,
                                isCurrentPage = true,
                                onZoomStateChanged = { zoomed -> isZoomed = zoomed }
                            )
                        }
                    }
                }
            }

            // LAYER 5: Bottom Cluster Thumbnails Strip
            if (clusterMembers.size > 1 && !swipeState.isLifted && !isZoomed) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 24.dp)
                ) {
                    val context = LocalContext.current
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(
                            items = clusterMembers,
                            key = { it.photo.id }
                        ) { member ->
                            val isSelected = member.photo.id == (activeItem?.photo?.id)
                            val request = remember(member.photo.contentUri, member.photo.dateModified) {
                                ImageRequest.Builder(context)
                                    .data(member.photo.contentUri)
                                    .setParameter("modified", member.photo.dateModified)
                                    .crossfade(true)
                                    .build()
                            }

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
                                        swipeState.reset()
                                    }
                            ) {
                                AsyncImage(
                                    model = request,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                    error = ColorPainter(Color.DarkGray)
                                )
                                if (member.isBestTake) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(3.dp)
                                            .size(8.dp)
                                            .background(Color.White, CircleShape)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // LAYER 6: Header Bar
            if (!swipeState.isLifted && !isZoomed) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }

                    Text(
                        text = "${mainPagerState.currentPage + 1} / ${results.size}",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )

                    // Spacer balancing the close icon for centered text alignment
                    Spacer(modifier = Modifier.size(48.dp))
                }
            }
        }
    }
}

/**
 * High-performance zoomable image supporting seamless pinch-to-zoom,
 * bounds-clamped pan, and double-tap zoom transitions.
 */
@Composable
fun ZoomableMediaView(
    uri: Uri,
    dateModified: Long,
    isCurrentPage: Boolean,
    modifier: Modifier = Modifier,
    onZoomStateChanged: (isZoomed: Boolean) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val scaleAnim = remember { Animatable(1f) }
    val offsetXAnim = remember { Animatable(0f) }
    val offsetYAnim = remember { Animatable(0f) }

    // Reset zoom when navigating away from the page
    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage && scaleAnim.value != 1f) {
            scaleAnim.snapTo(1f)
            offsetXAnim.snapTo(0f)
            offsetYAnim.snapTo(0f)
            onZoomStateChanged(false)
        }
    }

    val request = remember(uri, dateModified) {
        ImageRequest.Builder(context)
            .data(uri)
            .setParameter("modified", dateModified)
            .crossfade(true)
            .build()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { tapCenter ->
                        coroutineScope.launch {
                            if (scaleAnim.value > 1.05f) {
                                launch { scaleAnim.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow)) }
                                launch { offsetXAnim.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                                launch { offsetYAnim.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                                onZoomStateChanged(false)
                            } else {
                                val targetScale = 3f
                                val width = size.width.toFloat()
                                val height = size.height.toFloat()

                                val maxOffsetX = (width * (targetScale - 1f)) / 2f
                                val maxOffsetY = (height * (targetScale - 1f)) / 2f

                                val targetX = ((width / 2f - tapCenter.x) * (targetScale - 1f)).coerceIn(-maxOffsetX, maxOffsetX)
                                val targetY = ((height / 2f - tapCenter.y) * (targetScale - 1f)).coerceIn(-maxOffsetY, maxOffsetY)

                                launch { scaleAnim.animateTo(targetScale, spring(stiffness = Spring.StiffnessMediumLow)) }
                                launch { offsetXAnim.animateTo(targetX, spring(stiffness = Spring.StiffnessMediumLow)) }
                                launch { offsetYAnim.animateTo(targetY, spring(stiffness = Spring.StiffnessMediumLow)) }
                                onZoomStateChanged(true)
                            }
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pointerCount = event.changes.size
                        val currentScale = scaleAnim.value

                        // Case A: Image is zoomed in (> 1.05x). Pan & zoom inside image bounds.
                        if (currentScale > 1.05f) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()

                            if (zoom != 1f || pan != Offset.Zero) {
                                event.changes.forEach { change ->
                                    if (change.positionChanged()) change.consume()
                                }

                                coroutineScope.launch {
                                    val newScale = (currentScale * zoom).coerceIn(1f, 5f)
                                    scaleAnim.snapTo(newScale)

                                    val width = size.width.toFloat()
                                    val height = size.height.toFloat()
                                    val maxOffsetX = (width * (newScale - 1f)) / 2f
                                    val maxOffsetY = (height * (newScale - 1f)) / 2f

                                    val newX = (offsetXAnim.value + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                                    val newY = (offsetYAnim.value + pan.y).coerceIn(-maxOffsetY, maxOffsetY)

                                    offsetXAnim.snapTo(newX)
                                    offsetYAnim.snapTo(newY)

                                    if (newScale <= 1.05f) {
                                        onZoomStateChanged(false)
                                    }
                                }
                            }
                        }
                        // Case B: Image is at 1.0x. Only intercept if user is pinching with 2+ fingers.
                        else if (pointerCount >= 2) {
                            val zoom = event.calculateZoom()
                            if (zoom != 1f) {
                                event.changes.forEach { change ->
                                    if (change.positionChanged()) change.consume()
                                }

                                coroutineScope.launch {
                                    val newScale = (currentScale * zoom).coerceIn(1f, 5f)
                                    scaleAnim.snapTo(newScale)

                                    if (newScale > 1.05f) {
                                        onZoomStateChanged(true)
                                    }
                                }
                            }
                        }
                        // Case C: 1 finger drag at 1.0x -> DO NOT consume so Pager & Draggable work smoothly!
                    } while (event.changes.any { it.pressed })

                    // If scale was left near 1.0x when fingers lift, snap back to baseline
                    if (scaleAnim.value < 1.05f && scaleAnim.value != 1f) {
                        coroutineScope.launch {
                            launch { scaleAnim.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow)) }
                            launch { offsetXAnim.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                            launch { offsetYAnim.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                            onZoomStateChanged(false)
                        }
                    }
                }
            }
    ) {
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scaleAnim.value
                    scaleY = scaleAnim.value
                    translationX = offsetXAnim.value
                    translationY = offsetYAnim.value
                }
        )
    }
}