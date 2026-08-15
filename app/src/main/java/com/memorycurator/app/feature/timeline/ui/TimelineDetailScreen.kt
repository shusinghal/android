package com.memorycurator.app.feature.timeline.ui

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.tooling.preview.Preview
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
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
import com.memorycurator.app.data.media.MediaIndexer
import com.memorycurator.app.data.media.MediaPhoto
import com.memorycurator.app.data.media.MediaRepository
import com.memorycurator.app.feature.timeline.model.TimelineGroup
import com.memorycurator.app.feature.viewer.ui.ViewerScreen
import com.memorycurator.app.ui.screens.AICurationViewModel
import com.memorycurator.app.ui.screens.AICurationViewModelFactory
import com.memorycurator.app.ui.screens.CurationViewerScreen
import com.memorycurator.app.ui.screens.AnalysisProgressView
import com.memorycurator.app.ui.preview.PreviewStockPhotos
import com.memorycurator.app.ui.theme.MemoryCuratorTheme

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TimelineDetailScreen(
    group: TimelineGroup,
    onBack: () -> Unit,
    onBestTakesClick: (TimelineGroup) -> Unit,
    repository: MediaRepository? = null,
    mediaIndexer: MediaIndexer? = null
) {
    var selectedIndex by remember { mutableIntStateOf(-1) }
    var isBestTakesActive by rememberSaveable { mutableStateOf(false) }
    // Track which filtered list is being viewed in the viewer
    var viewerSourceList by remember { mutableStateOf<List<CuratedResult>>(emptyList()) }

    val viewModel: AICurationViewModel? = if (repository != null && mediaIndexer != null) {
        viewModel(
            key = "curation_${group.title}",
            factory = AICurationViewModelFactory(repository, mediaIndexer)
        )
    } else null

    val context = LocalContext.current
    val analysisResults by viewModel?.analysisResults?.collectAsState() ?: remember { mutableStateOf(emptyList<CuratedResult>()) }
    val isAnalyzing by viewModel?.isAnalyzing?.collectAsState() ?: remember { mutableStateOf(false) }
    val isSyncing by viewModel?.isSyncing?.collectAsState() ?: remember { mutableStateOf(false) }
    val progress by viewModel?.progress?.collectAsState() ?: remember { mutableStateOf(null) }
    val archivedPhotos by viewModel?.archivedPhotos?.collectAsState() ?: remember { mutableStateOf(emptyList<MediaPhoto>()) }
    
    val isSelectionMode by viewModel?.isSelectionMode?.collectAsState() ?: remember { mutableStateOf(false) }
    val selectedIds by viewModel?.selectedIds?.collectAsState() ?: remember { mutableStateOf(emptySet<Long>()) }

    val keepers = remember(analysisResults) { analysisResults.filter { it.score != -1f && it.isBestTake } }
    val forReview = remember(analysisResults) { analysisResults.filter { it.score != -1f && !it.isBestTake } }
    val notAnalyzed = remember(analysisResults) { analysisResults.filter { it.score == -1f } }
    val nonArchivedPhotos = remember(group.photos, archivedPhotos) {
        val archivedIds = archivedPhotos.map { it.id }.toSet()
        group.photos.filter { it.id !in archivedIds }
    }

    val gridState = rememberLazyGridState()

    val writeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel?.archiveSelected()
        }
    }

    fun requestWrite(uris: List<Uri>) {
        if (uris.isEmpty()) return
        try {
            val pendingIntent = MediaStore.createWriteRequest(context.contentResolver, uris)
            writeLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    LaunchedEffect(group.photos) {
        viewModel?.setSessionPhotos(group.photos)
    }

    // Auto-activate Best Takes UI if analysis already exists (persists from previous sessions)
    LaunchedEffect(analysisResults) {
        if (analysisResults.any { it.score != -1f }) {
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
                                text = if (isSyncing) "Syncing gallery..." else "${group.photos.size} photos",
                                color = if (isSyncing) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f),
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
                            IconButton(onClick = { viewModel?.selectAll() }) {
                                Icon(Icons.Default.SelectAll, "Select All", tint = Color.White)
                            }
                            IconButton(
                                onClick = { 
                                    val uris = nonArchivedPhotos
                                        .filter { it.id in selectedIds }
                                        .map { it.contentUri }
                                    sharePhotos(context, uris)
                                },
                                enabled = selectedIds.isNotEmpty()
                            ) {
                                Icon(
                                    Icons.Default.Share,
                                    "Share",
                                    tint = if (selectedIds.isNotEmpty()) Color.White else Color.White.copy(alpha = 0.4f)
                                )
                            }
                            IconButton(
                                onClick = { 
                                    val uris = nonArchivedPhotos
                                        .filter { it.id in selectedIds }
                                        .map { it.contentUri }
                                    requestWrite(uris)
                                },
                                enabled = selectedIds.isNotEmpty()
                            ) {
                                Icon(
                                    Icons.Default.Archive,
                                    "Archive",
                                    tint = if (selectedIds.isNotEmpty()) Color.White else Color.White.copy(alpha = 0.4f)
                                )
                            }
                        } else if (isBestTakesActive && analysisResults.isNotEmpty()) {
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
                if (analyzing) {
                    progress?.let { currentProgress ->
                        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                            AnalysisProgressView(currentProgress)
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = gridState,
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentPadding = PaddingValues(1.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                        horizontalArrangement = Arrangement.spacedBy(1.dp)
                    ) {

                        if (isBestTakesActive && (keepers.isNotEmpty() || forReview.isNotEmpty())) {
                            if (keepers.isNotEmpty()) {
                                item(span = { GridItemSpan(3) }, key = "keepers_header") {
                                    val sectionIds = remember(keepers) { keepers.map { it.photo.id } }
                                    val allSelected = remember(selectedIds, sectionIds) { sectionIds.isNotEmpty() && sectionIds.all { selectedIds.contains(it) } }
                                    SectionHeaderSmall(
                                        title = "Keepers",
                                        icon = Icons.Default.AutoAwesome,
                                        action = {
                                            if (isSelectionMode) {
                                                IconButton(onClick = { viewModel?.toggleSectionSelection(sectionIds) }) {
                                                    Icon(
                                                        imageVector = if (allSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                                        contentDescription = "Select All",
                                                        tint = if (allSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f)
                                                    )
                                                }
                                            }
                                        }
                                    )
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
                                    val sectionIds = remember(forReview) { forReview.map { it.photo.id } }
                                    val allSelected = remember(selectedIds, sectionIds) { sectionIds.isNotEmpty() && sectionIds.all { selectedIds.contains(it) } }
                                    SectionHeaderSmall(
                                        title = "For Review",
                                        icon = Icons.Default.BatchPrediction,
                                        action = {
                                            if (isSelectionMode) {
                                                IconButton(onClick = { viewModel?.toggleSectionSelection(sectionIds) }) {
                                                    Icon(
                                                        imageVector = if (allSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                                        contentDescription = "Select All",
                                                        tint = if (allSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f)
                                                    )
                                                }
                                            }
                                        }
                                    )
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
                            
                            // If user clicked "Best Takes" but some photos weren't analyzed or were new, 
                            // show them in a generic section or handle as part of review
                            if (notAnalyzed.isNotEmpty()) {
                                item(span = { GridItemSpan(3) }) {
                                    SectionHeaderSmall("Yet to Review", Icons.Default.Close)
                                }
                                itemsIndexed(notAnalyzed) { index, result ->
                                    PhotoGridItem(
                                        photo = result.photo,
                                        isSelected = selectedIds.contains(result.photo.id),
                                        isSelectionMode = isSelectionMode,
                                        onClick = {
                                            if (isSelectionMode) {
                                                viewModel?.togglePhotoSelection(result.photo.id)
                                            } else {
                                                viewerSourceList = notAnalyzed
                                                selectedIndex = index
                                            }
                                        }
                                    )
                                }
                            }
                        } else {
                            itemsIndexed(nonArchivedPhotos, key = { _, photo -> photo.id }) { index, photo ->
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
            //if (isBestTakesActive && analysisResults.isNotEmpty() && viewerSourceList.isNotEmpty()) {
                CurationViewerScreen(
                    results = viewerSourceList, // Pass only the relevant list (Keepers OR Review)
                    allResults = analysisResults,
                    initialIndex = selectedIndex,
                    onToggleAction = { id -> viewModel?.toggleBestTake(id) },
                    onDismiss = { selectedIndex = -1 }
                )
            //} else {
             //   ViewerScreen(
             //       photos = group.photos,
             //       initialIndex = selectedIndex,
             //       onDismiss = { selectedIndex = -1 }
              //  )
           // }
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
                .setParameter("modified", photo.dateModified) // Force invalidation on change
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            error = androidx.compose.ui.graphics.painter.ColorPainter(Color.DarkGray)
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
fun SectionHeaderSmall(
    title: String, 
    icon: ImageVector,
    action: @Composable () -> Unit = {}
) {
    Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        action()
    }
}


@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun TimelineDetailScreenPreview() {
    val mockPhotos = PreviewStockPhotos.getPhotos(9)
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
            photo = MediaPhoto(1, Uri.EMPTY, System.currentTimeMillis(), System.currentTimeMillis()),
            onClick = {},
            isSelected = false,
            isSelectionMode = false
        )
    }
}

fun sharePhotos(context: Context, uris: List<Uri>) {
    if (uris.isEmpty()) return
    
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            type = context.contentResolver.getType(uris[0]) ?: "image/*"
            putExtra(Intent.EXTRA_STREAM, uris[0])
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
    }
    
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    val chooser = Intent.createChooser(intent, "Share with")
    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}
