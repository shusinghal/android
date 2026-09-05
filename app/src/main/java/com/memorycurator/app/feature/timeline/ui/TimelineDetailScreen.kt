package com.memorycurator.app.feature.timeline.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatchPrediction
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
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
import com.memorycurator.app.ui.preview.PreviewStockPhotos
import com.memorycurator.app.ui.screens.AICurationViewModel
import com.memorycurator.app.ui.screens.AICurationViewModelFactory
import com.memorycurator.app.ui.screens.AnalysisProgressView
import com.memorycurator.app.ui.screens.CurationViewerScreen
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
    var viewerSourceList by remember { mutableStateOf<List<CuratedResult>>(emptyList()) }

    val viewModel: AICurationViewModel? = if (repository != null && mediaIndexer != null) {
        viewModel(
            key = "curation_${group.title}",
            factory = AICurationViewModelFactory(repository, mediaIndexer)
        )
    } else null

    val context = LocalContext.current
    val analysisResults by viewModel?.analysisResults?.collectAsState() ?: remember { mutableStateOf(emptyList()) }
    val isAnalyzing by viewModel?.isAnalyzing?.collectAsState() ?: remember { mutableStateOf(false) }
    val isSyncing by viewModel?.isSyncing?.collectAsState() ?: remember { mutableStateOf(false) }
    val progress by viewModel?.progress?.collectAsState() ?: remember { mutableStateOf(null) }

    val isSelectionMode by viewModel?.isSelectionMode?.collectAsState() ?: remember { mutableStateOf(false) }
    val selectedIds by viewModel?.selectedIds?.collectAsState() ?: remember { mutableStateOf(emptySet()) }

    // Derived Category Slices
    val keepers = remember(analysisResults) { analysisResults.filter { it.score != -1f && it.isBestTake } }
    val forReview = remember(analysisResults) { analysisResults.filter { it.score != -1f && !it.isBestTake } }
    val notAnalyzed = remember(analysisResults) { analysisResults.filter { it.score == -1f } }

    // Filter out photos that might have been deleted but index hasn't caught up
    val activePhotos = group.photos

    // Keep viewerSourceList in sync with latest analysis results without changing structure
    LaunchedEffect(analysisResults) {
        if (selectedIndex >= 0 && viewerSourceList.isNotEmpty()) {
            val latestMap = analysisResults.associateBy { it.photo.id }
            viewerSourceList = viewerSourceList.map { item ->
                latestMap[item.photo.id] ?: item
            }
        }
    }

    val gridState = rememberLazyGridState()

    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel?.deleteSelected()
        }
    }

    fun requestTrash(uris: List<Uri>) {
        if (uris.isEmpty()) return
        try {
            val pendingIntent = MediaStore.createTrashRequest(context.contentResolver, uris, true)
            deleteLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    LaunchedEffect(group.photos) {
        viewModel?.setSessionPhotos(group.photos)
    }

    LaunchedEffect(analysisResults) {
        if (analysisResults.any { it.score != -1f }) {
            isBestTakesActive = true
        }
    }

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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    text = if (isSyncing) "Syncing metadata..." else "${activePhotos.size} photos",
                                    color = if (isSyncing) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f),
                                    fontSize = 12.sp
                                )
                            }
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
                                    val uris = activePhotos
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
                                    val uris = activePhotos
                                        .filter { it.id in selectedIds }
                                        .map { it.contentUri }
                                    requestTrash(uris)
                                },
                                enabled = selectedIds.isNotEmpty()
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    "Delete",
                                    tint = if (selectedIds.isNotEmpty()) Color.White else Color.White.copy(alpha = 0.4f)
                                )
                            }
                        } else if (isBestTakesActive && analysisResults.isNotEmpty()) {
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
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "analysis_transition"
            ) { analyzing ->
                if (analyzing) {
                    progress?.let { currentProgress ->
                        Box(
                            modifier = Modifier
                                .padding(padding)
                                .fillMaxSize()
                        ) {
                            AnalysisProgressView(currentProgress)
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = gridState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentPadding = PaddingValues(1.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                        horizontalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        if (isBestTakesActive && (keepers.isNotEmpty() || forReview.isNotEmpty() || notAnalyzed.isNotEmpty())) {
                            // Section 1: Keepers
                            if (keepers.isNotEmpty()) {
                                item(span = { GridItemSpan(3) }, key = "keepers_header") {
                                    val sectionIds = remember(keepers) { keepers.map { it.photo.id } }
                                    val allSelected = remember(selectedIds, sectionIds) {
                                        sectionIds.isNotEmpty() && sectionIds.all { selectedIds.contains(it) }
                                    }
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
                                        score = result.score,
                                        rejectionReason = result.rejectionReason,
                                        isBestTake = result.isBestTake,
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

                            // Section 2: For Review
                            if (forReview.isNotEmpty()) {
                                item(span = { GridItemSpan(3) }, key = "review_header") {
                                    val sectionIds = remember(forReview) { forReview.map { it.photo.id } }
                                    val allSelected = remember(selectedIds, sectionIds) {
                                        sectionIds.isNotEmpty() && sectionIds.all { selectedIds.contains(it) }
                                    }
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
                                        score = result.score,
                                        rejectionReason = result.rejectionReason,
                                        isBestTake = result.isBestTake,
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

                            // Section 3: Yet to Review / Unanalyzed
                            if (notAnalyzed.isNotEmpty()) {
                                item(span = { GridItemSpan(3) }, key = "not_analyzed_header") {
                                    val sectionIds = remember(notAnalyzed) { notAnalyzed.map { it.photo.id } }
                                    val unanalyzedPhotos = remember(notAnalyzed) { notAnalyzed.map { it.photo } }
                                    val allSelected = remember(selectedIds, sectionIds) {
                                        sectionIds.isNotEmpty() && sectionIds.all { selectedIds.contains(it) }
                                    }
                                    SectionHeaderSmall(
                                        title = "Yet to Review",
                                        icon = Icons.Default.HelpOutline,
                                        action = {
                                            if (isSelectionMode) {
                                                IconButton(onClick = { viewModel?.toggleSectionSelection(sectionIds) }) {
                                                    Icon(
                                                        imageVector = if (allSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                                        contentDescription = "Select All",
                                                        tint = if (allSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f)
                                                    )
                                                }
                                            } else {
                                                Surface(
                                                    onClick = {
                                                        viewModel?.filterBestTakes(context, unanalyzedPhotos)
                                                    },
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.AutoAwesome,
                                                            contentDescription = "Best Takes",
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                        Spacer(Modifier.width(4.dp))
                                                        Text(
                                                            text = "Best Takes",
                                                            color = MaterialTheme.colorScheme.primary,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                                itemsIndexed(notAnalyzed, key = { _, result -> result.photo.id }) { index, result ->
                                    PhotoGridItem(
                                        photo = result.photo,
                                        score = result.score,
                                        rejectionReason = result.rejectionReason,
                                        isBestTake = result.isBestTake,
                                        isSelected = selectedIds.contains(result.photo.id),
                                        isSelectionMode = isSelectionMode,
                                        modifier = Modifier.animateItem(),
                                        onClick = {
                                            if (isSelectionMode) {
                                                viewModel?.togglePhotoSelection(result.photo.id)
                                            } else {
                                                viewerSourceList = notAnalyzed
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
                            // Default Grid View (Standard non-curated view)
                            // Optimization: Use the reactive analysisResults to ensure Pass 2 updates appear
                            itemsIndexed(analysisResults, key = { _, result -> result.photo.id }) { index, result ->
                                PhotoGridItem(
                                    photo = result.photo,
                                    score = result.score,
                                    rejectionReason = result.rejectionReason,
                                    isBestTake = result.isBestTake,
                                    isSelected = selectedIds.contains(result.photo.id),
                                    isSelectionMode = isSelectionMode,
                                    modifier = Modifier.animateItem(),
                                    onClick = {
                                        if (isSelectionMode) {
                                            viewModel?.togglePhotoSelection(result.photo.id)
                                        } else {
                                            viewerSourceList = analysisResults
                                            selectedIndex = index
                                        }
                                    },
                                    onLongClick = {
                                        viewModel?.togglePhotoSelection(result.photo.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Full Screen Large Photo/Video Viewer (Always use Curation Mode)
        if (selectedIndex >= 0) {
            CurationViewerScreen(
                results = viewerSourceList,
                allResults = if (analysisResults.isNotEmpty()) analysisResults else viewerSourceList,
                initialIndex = selectedIndex,
                onToggleAction = { id -> viewModel?.toggleBestTake(id) },
                onDismiss = { selectedIndex = -1 }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoGridItem(
    photo: MediaPhoto,
    score: Float = -1f,
    rejectionReason: RejectionReason? = null,
    isBestTake: Boolean = false,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val imageRequest = remember(photo.contentUri, photo.dateModified) {
        ImageRequest.Builder(context)
            .data(photo.contentUri)
            .setParameter("modified", photo.dateModified)
            .crossfade(true)
            .build()
    }

    val label = remember(rejectionReason, isBestTake, score) {
        if (isBestTake || score == -1f || rejectionReason == null || rejectionReason == RejectionReason.NONE) null else when (rejectionReason) {
            RejectionReason.DUPLICATE -> "Similar"
            RejectionReason.BLURRY -> "Hazy"
            RejectionReason.EYES_CLOSED -> "Blinked"
            RejectionReason.BAD_EXPRESSION -> "Awkward"
            RejectionReason.POOR_LIGHTING -> "Darkish"
            RejectionReason.POOR_COMPOSITION -> "Framing"
            RejectionReason.LOW_QUALITY -> "Subpar"
            RejectionReason.MANUAL -> "Your choice"
            RejectionReason.UTILITY -> "Utility"
            RejectionReason.NO_SUBJECT -> "Unclear"
            RejectionReason.NONE -> null
        }
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            error = ColorPainter(Color.DarkGray)
        )

        // Footer Info Bar (AI Score & Reason)
        if (score != -1f || label != null) {
            val isHigh = score >= 0.7f
            val softGreen = Color(0xFFA5D6A7)
            
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
                            text = label.uppercase(java.util.Locale.getDefault()),
                            color = Color.White,
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        if (score != -1f) {
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                    }

                    if (score != -1f) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = if (isHigh) softGreen else Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(8.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "${(score * 100).toInt()}",
                                color = if (isHigh) softGreen else Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
        }

        // Best Take Badge (Top Left)
        if (isBestTake) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(20.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
        }

        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (isSelected) Color.White.copy(alpha = 0.2f) else Color.Transparent)
            )
            // Move selection checkmark to Top End to avoid collision with score in Bottom End
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
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