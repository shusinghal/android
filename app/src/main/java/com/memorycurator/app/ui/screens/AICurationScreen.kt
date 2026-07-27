package com.memorycurator.app.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.memorycurator.app.core.ai.CuratedResult
import com.memorycurator.app.core.ai.RejectionReason
import com.memorycurator.app.data.media.MediaPhoto
import com.memorycurator.app.data.media.MediaRepository
import com.memorycurator.app.ui.theme.MemoryCuratorTheme

@Composable
fun AICurationScreen(
    photos: List<MediaPhoto>,
    onBack: () -> Unit,
    repository: MediaRepository
) {
    val viewModel: AICurationViewModel = viewModel(
        factory = AICurationViewModelFactory(repository)
    )
    val context = LocalContext.current
    
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    
    var selectedPhotoIndex by remember { mutableIntStateOf(-1) }
    var viewerSourceIsKeepers by remember { mutableStateOf(true) }

    BackHandler(enabled = selectedPhotoIndex >= 0 || isSelectionMode) {
        if (selectedPhotoIndex >= 0) {
            selectedPhotoIndex = -1
        } else {
            viewModel.toggleSelectionMode(false)
        }
    }

    LaunchedEffect(photos) {
        if (photos.isNotEmpty()) {
            viewModel.filterBestTakes(context, photos)
        }
    }
    
    val analysisResults by viewModel.analysisResults.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val progress by viewModel.progress.collectAsState()

    val keepers = remember(analysisResults) { analysisResults.filter { it.isBestTake } }
    val forReview = remember(analysisResults) { analysisResults.filter { !it.isBestTake } }
    
    val trashLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            onBack()
        }
    }

    fun requestTrash(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val pendingIntent = MediaStore.createTrashRequest(context.contentResolver, uris, true)
        trashLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        CurationHeader(
            onBack = onBack,
            isAnalyzing = isAnalyzing,
            isSelectionMode = isSelectionMode,
            selectedCount = selectedIds.size,
            onDeleteSelected = {
                val uris = analysisResults
                    .filter { it.photo.id in selectedIds }
                    .map { it.photo.contentUri }
                requestTrash(uris)
            },
            onSelectAll = { viewModel.selectAll() },
            onCancelSelection = { viewModel.toggleSelectionMode(false) }
        )

        if (isAnalyzing && progress != null) {
            AnalysisProgressView(progress!!)
        } else {
            CurationResultsGrid(
                keepers = keepers,
                forReview = forReview,
                onToggleBestTake = { viewModel.toggleBestTake(it) },
                onPhotoClick = { index, isKeeper ->
                    if (isSelectionMode) {
                        val result = if (isKeeper) keepers[index] else forReview[index]
                        viewModel.togglePhotoSelection(result.photo.id)
                    } else {
                        selectedPhotoIndex = index
                        viewerSourceIsKeepers = isKeeper
                    }
                },
                onPhotoLongClick = { result ->
                    viewModel.togglePhotoSelection(result.photo.id)
                },
                isSelectionMode = isSelectionMode,
                selectedIds = selectedIds,
                onDeleteSuggestions = {
                    requestTrash(forReview.map { it.photo.contentUri })
                }
            )
        }

        if (selectedPhotoIndex >= 0) {
            val viewerList = if (viewerSourceIsKeepers) keepers else forReview
            if (selectedPhotoIndex < viewerList.size) {
                CurationViewerScreen(
                    results = viewerList,
                    allResults = analysisResults,
                    initialIndex = selectedPhotoIndex,
                    onToggleAction = { id -> viewModel.toggleBestTake(id) },
                    onDismiss = { selectedPhotoIndex = -1 }
                )
            }
        }
    }
}

