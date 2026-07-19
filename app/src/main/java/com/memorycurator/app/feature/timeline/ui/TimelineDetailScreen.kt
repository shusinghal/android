package com.memorycurator.app.feature.timeline.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.tooling.preview.Preview
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BatchPrediction
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TimelineDetailScreen(
    group: TimelineGroup,
    onBack: () -> Unit,
    onBestTakesClick: (TimelineGroup) -> Unit,
    repository: MediaRepository? = null
) {
    var selectedIndex by remember { mutableIntStateOf(-1) }
    var isBestTakesActive by remember { mutableStateOf(false) }
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

    // Auto-activate Best Takes UI if analysis already exists
    LaunchedEffect(analysisResults) {
        if (analysisResults.isNotEmpty()) {
            isBestTakesActive = true
        }
    }

    BackHandler(enabled = selectedIndex >= 0) {
        selectedIndex = -1
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
                        IconButton(onClick = onBack) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    actions = {
                        if (isBestTakesActive && analysisResults.isNotEmpty()) {
                            // Reset word instead of icon
                            TextButton(onClick = { 
                                isBestTakesActive = false
                                viewModel?.resetCuration(group.photos) 
                            }) {
                                Text("Reset", color = Color.White)
                            }
                            // Retake word instead of icon
                            TextButton(onClick = { viewModel?.filterBestTakes(context, group.photos) }) {
                                Text("Retake", color = Color.White)
                            }
                        }
                        
                        if (!isBestTakesActive || analysisResults.isEmpty()) {
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
            if (isAnalyzing && progress != null) {
                Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                    AnalysisProgressView(progress!!)
                }
            } else {
                val keepers = remember(analysisResults) { analysisResults.filter { it.isBestTake } }
                val forReview = remember(analysisResults) { analysisResults.filter { !it.isBestTake } }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(1.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                    horizontalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    if (isBestTakesActive && analysisResults.isNotEmpty()) {
                        if (keepers.isNotEmpty()) {
                            item(span = { GridItemSpan(3) }) {
                                SectionHeaderSmall("Keepers", Icons.Default.AutoAwesome)
                            }
                            itemsIndexed(keepers) { index, result ->
                                PhotoGridItem(
                                    photo = result.photo,
                                    onClick = { 
                                        viewerSourceList = keepers
                                        selectedIndex = index 
                                    }
                                )
                            }
                        }
                        
                        if (forReview.isNotEmpty()) {
                            item(span = { GridItemSpan(3) }) {
                                SectionHeaderSmall("For Review", Icons.Default.BatchPrediction)
                            }
                            itemsIndexed(forReview) { index, result ->
                                PhotoGridItem(
                                    photo = result.photo,
                                    onClick = { 
                                        viewerSourceList = forReview
                                        selectedIndex = index 
                                    }
                                )
                            }
                        }
                    } else {
                        itemsIndexed(group.photos) { index, photo ->
                            PhotoGridItem(
                                photo = photo,
                                onClick = { 
                                    selectedIndex = index 
                                }
                            )
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

@Composable
fun PhotoGridItem(photo: MediaPhoto, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable { onClick() }
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
