package com.memorycurator.app.feature.timeline.ui

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.tooling.preview.Preview
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BatchPrediction
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.memorycurator.app.core.ai.CuratedResult
import com.memorycurator.app.core.ai.RejectionReason
import com.memorycurator.app.data.media.MediaPhoto
import com.memorycurator.app.data.media.MediaRepository
import com.memorycurator.app.feature.timeline.model.TimelineGroup
import com.memorycurator.app.feature.viewer.ui.ViewerScreen
import com.memorycurator.app.ui.screens.AICurationViewModel
import com.memorycurator.app.ui.screens.AICurationViewModelFactory
import com.memorycurator.app.ui.screens.CurationViewerScreen
import com.memorycurator.app.ui.screens.AnalysisProgressView
import com.memorycurator.app.ui.theme.MemoryCuratorTheme

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TimelineDetailScreen(
    group: TimelineGroup,
    onBack: () -> Unit,
    onBestTakesClick: (TimelineGroup) -> Unit,
    repository: MediaRepository? = null
) {
    var selectedIndex by remember { mutableIntStateOf(-1) }
    var isBestTakesActive by rememberSaveable { mutableStateOf(false) }
    // Track which filtered list is being viewed in the viewer
    var viewerSourceList by remember { mutableStateOf<List<CuratedResult>>(emptyList()) }

    val viewModel: AICurationViewModel? = if (repository != null) {
        viewModel(
            key = "curation_${group.title}",
            factory = AICurationViewModelFactory(repository)
        )
    } else null

    val context = LocalContext.current
    val analysisResults by viewModel?.analysisResults?.collectAsState() ?: remember { mutableStateOf(emptyList<CuratedResult>()) }
    val isAnalyzing by viewModel?.isAnalyzing?.collectAsState() ?: remember { mutableStateOf(false) }
    val progress by viewModel?.progress?.collectAsState() ?: remember { mutableStateOf(null) }
    val archivedPhotos by viewModel?.archivedPhotos?.collectAsState() ?: remember { mutableStateOf(emptyList<MediaPhoto>()) }
    
    val isSelectionMode by viewModel?.isSelectionMode?.collectAsState() ?: remember { mutableStateOf(false) }
    val selectedIds by viewModel?.selectedIds?.collectAsState() ?: remember { mutableStateOf(emptySet<Long>()) }

    val gridState = rememberLazyGridState()

    LaunchedEffect(group.photos) {
        viewModel?.setSessionPhotos(group.photos)
    }

    // Auto-activate Best Takes UI if analysis already exists
    LaunchedEffect(analysisResults) {
        if (analysisResults.isNotEmpty()) {
            isBestTakesActive = true
        }
    }

    // Scroll to top when analysis finishes and we switch to Curation view
    LaunchedEffect(isAnalyzing) {
        if (!isAnalyzing && isBestTakesActive && analysisResults.isNotEmpty()) {
            gridState.scrollToItem(0)
        }
    }

    BackHandler(enabled = selectedIndex >= 0 || isSelectionMode) {
        if (selectedIndex >= 0) {
            selectedIndex = -1
        } else {
            viewModel?.toggleSelectionMode(false)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = group.title,
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${group.photos.size} photos",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                        }
                    },
                    navigationIcon = {
                        if (isSelectionMode) {
                            IconButton(onClick = { viewModel?.toggleSelectionMode(false) }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Cancel", tint = Color.White)
                            }
                        } else {
                            IconButton(onClick = onBack) {
                                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                            }
                        }
                    },
                    actions = {
                        if (isSelectionMode) {
                            TextButton(onClick = { viewModel?.selectAll() }) {
                                Text("Select All", color = Color.White)
                            }
                            IconButton(
                                onClick = { viewModel?.archiveSelected() },
                                enabled = selectedIds.isNotEmpty()
                            ) {
                                Icon(
                                    Icons.Default.Archive,
                                    "Archive",
                                    tint = if (selectedIds.isNotEmpty()) Color.White else Color.White.copy(alpha = 0.4f)
                                )
                            }
                        } else if (isBestTakesActive && analysisResults.isNotEmpty()) {
                            val reviewCount = analysisResults.count { !it.isBestTake }
                            if (reviewCount > 0) {
                                TextButton(onClick = { viewModel?.archiveAllUnderReview() }) {
                                    Text("Clean Up ($reviewCount)", color = Color.White)
                                }
                            }
                            // Reset word instead of icon
                            TextButton(onClick = {
                                isBestTakesActive = false
                                viewModel?.resetCuration(group.photos)
                            }) {
                                Text("Reset", color = Color.White)
                            }
                        }

                        if (!isSelectionMode && (!isBestTakesActive || analysisResults.isEmpty())) {
                            TextButton(onClick = {
                                isBestTakesActive = true
                                viewModel?.filterBestTakes(context, group.photos)
                            }) {
                                Text(
                                    text = "Best Takes",
                                    color = Color.White
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            AnimatedContent(
                targetState = isAnalyzing,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "analysis_transition"
            ) { analyzing ->
                if (analyzing && progress != null) {
                    Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                        AnalysisProgressView(progress!!)
                    }
                } else {
                    val keepers = remember(analysisResults) { analysisResults.filter { it.isBestTake } }
                    val forReview = remember(analysisResults) { analysisResults.filter { !it.isBestTake } }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = gridState,
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentPadding = PaddingValues(1.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                        horizontalArrangement = Arrangement.spacedBy(1.dp)
                    ) {

                        if (isBestTakesActive && analysisResults.isNotEmpty()) {
                            if (keepers.isNotEmpty()) {
                                item(span = { GridItemSpan(3) }, key = "keepers_header") {
                                    SectionHeaderSmall("Keepers", Icons.Default.AutoAwesome)
                                }
                                itemsIndexed(keepers, key = { _, result -> result.photo.id }) { index, result ->
                                    PhotoGridItem(
                                        photo = result.photo,
                                        isSelected = selectedIds.contains(result.photo.id),
                                        isSelectionMode = isSelectionMode,
                                        modifier = Modifier.animateItem(),
                                        onClick = { 
                                            if (isSelectionMode) {
                                                viewModel?.togglePhotoSelection(result.photo.id)
                                            } else {
                                                viewerSourceList = keepers
                                                selectedIndex = index 
                                            }
                                        },
                                        onLongClick = {
                                            viewModel?.togglePhotoSelection(result.photo.id)
                                        }
                                    )
                                }
                            }
                            
                            if (forReview.isNotEmpty()) {
                                item(span = { GridItemSpan(3) }, key = "review_header") {
                                    SectionHeaderSmall("For Review", Icons.Default.BatchPrediction)
                                }
                                itemsIndexed(forReview, key = { _, result -> result.photo.id }) { index, result ->
                                    PhotoGridItem(
                                        photo = result.photo,
                                        isSelected = selectedIds.contains(result.photo.id),
                                        isSelectionMode = isSelectionMode,
                                        modifier = Modifier.animateItem(),
                                        onClick = { 
                                            if (isSelectionMode) {
                                                viewModel?.togglePhotoSelection(result.photo.id)
                                            } else {
                                                viewerSourceList = forReview
                                                selectedIndex = index 
                                            }
                                        },
                                        onLongClick = {
                                            viewModel?.togglePhotoSelection(result.photo.id)
                                        }
                                    )
                                }
                            }
                        } else {
                            itemsIndexed(group.photos, key = { _, photo -> photo.id }) { index, photo ->
                                PhotoGridItem(
                                    photo = photo,
                                    isSelected = selectedIds.contains(photo.id),
                                    isSelectionMode = isSelectionMode,
                                    modifier = Modifier.animateItem(),
                                    onClick = { 
                                        if (isSelectionMode) {
                                            viewModel?.togglePhotoSelection(photo.id)
                                        } else {
                                            selectedIndex = index 
                                        }
                                    },
                                    onLongClick = {
                                        viewModel?.togglePhotoSelection(photo.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (selectedIndex >= 0) {
            if (isBestTakesActive && analysisResults.isNotEmpty() && viewerSourceList.isNotEmpty()) {
                CurationViewerScreen(
                    results = viewerSourceList, // Pass only the relevant list (Keepers OR Review)
                    allResults = analysisResults,
                    initialIndex = selectedIndex,
                    onToggleAction = { id -> viewModel?.toggleBestTake(id) },
                    onDismiss = { selectedIndex = -1 }
                )
            } else {
                ViewerScreen(
                    photos = group.photos,
                    initialIndex = selectedIndex,
                    onDismiss = { selectedIndex = -1 }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoGridItem(
    photo: MediaPhoto,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(photo.contentUri)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (isSelected) Color.White.copy(alpha = 0.2f) else Color.Transparent)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(24.dp)
                    .background(if (isSelected) Color.White else Color.Black.copy(alpha = 0.3f), CircleShape)
                    .border(2.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                }
            }
        } else if (photo.isVideo) {
            Icon(
                imageVector = Icons.Default.PlayCircle,
                contentDescription = "Video",
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(24.dp),
                tint = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun SectionHeaderSmall(title: String, icon: ImageVector) {
    Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}


@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun TimelineDetailScreenPreview() {
    val mockPhotos = listOf(
        MediaPhoto(1, Uri.EMPTY, System.currentTimeMillis()),
        MediaPhoto(2, Uri.EMPTY, System.currentTimeMillis()),
        MediaPhoto(3, Uri.EMPTY, System.currentTimeMillis())
    )
    val mockGroup = TimelineGroup(
        title = "Recent Memories",
        photos = mockPhotos
    )

    MemoryCuratorTheme {
        TimelineDetailScreen(
            group = mockGroup,
            onBack = {},
            onBestTakesClick = {},
            repository = null
        )
    }
}

@Preview
@Composable
fun PhotoGridItemPreview() {
    MemoryCuratorTheme {
        PhotoGridItem(
            photo = MediaPhoto(1, Uri.EMPTY, System.currentTimeMillis()),
            onClick = {},
            isSelected = false,
            isSelectionMode = false
        )
    }
}