@Composable
fun CurationResultsGrid(
    keepers: List<CuratedResult>,
    forReview: List<CuratedResult>,
    onToggleBestTake: (Long) -> Unit,
    onPhotoClick: (Int, Boolean) -> Unit,
    onPhotoLongClick: (CuratedResult) -> Unit,
    isSelectionMode: Boolean,
    selectedIds: Set<Long>,
    onDeleteSuggestions: () -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(1.dp),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        if (keepers.isNotEmpty()) {
            item(span = { GridItemSpan(3) }) {
                SectionHeader("Keepers", Icons.Default.AutoAwesome)
            }
            itemsIndexed(keepers) { index, result ->
                CurationPhotoCard(
                    result = result, 
                    isKeeper = true, 
                    onToggleBestTake = onToggleBestTake,
                    onClick = { onPhotoClick(index, true) },
                    onLongClick = { onPhotoLongClick(result) },
                    isSelected = selectedIds.contains(result.photo.id),
                    isSelectionMode = isSelectionMode
                )
            }
        }

        if (forReview.isNotEmpty()) {
            item(span = { GridItemSpan(3) }) {
                SectionHeader(
                    title = "Review Suggestions",
                    icon = Icons.Default.BatchPrediction,
                    action = {
                        if (!isSelectionMode) {
                            TextButton(onClick = onDeleteSuggestions) {
                                Icon(Icons.Default.DeleteSweep, null, tint = Color.Red, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Clean Up", color = Color.Red, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                )
            }
            itemsIndexed(forReview) { index, result ->
                CurationPhotoCard(
                    result = result, 
                    isKeeper = false, 
                    onToggleBestTake = onToggleBestTake,
                    onClick = { onPhotoClick(index, false) },
                    onLongClick = { onPhotoLongClick(result) },
                    isSelected = selectedIds.contains(result.photo.id),
                    isSelectionMode = isSelectionMode
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CurationPhotoCard(
    result: CuratedResult, 
    isKeeper: Boolean, 
    onToggleBestTake: (Long) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    isSelected: Boolean,
    isSelectionMode: Boolean
) {
    val padding by animateDpAsState(if (isSelected) 10.dp else 0.dp)
    val cornerRadius by animateDpAsState(if (isSelected) 16.dp else 0.dp)
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .padding(padding)
            .clip(RoundedCornerShape(cornerRadius))
    ) {
        AsyncImage(
            model = result.photo.contentUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        if (isSelectionMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .size(24.dp)
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.3f), CircleShape)
                    .border(2.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(28.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .clickable { onToggleBestTake(result.photo.id) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isKeeper) Icons.Default.Close else Icons.Default.Check,
                    contentDescription = if (isKeeper) "Dismiss" else "Keep",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }

            if (!isKeeper && result.rejectionReason != RejectionReason.NONE) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = when (result.rejectionReason) {
                                RejectionReason.EYES_CLOSED -> Icons.Default.VisibilityOff
                                RejectionReason.DUPLICATE -> Icons.Default.CopyAll
                                RejectionReason.BLURRY -> Icons.Default.BlurOn
                                else -> Icons.Default.ErrorOutline
                            },
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = result.rejectionReason.name.lowercase().replaceFirstChar { it.uppercase() },
                            color = Color.White,
                            fontSize = 8.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CurationHeader(
    onBack: () -> Unit,
    isAnalyzing: Boolean,
    isSelectionMode: Boolean,
    selectedCount: Int,
    onDeleteSelected: () -> Unit,
    onSelectAll: () -> Unit,
    onCancelSelection: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode) {
            IconButton(onClick = onCancelSelection) {
                Icon(Icons.Default.Close, "Cancel", tint = Color.White)
            }
            Text(
                "$selectedCount Selected",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onSelectAll) {
                Text("Select All", color = Color.White)
            }
            IconButton(onClick = onDeleteSelected, enabled = selectedCount > 0) {
                Icon(
                    Icons.Default.Delete, 
                    "Delete", 
                    tint = if (selectedCount > 0) Color.White else Color.White.copy(alpha = 0.4f)
                )
            }
        } else {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text("Smart Review", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = if (isAnalyzing) "Processing..." else "AI Analysis Complete",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun AnalysisProgressView(progress: CurationProgress) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            LinearProgressIndicator(
                progress = { progress.percentage },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.1f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Analyzing ${progress.current} of ${progress.total} photos",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                "Detecting faces, smiles, and duplicates...",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun SectionHeader(
    title: String, 
    icon: ImageVector,
    action: @Composable () -> Unit = {}
) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        action()
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun AICurationScreenPreview() {
    val dummyPhotos = listOf(
        MediaPhoto(1, Uri.EMPTY, System.currentTimeMillis()),
        MediaPhoto(2, Uri.EMPTY, System.currentTimeMillis()),
        MediaPhoto(3, Uri.EMPTY, System.currentTimeMillis())
    )

    MemoryCuratorTheme {
        AICurationScreen(
            photos = dummyPhotos,
            onBack = {},
            repository = object : MediaRepository {
                override fun getPagedPhotos() = error("Not implemented")
                override fun getAllPhotos() = error("Not implemented")
                override fun getArchivedPhotos() = kotlinx.coroutines.flow.flowOf(emptyList<MediaPhoto>())
                override suspend fun getMediaEntities(ids: List<Long>) = emptyList<com.memorycurator.app.data.local.MediaEntity>()
                override suspend fun saveAiResults(entities: List<com.memorycurator.app.data.local.MediaEntity>) {}
                override suspend fun updateBestTakeStatus(id: Long, isBest: Boolean) {}
                override suspend fun resetAiMetadata(ids: List<Long>) {}
                override suspend fun archiveMedia(ids: List<Long>) {}
                override suspend fun restoreMedia(ids: List<Long>) {}
            }
        )
    }
}

@Preview
@Composable
fun AnalysisProgressPreview() {
    MemoryCuratorTheme {
        AnalysisProgressView(CurationProgress(5, 10, 0.5f))
    }
}
