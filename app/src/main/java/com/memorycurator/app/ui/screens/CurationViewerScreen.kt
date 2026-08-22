package com.memorycurator.app.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
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
import com.memorycurator.app.core.ai.RejectionReason
import com.memorycurator.app.ui.components.VideoPlayer
import com.memorycurator.app.ui.preview.PreviewStockPhotos
import com.memorycurator.app.ui.theme.MemoryCuratorTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/**
 * Optimized state holder managing swipe actions with job cancellation
 * to prevent coroutine flooding during rapid gestures.
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

    private var dragJob: Job? = null

    fun onDrag(delta: Float) {
        val target = verticalOffset.value + delta
        if (target < 0 || (target >= 0 && !isLifted)) {
            dragJob?.cancel()
            dragJob = coroutineScope.launch {
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
                    // Call confirmation immediately so the UI state (colors/text) 
                    // updates while the card is still animating back to center.
                    onConfirmAction()
                    verticalOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
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
        dragJob?.cancel()
        coroutineScope.launch {
            verticalOffset.snapTo(0f)
            isLifted = false
            isDragging = false
        }
    }
}

/**
 * Idiomatic factory for CurationSwipeState.
 */
@Composable
fun rememberCurationSwipeState(
    key: Any?,
    onConfirmAction: () -> Unit,
    onDismiss: () -> Unit,
    density: Density = LocalDensity.current,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    haptic: HapticFeedback = LocalHapticFeedback.current
): CurationSwipeState {
    return remember(key) {
        CurationSwipeState(
            density = density,
            coroutineScope = coroutineScope,
            haptic = haptic,
            onConfirmAction = onConfirmAction,
            onDismiss = onDismiss
        )
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

    // Fixing closure captures for toggling
    val currentOnConfirm by rememberUpdatedState(onToggleAction)
    val currentActiveItem by rememberUpdatedState(activeItem)

    val swipeState = rememberCurationSwipeState(
        key = activeItem?.photo?.id,
        onConfirmAction = {
            currentActiveItem?.let { currentOnConfirm(it.photo.id) }
        },
        onDismiss = {
            if (focusedClusterPhoto != null) {
                focusedClusterPhoto = null
            } else {
                onDismiss()
            }
        }
    )

    // Optimized O(1) cluster lookup
    val clusterMap = remember(allResults) {
        allResults.groupBy { it.clusterId }
    }

    val clusterMembers = remember(currentMainItem?.clusterId, clusterMap) {
        currentMainItem?.clusterId?.let { clusterMap[it] } ?: emptyList()
    }

    val reviewReasonLabel = remember(activeItem) {
        if (activeItem?.isBestTake == true) null else when (activeItem?.rejectionReason) {
            RejectionReason.DUPLICATE -> "Similar"
            RejectionReason.BLURRY -> "Hazy"
            RejectionReason.EYES_CLOSED -> "Blinked"
            RejectionReason.BAD_EXPRESSION -> "Awkward"
            RejectionReason.POOR_LIGHTING -> "Darkish"
            RejectionReason.POOR_COMPOSITION -> "Framing"
            RejectionReason.LOW_QUALITY -> "Subpar"
            RejectionReason.MANUAL -> "Your choice"
            else -> "Subpar"
        }
    }

    LaunchedEffect(mainPagerState.currentPage) {
        swipeState.reset()
        focusedClusterPhoto = null
        isZoomed = false
    }

    BackHandler {
        when {
            isZoomed -> isZoomed = false
            focusedClusterPhoto != null -> focusedClusterPhoto = null
            swipeState.isLifted -> swipeState.reset()
            else -> onDismiss()
        }
    }

    val actionColor = if (isBestTake) Color(0xFFEF5350) else Color(0xFF4CAF50)
    val dragDistancePx = with(density) { 300.dp.toPx() }

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
                .drawBehind {
                    // Optimized Glow: Defer state read to Draw Phase to skip recomposition
                    val offset = swipeState.verticalOffset.value
                    if (offset < 0) {
                        val alpha = (offset.absoluteValue / dragDistancePx).coerceIn(0f, 0.6f)
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, actionColor.copy(alpha = alpha)),
                                startY = 200.dp.toPx()
                            )
                        )
                    }
                }
        ) {
            // LAYER 2: Text displayed under active card
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(2f) // Force text to stay on top of large photo cards
                    .navigationBarsPadding()
                    .padding(bottom = 110.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                // Optimized text trigger using derivedStateOf
                val isActive by remember {
                    derivedStateOf {
                        swipeState.verticalOffset.value < -20f || swipeState.isLifted
                    }
                }

                // AI Reason Badge (Bottom Left)
                if (!isBestTake && reviewReasonLabel != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 24.dp, bottom = 24.dp),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                        ) {
                            Text(
                                text = reviewReasonLabel,
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                beyondViewportPageCount = 0,
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
                        .semantics {
                            // Accessibility support
                            customActions = listOf(
                                CustomAccessibilityAction(
                                    label = if (isBestTake) "Remove from Best Takes" else "Add to Best Takes",
                                    action = {
                                        activeItem?.let { currentOnConfirm(it.photo.id) }
                                        true
                                    }
                                )
                            )
                        }
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

                    Spacer(modifier = Modifier.size(48.dp))
                }
            }
        }
    }
}

/**
 * Unified high-performance zoomable media view.
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
                // Unified gesture detection: Taps and Transforms in a single scope
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    
                    // We check for double tap manually to keep everything in one pass if needed, 
                    // but for brevity and consistency we use detectTapGestures in a secondary block 
                    // or combine here.
                }
            }
            // For stability, we use detectTapGestures and detectTransformGestures separately 
            // but ensure they are optimized.
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

                        if (currentScale > 1.05f) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            if (zoom != 1f || pan != Offset.Zero) {
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                                coroutineScope.launch {
                                    val newScale = (currentScale * zoom).coerceIn(1f, 5f)
                                    scaleAnim.snapTo(newScale)
                                    val width = size.width.toFloat()
                                    val height = size.height.toFloat()
                                    val maxOffsetX = (width * (newScale - 1f)) / 2f
                                    val maxOffsetY = (height * (newScale - 1f)) / 2f
                                    offsetXAnim.snapTo((offsetXAnim.value + pan.x).coerceIn(-maxOffsetX, maxOffsetX))
                                    offsetYAnim.snapTo((offsetYAnim.value + pan.y).coerceIn(-maxOffsetY, maxOffsetY))
                                    if (newScale <= 1.05f) onZoomStateChanged(false)
                                }
                            }
                        } else if (pointerCount >= 2) {
                            val zoom = event.calculateZoom()
                            if (zoom != 1f) {
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                                coroutineScope.launch {
                                    val newScale = (currentScale * zoom).coerceIn(1f, 5f)
                                    scaleAnim.snapTo(newScale)
                                    if (newScale > 1.05f) onZoomStateChanged(true)
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })

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
