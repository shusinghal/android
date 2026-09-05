package com.memorycurator.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatchPrediction
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.memorycurator.app.core.ai.CuratedResult
import com.memorycurator.app.core.ai.RejectionReason
import com.memorycurator.app.data.media.MediaIndexer
import com.memorycurator.app.data.media.MediaPhoto
import com.memorycurator.app.data.media.MediaRepository
import com.memorycurator.app.ui.theme.MemoryCuratorTheme

@Composable
fun AICurationScreen(
    photos: List<MediaPhoto>,
    onBack: () -> Unit,
    repository: MediaRepository,
    mediaIndexer: MediaIndexer
) {
    val viewModel: AICurationViewModel = viewModel(
        factory = AICurationViewModelFactory(repository, mediaIndexer)
    )
    val context = LocalContext.current
    
    LaunchedEffect(photos) {
        viewModel.setSessionPhotos(photos)
    }

    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    
    var selectedPhotoIndex by remember { mutableIntStateOf(-1) }
    var viewerSourceIsKeepers by remember { mutableStateOf(true) }
    val showAiLogic = true // Hardcoded for testing

    BackHandler(enabled = selectedPhotoIndex >= 0 || isSelectionMode) {
        if (selectedPhotoIndex >= 0) {
            selectedPhotoIndex = -1
        } else {
            viewModel.toggleSelectionMode(false)
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
            viewModel.archiveSelected()
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
            isSyncing = isSyncing,
            isSelectionMode = isSelectionMode,
            selectedCount = selectedIds.size,
            onDeleteSelected = {
                val uris = analysisResults
                    .filter { it.photo.id in selectedIds }
                    .map { it.photo.contentUri }
                requestTrash(uris)
            },
            onShareSelected = {
                val uris = analysisResults
                    .filter { it.photo.id in selectedIds }
                    .map { it.photo.contentUri }
                sharePhotos(context, uris)
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
                showAiLogic = showAiLogic,
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
                onToggleSection = { ids ->
                    viewModel.toggleSectionSelection(ids)
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
    showAiLogic: Boolean,
    onToggleBestTake: (Long) -> Unit,
    onPhotoClick: (Int, Boolean) -> Unit,
    onPhotoLongClick: (CuratedResult) -> Unit,
    isSelectionMode: Boolean,
    selectedIds: Set<Long>,
    onToggleSection: (List<Long>) -> Unit
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
                val sectionIds = remember(keepers) { keepers.map { it.photo.id } }
                val allSelected = remember(selectedIds, sectionIds) { sectionIds.isNotEmpty() && sectionIds.all { selectedIds.contains(it) } }
                
                SectionHeader(
                    title = "Keepers",
                    icon = Icons.Default.AutoAwesome,
                    action = {
                        if (isSelectionMode) {
                            IconButton(onClick = { onToggleSection(sectionIds) }) {
                                Icon(
                                    imageVector = if (allSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = "Select All Keepers",
                                    tint = if (allSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                )
            }
            itemsIndexed(keepers) { index, result ->
                CurationPhotoCard(
                    result = result, 
                    isKeeper = true, 
                    showAiLogic = showAiLogic,
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
                val sectionIds = remember(forReview) { forReview.map { it.photo.id } }
                val allSelected = remember(selectedIds, sectionIds) { sectionIds.isNotEmpty() && sectionIds.all { selectedIds.contains(it) } }
                
                SectionHeader(
                    title = "Review Suggestions",
                    icon = Icons.Default.BatchPrediction,
                    action = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isSelectionMode) {
                                IconButton(onClick = { onToggleSection(sectionIds) }) {
                                    Icon(
                                        imageVector = if (allSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = "Select All Suggestions",
                                        tint = if (allSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                )
            }
            itemsIndexed(forReview) { index, result ->
                CurationPhotoCard(
                    result = result, 
                    isKeeper = false, 
                    showAiLogic = showAiLogic,
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
    showAiLogic: Boolean,
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
            model = ImageRequest.Builder(LocalContext.current)
                .data(result.photo.contentUri)
                .setParameter("modified", result.photo.dateModified)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            error = androidx.compose.ui.graphics.painter.ColorPainter(Color.DarkGray)
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
            // Overlaying selection toggle/dismiss buttons at the top
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
            ) {
                // Top Right Action Button (Keep/Dismiss)
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(28.dp)
                        .clickable { onToggleBestTake(result.photo.id) },
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = if (isKeeper) Icons.Default.Close else Icons.Default.Check,
                        contentDescription = if (isKeeper) "Dismiss" else "Keep",
                        tint = Color.White,
                        modifier = Modifier
                            .padding(6.dp)
                            .fillMaxSize()
                    )
                }
            }

            // Bottom Floating Bar (Rejection Reason & AI Score)
            val hasDescription = result.aiDescription.isNotEmpty()
            if (result.score != -1f || (!isKeeper && result.rejectionReason != RejectionReason.NONE) || (showAiLogic && hasDescription)) {
                val isHigh = result.score >= 0.7f
                val softGreen = Color(0xFFA5D6A7)
                
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 6.dp, start = 6.dp, end = 6.dp)
                        .fillMaxWidth(),
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left side: Rejection Reason / "Keeper" label
                            Box(modifier = Modifier.weight(1f)) {
                                if (!isKeeper) {
                                    val reason = result.rejectionReason
                                    val label = when (reason) {
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
                                    if (label != null) {
                                        Text(
                                            text = label.uppercase(),
                                            color = Color.White.copy(alpha = 0.9f),
                                            fontSize = 7.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "KEEPER",
                                        color = softGreen,
                                        fontSize = 7.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }

                            // Right side: AI Score
                            if (result.score != -1f) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = if (isHigh) softGreen else Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.size(8.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "${(result.score * 100).toInt()}",
                                        color = if (isHigh) softGreen else Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }

                        // Bottom: AI Description (full width if present)
                        if (showAiLogic && hasDescription) {
                            HorizontalDivider(
                                color = Color.White.copy(alpha = 0.2f),
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                            Text(
                                text = result.aiDescription,
                                color = Color.White,
                                fontSize = 8.sp,
                                lineHeight = 9.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
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
    isSyncing: Boolean = false,
    isSelectionMode: Boolean,
    selectedCount: Int,
    onDeleteSelected: () -> Unit,
    onShareSelected: () -> Unit,
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
            IconButton(onClick = onShareSelected, enabled = selectedCount > 0) {
                Icon(
                    Icons.Default.Share, 
                    "Share",
                    tint = if (selectedCount > 0) Color.White else Color.White.copy(alpha = 0.4f)
                )
            }
            IconButton(onClick = onSelectAll) {
                Icon(Icons.Default.SelectAll, "Select All", tint = Color.White)
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
                    text = if (isSyncing) "Syncing gallery..." else if (isAnalyzing) "Processing..." else "AI Analysis Complete",
                    color = if (isSyncing) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f),
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
        MediaPhoto(1, Uri.EMPTY, System.currentTimeMillis(), System.currentTimeMillis()),
        MediaPhoto(2, Uri.EMPTY, System.currentTimeMillis(), System.currentTimeMillis()),
        MediaPhoto(3, Uri.EMPTY, System.currentTimeMillis(), System.currentTimeMillis())
    )

    MemoryCuratorTheme {
        AICurationScreen(
            photos = dummyPhotos,
            onBack = {},
            repository = object : MediaRepository {
                override fun getPagedPhotos(bestTakesOnly: Boolean) = error("Not implemented")
                override fun getAllPhotos() = error("Not implemented")
                override fun getArchivedPhotos() = kotlinx.coroutines.flow.flowOf(emptyList<MediaPhoto>())
                override suspend fun getMediaEntities(ids: List<Long>) = emptyList<com.memorycurator.app.data.local.MediaEntity>()
                override fun getMediaEntitiesFlow(ids: List<Long>) = kotlinx.coroutines.flow.flowOf(emptyList<com.memorycurator.app.data.local.MediaEntity>())
                override suspend fun saveAiResults(entities: List<com.memorycurator.app.data.local.MediaEntity>) {}
                override suspend fun updateBestTakeStatus(id: Long, isBest: Boolean) {}
                override suspend fun resetAiMetadata(ids: List<Long>) {}
                override suspend fun archiveMedia(ids: List<Long>) {}
                override suspend fun restoreMedia(ids: List<Long>) {}
                override suspend fun deleteMediaFromDb(ids: List<Long>) {}
            },
            mediaIndexer = MediaIndexer(LocalContext.current, object : com.memorycurator.app.data.local.MediaDao {
                override suspend fun insertAll(media: List<com.memorycurator.app.data.local.MediaEntity>) {}
                override suspend fun insertNewMedia(media: List<com.memorycurator.app.data.local.MediaEntity>) {}
                override fun getAllMedia() = error("Not implemented")
                override fun getPagedMedia() = error("Not implemented")
                override fun getPagedBestTakes() = error("Not implemented")
                override fun getArchivedMedia() = error("Not implemented")
                override fun getAlbums() = error("Not implemented")
                override fun getPhotosInAlbum(folderName: String) = error("Not implemented")
                override suspend fun getPhotosInAlbumSync(folderName: String) = error("Not implemented")
                override fun getMediaWithLocation() = error("Not implemented")
                override suspend fun getMediaWithLocationSync() = error("Not implemented")
                override suspend fun getMediaMissingLocation() = error("Not implemented")
                override suspend fun updateLocation(id: Long, lat: Double?, lon: Double?, name: String?) {}
                override suspend fun getMediaById(id: Long) = error("Not implemented")
                override suspend fun getMediaByIds(ids: List<Long>) = error("Not implemented")
                override fun getMediaByIdsFlow(ids: List<Long>) = kotlinx.coroutines.flow.flowOf(emptyList<com.memorycurator.app.data.local.MediaEntity>())
                override suspend fun updateMetadata(id: Long, isBest: Boolean, score: Float, reason: String?, dateModified: Long) {}
                override suspend fun updateFullMetadata(id: Long, isBest: Boolean, score: Float, reason: String?, clusterId: String?, originalFolder: String?, description: String?, faceCount: Int) {}
                override suspend fun resetAiMetadata(ids: List<Long>) {}
                override suspend fun updateArchiveStatus(id: Long, isArchived: Boolean) {}
                override suspend fun updateFolder(id: Long, folderName: String?, bucketId: String?) {}
                override suspend fun archiveMedia(ids: List<Long>) {}
                override suspend fun restoreMedia(ids: List<Long>) {}
                override suspend fun updateMediaUri(id: Long, newUri: String) {}
                override suspend fun delete(entity: com.memorycurator.app.data.local.MediaEntity) {}
                override suspend fun transferMetadata(oldId: Long, newId: Long, newUri: String, newFolder: String, originalFolder: String?) {}
                override suspend fun getAllMediaSync(): List<com.memorycurator.app.data.local.MediaEntity> = emptyList()
                override suspend fun getAllIds(): List<Long> = emptyList()
                override suspend fun getLastModifiedTimestamp(): Long? = 0L
                override suspend fun deleteById(id: Long) {}
                override suspend fun deleteByIds(ids: List<Long>) {}
                override suspend fun deleteMissingIds(scannedIds: Set<Long>) {}
                override suspend fun deleteByUri(uri: String) {}
                override suspend fun insertOrIgnorePreservingAI(media: List<com.memorycurator.app.data.local.MediaEntity>) {}
                override suspend fun insertOrUpdateSingle(entity: com.memorycurator.app.data.local.MediaEntity) {}
                override suspend fun updateSystemMetadata(
                    id: Long, uri: String, bucketId: String?, folderName: String?,
                    dateTaken: Long, dateModified: Long, mimeType: String?,
                    width: Int, height: Int, size: Long
                ) {}
            })
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
