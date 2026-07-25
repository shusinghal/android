package com.memorycurator.app.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.memorycurator.app.core.ai.CuratedResult
import com.memorycurator.app.core.ai.RejectionReason
import com.memorycurator.app.data.media.MediaPhoto
import com.memorycurator.app.data.media.MediaRepository
import com.memorycurator.app.ui.components.PhotoCard
import com.memorycurator.app.ui.models.PhotoItem

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
    
    var selectedPhotoIndex by remember { mutableIntStateOf(-1) }
    var viewerSourceIsKeepers by remember { mutableStateOf(true) }

    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }

    BackHandler(enabled = selectedPhotoIndex >= 0 || isSelectionMode) {
        if (selectedPhotoIndex >= 0) {
            selectedPhotoIndex = -1
        } else {
            isSelectionMode = false
            selectedIds = emptySet()
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
    val archivedPhotos by viewModel.archivedPhotos.collectAsState()

    val keepers = remember(analysisResults) { analysisResults.filter { it.isBestTake } }
    val forReview = remember(analysisResults) { analysisResults.filter { !it.isBestTake } }
    
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val trashLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            onBack()
        }
    }

    fun requestTrash(photos: List<Uri>) {
        if (photos.isEmpty()) return
        val pendingIntent = MediaStore.createTrashRequest(context.contentResolver, photos, true)
        trashLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Move to Trash?") },
            text = { Text("You are about to move ${forReview.size} photos to the system trash. They can be recovered within 30 days.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    requestTrash(forReview.map { it.photo.contentUri })
                }) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        CurationHeader(
            onBack = onBack,
            isAnalyzing = isAnalyzing,
            resultCount = analysisResults.size,
            onDeleteRejected = { showDeleteConfirm = true },
            isSelectionMode = isSelectionMode,
            selectedCount = selectedIds.size,
            onArchiveSelected = {
                viewModel.archivePhotos(selectedIds.toList())
                isSelectionMode = false
                selectedIds = emptySet()
            },
            onSelectAll = {
                selectedIds = analysisResults.map { it.photo.id }.toSet()
            },
            onCancelSelection = {
                isSelectionMode = false
                selectedIds = emptySet()
            }
        )

        if (isAnalyzing && progress != null) {
            AnalysisProgressView(progress!!)
        } else {
            CurationResultsGrid(
                keepers = keepers,
                forReview = forReview,
                archivedCount = archivedPhotos.size,
                onRestoreArchived = {
                    viewModel.restorePhotos(archivedPhotos.map { it.id })
                },
                onToggleBestTake = { viewModel.toggleBestTake(it) },
                onPhotoClick = { index, isKeeper ->
                    if (isSelectionMode) {
                        val result = if (isKeeper) keepers[index] else forReview[index]
                        selectedIds = if (selectedIds.contains(result.photo.id)) {
                            selectedIds - result.photo.id
                        } else {
                            selectedIds + result.photo.id
                        }
                    } else {
                        selectedPhotoIndex = index
                        viewerSourceIsKeepers = isKeeper
                    }
                },
                onPhotoLongClick = { result ->
                    if (!isSelectionMode) {
                        isSelectionMode = true
                        selectedIds = setOf(result.photo.id)
                    }
                },
                isSelectionMode = isSelectionMode,
                selectedIds = selectedIds
            )
        }

        if (selectedPhotoIndex >= 0) {
            val viewerList = if (viewerSourceIsKeepers) keepers else forReview
            if (selectedPhotoIndex < viewerList.size) {
                CurationViewerScreen(
                    results = viewerList,
                    allResults = analysisResults,
                    initialIndex = selectedPhotoIndex,
                    onToggleAction = { id -> 
                        viewModel.toggleBestTake(id)
                    },
                    onDismiss = { selectedPhotoIndex = -1 }
                )
            } else {
                selectedPhotoIndex = -1
            }
        }
    }
}

@Composable
fun CurationResultsGrid(
    keepers: List<CuratedResult>,
    forReview: List<CuratedResult>,
    archivedCount: Int,
    onRestoreArchived: () -> Unit,
    onToggleBestTake: (Long) -> Unit,
    onPhotoClick: (Int, Boolean) -> Unit,
    onPhotoLongClick: (CuratedResult) -> Unit,
    isSelectionMode: Boolean,
    selectedIds: Set<Long>
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(1.dp),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        if (archivedCount > 0) {
            item(span = { GridItemSpan(3) }) {
                ArchiveHeader(count = archivedCount, onUndo = onRestoreArchived)
            }
        }

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
                SectionHeader("For Review", Icons.Default.BatchPrediction)
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
    isSelectionMode: Boolean,
    compact: Boolean = false
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
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
        } else {
            // Toggle Button (Add/Remove from Best Takes)
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .size(24.dp)
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    .clickable { onToggleBestTake(result.photo.id) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isKeeper) Icons.Default.Favorite else Icons.Default.Add,
                    contentDescription = if (isKeeper) "Remove" else "Add",
                    tint = if (isKeeper) Color.Red else Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }

            // Overlay for Rejection Reason
            if (!isKeeper && result.rejectionReason != RejectionReason.NONE) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = when (result.rejectionReason) {
                            RejectionReason.EYES_CLOSED -> Icons.Default.VisibilityOff
                            RejectionReason.DUPLICATE -> Icons.Default.CopyAll
                            RejectionReason.BLURRY -> Icons.Default.BlurOn
                            else -> Icons.Default.ErrorOutline
                        },
                        contentDescription = result.rejectionReason.name,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ArchiveHeader(count: Int, onUndo: () -> Unit) {
    Surface(
        color = Color.DarkGray.copy(alpha = 0.4f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Archive, "Archive", tint = Color.LightGray)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Archive", fontWeight = FontWeight.Bold, color = Color.White)
                Text("$count images", fontSize = 12.sp, color = Color.Gray)
            }
            TextButton(onClick = onUndo) {
                Text("UNDO", fontWeight = FontWeight.Bold, color = Color.Yellow)
            }
        }
    }
}

@Composable
fun CurationHeader(
    onBack: () -> Unit,
    isAnalyzing: Boolean,
    resultCount: Int,
    onDeleteRejected: () -> Unit,
    isSelectionMode: Boolean,
    selectedCount: Int,
    onArchiveSelected: () -> Unit,
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
            IconButton(onClick = onArchiveSelected, enabled = selectedCount > 0) {
                Icon(
                    Icons.Default.Archive, 
                    "Archive", 
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
                    text = if (isAnalyzing) "Processing..." else "$resultCount photos analyzed",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }

            if (!isAnalyzing && resultCount > 0) {
                Button(
                    onClick = onDeleteRejected,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.DeleteSweep, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Clean Up", fontSize = 12.sp)
                }
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
fun SectionHeader(title: String, icon: ImageVector) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}
