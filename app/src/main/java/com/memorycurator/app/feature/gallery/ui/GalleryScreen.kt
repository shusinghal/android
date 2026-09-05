package com.memorycurator.app.feature.gallery.ui

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.memorycurator.app.ui.preview.PreviewStockPhotos
import com.memorycurator.app.core.ai.RejectionReason
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.memorycurator.app.core.ai.CuratedResult
import com.memorycurator.app.data.media.MediaPhoto
import com.memorycurator.app.ui.gallery.GalleryViewModel
import com.memorycurator.app.ui.screens.CurationViewerScreen
import com.memorycurator.app.ui.theme.MemoryCuratorTheme
import kotlinx.coroutines.flow.flowOf
import java.util.*

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel,
    state: LazyGridState = rememberLazyGridState()
) {

    val uiState by viewModel
        .uiState
        .collectAsStateWithLifecycle()

    val isBestTakesOnly by viewModel.isBestTakesOnly.collectAsState()

    val photos =
        viewModel
            .photos
            .collectAsLazyPagingItems()

    var selectedIndex by remember {
        mutableIntStateOf(-1)
    }

    // Local state to track manual curation changes before Paging refresh
    val manualToggles = remember { mutableStateMapOf<Long, Boolean>() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Gallery",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.indexMedia() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = Color.White
                        )
                    }
                    
                    FilterChip(
                        selected = isBestTakesOnly,
                        onClick = { viewModel.toggleBestTakesOnly() },
                        label = {
                            Text(
                                text = if (isBestTakesOnly) "Best" else "All",
                                fontSize = 11.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color.White.copy(alpha = 0.05f),
                            labelColor = Color.LightGray,
                            selectedContainerColor = Color.White.copy(alpha = 0.2f),
                            selectedLabelColor = Color.White
                        ),
                        border = null,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {

            if (uiState.isLoading && photos.itemCount == 0) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }

            if (photos.itemCount == 0 && !uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (isBestTakesOnly) "No Best Takes found." else "No photos found.",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    state = state,
                    columns = GridCells.Adaptive(
                        minSize = 120.dp
                    ),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 6.dp,
                        end = 6.dp,
                        top = 8.dp,
                        bottom = 100.dp
                    )
                ) {
                    items(
                        count = photos.itemCount
                    ) { index ->
                        val photo = photos[index] ?: return@items
                        val isBestTake = manualToggles[photo.id] ?: photo.isBestTake
                        
                        Box(
                            modifier = Modifier
                                .padding(3.dp)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(18.dp))
                                .clickable { selectedIndex = index }
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(photo.contentUri)
                                    .setParameter("modified", photo.dateModified)
                                    .crossfade(true)
                                    .size(300)
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                error = androidx.compose.ui.graphics.painter.ColorPainter(Color.DarkGray)
                            )
                            
                                // Footer Info Bar (AI Score & Reason)
                                if (photo.aiScore != -1f) {
                                    val isHigh = photo.aiScore >= 0.7f
                                    val softGreen = Color(0xFFA5D6A7)
                                    val reasonName = photo.rejectionReason ?: "NONE"
                                    val reason = try { RejectionReason.valueOf(reasonName) } catch (_: Exception) { RejectionReason.NONE }
                                    val label = if (isBestTake || reason == RejectionReason.NONE) null else when (reason) {
                                        RejectionReason.DUPLICATE -> "Similar"
                                        RejectionReason.BLURRY -> "Hazy"
                                        RejectionReason.EYES_CLOSED -> "Blinked"
                                        RejectionReason.BAD_EXPRESSION -> "Awkward"
                                        RejectionReason.POOR_LIGHTING -> "Darkish"
                                        RejectionReason.POOR_COMPOSITION -> "Framing"
                                        RejectionReason.LOW_QUALITY -> "Subpar"
                                        RejectionReason.MANUAL -> "Choice"
                                        else -> null
                                    }

                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(bottom = 4.dp),
                                        color = Color.Black.copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (label != null) {
                                                Text(
                                                    text = label.uppercase(),
                                                    color = Color.White,
                                                    fontSize = 7.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    letterSpacing = 0.5.sp
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.AutoAwesome,
                                                    contentDescription = null,
                                                    tint = if (isHigh) softGreen else Color.White.copy(alpha = 0.4f),
                                                    modifier = Modifier.size(8.dp)
                                                )
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Text(
                                                    text = "${(photo.aiScore * 100).toInt()}",
                                                    color = if (isHigh) softGreen else Color.White,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Black
                                                )
                                            }
                                        }
                                    }
                                }

                            if (photo.isVideo) {
                                Icon(
                                    imageVector = Icons.Default.PlayCircle,
                                    contentDescription = "Video",
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .padding(8.dp),
                                    tint = Color.White.copy(alpha = 0.8f)
                                )
                            }
                            
                            if (isBestTake) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Best Take",
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .size(18.dp),
                                    tint = Color(0xFFFFD700) // Gold
                                )
                            }
                        }
                    }
                }
            }

            if (
                selectedIndex >= 0 &&
                selectedIndex < photos.itemCount
            ) {
                // Map the paged photos to CuratedResults for the viewer
                val curatedResults = List(photos.itemCount) { i ->
                    val photo = photos[i] ?: MediaPhoto(0, Uri.EMPTY, 0, 0)
                    val isManualToggled = manualToggles.containsKey(photo.id)
                    val currentIsBest = if (isManualToggled) manualToggles[photo.id] == true else photo.isBestTake
                    
                    val reasonName = photo.rejectionReason ?: "NONE"
                    val reason = try { RejectionReason.valueOf(reasonName) } catch (_: Exception) { RejectionReason.NONE }

                    CuratedResult(
                        photo = photo,
                        score = if (isManualToggled) (if (currentIsBest) 1.0f else 0.0f) else photo.aiScore,
                        isBestTake = currentIsBest,
                        rejectionReason = reason,
                        clusterId = null
                    )
                }

                CurationViewerScreen(
                    results = curatedResults,
                    allResults = curatedResults,
                    initialIndex = selectedIndex,
                    onToggleAction = { id ->
                        val current = manualToggles[id] ?: false
                        val next = !current
                        manualToggles[id] = next
                        viewModel.toggleBestTake(id, next)
                    },
                    onDismiss = {
                        selectedIndex = -1
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryGridPreview(
    photos: List<MediaPhoto>,
    state: LazyGridState = rememberLazyGridState()
) {
    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 20.dp, end = 20.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Gallery",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
        }

        LazyVerticalGrid(
            state = state,
            columns = GridCells.Adaptive(minSize = 120.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 100.dp)
        ) {
            items(count = photos.size) { index ->
                val photo = photos[index]
                Box(
                    modifier = Modifier
                        .padding(3.dp)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(18.dp))
                ) {
                    AsyncImage(
                        model = photo.contentUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GalleryScreenPreview() {
    MemoryCuratorTheme {
        GalleryGridPreview(photos = PreviewStockPhotos.getPhotos(10))
    }
}
