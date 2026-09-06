package com.memorycurator.app.ui.screens

import android.app.RecoverableSecurityException
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.memorycurator.app.core.ai.CuratedResult
import com.memorycurator.app.core.ai.ImageCurator
import com.memorycurator.app.core.ai.ImageCuratorImpl
import com.memorycurator.app.core.ai.RejectionReason
import com.memorycurator.app.data.local.UserPreferences
import com.memorycurator.app.data.media.MediaIndexer
import com.memorycurator.app.data.media.MediaPhoto
import com.memorycurator.app.data.media.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CurationProgress(
    val current: Int,
    val total: Int,
    val percentage: Float
)

class AICurationViewModel(
    private val repository: MediaRepository,
    private val mediaIndexer: MediaIndexer,
    private val imageCurator: ImageCurator = ImageCuratorImpl()
) : ViewModel() {

    private val _analysisResults = MutableStateFlow<List<CuratedResult>>(emptyList())
    val analysisResults: StateFlow<List<CuratedResult>> = _analysisResults

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing

    private val _progress = MutableStateFlow<CurationProgress?>(null)
    val progress: StateFlow<CurationProgress?> = _progress

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing

    private val _permissionRequest = MutableStateFlow<IntentSenderRequest?>(null)
    val permissionRequest: StateFlow<IntentSenderRequest?> = _permissionRequest

    private val _syncStatus = MutableStateFlow<String?>(null)
    val syncStatus: StateFlow<String?> = _syncStatus

    private val _isDirty = MutableStateFlow(false)
    val isDirty: StateFlow<Boolean> = _isDirty

    private var allPhotosFromSession: List<MediaPhoto> = emptyList()
    private var observationJob: Job? = null

    fun setSessionPhotos(photos: List<MediaPhoto>) {
        allPhotosFromSession = photos
        observationJob?.cancel()
        observationJob = viewModelScope.launch {
            // Priority Restore: Check for EXIF metadata immediately for this session
            try {
                _isSyncing.value = true
                mediaIndexer.restoreMetadataForIds(photos.map { it.id })
                // After restoration, what's in DB matches what's in EXIF
                _isDirty.value = false
            } finally {
                _isSyncing.value = false
            }

            repository.getMediaEntitiesFlow(photos.map { it.id }).collect { entities ->
                _analysisResults.value = entities
                    .filter { !it.isArchived }
                    .mapNotNull { entity ->
                        val photo = allPhotosFromSession.find { it.id == entity.id }
                        if (photo != null) {
                            CuratedResult(
                                photo = photo.copy(dateModified = entity.dateModified), // Sync modified date
                                score = entity.aiScore,
                                isBestTake = entity.isBestTake,
                                rejectionReason = runCatching { 
                                    RejectionReason.valueOf(entity.rejectionReason ?: "NONE")
                                }.getOrDefault(RejectionReason.NONE),
                                clusterId = entity.clusterId,
                                aiDescription = entity.aiDescription ?: "",
                                mainFaceCount = entity.mainFaceCount
                            )
                        } else null
                    }
            }
        }
    }

    fun toggleSelectionMode(enabled: Boolean) {
        _isSelectionMode.value = enabled
        if (!enabled) {
            _selectedIds.value = emptySet()
        }
    }

    fun togglePhotoSelection(photoId: Long) {
        val current = _selectedIds.value
        if (current.contains(photoId)) {
            _selectedIds.value = current - photoId
            if (_selectedIds.value.isEmpty()) {
                _isSelectionMode.value = false
            }
        } else {
            if (!_isSelectionMode.value) {
                _isSelectionMode.value = true
            }
            _selectedIds.value = current + photoId
        }
    }

    fun selectAll() {
        // Select from analysis results if active, otherwise from all photos in session
        val idsToSelect = if (_analysisResults.value.isNotEmpty()) {
            _analysisResults.value.map { it.photo.id }
        } else {
            allPhotosFromSession.map { it.id }
        }.toSet()

        if (_selectedIds.value.size == idsToSelect.size) {
            _selectedIds.value = emptySet()
            _isSelectionMode.value = false
        } else {
            _selectedIds.value = idsToSelect
            _isSelectionMode.value = true
        }
    }

    fun selectSectionIds(ids: List<Long>) {
        val current = _selectedIds.value
        _selectedIds.value = current + ids.toSet()
        _isSelectionMode.value = true
    }

    fun toggleSectionSelection(ids: List<Long>) {
        val current = _selectedIds.value
        val allInSectionSelected = ids.all { current.contains(it) }

        if (allInSectionSelected) {
            _selectedIds.value = current - ids.toSet()
        } else {
            _selectedIds.value = current + ids.toSet()
            _isSelectionMode.value = true
        }

        if (_selectedIds.value.isEmpty()) {
            _isSelectionMode.value = false
        }
    }

    fun deleteSelected() {
        val idsToDelete = _selectedIds.value.toList()
        if (idsToDelete.isNotEmpty()) {
            deletePhotos(idsToDelete)
            toggleSelectionMode(false)
        }
    }

    fun archiveSelected() {
        val idsToArchive = _selectedIds.value.toList()
        if (idsToArchive.isNotEmpty()) {
            viewModelScope.launch {
                _isSyncing.value = true
                withContext(Dispatchers.IO) {
                    repository.archiveMedia(idsToArchive)
                }
                // Small delay to allow UI to feel responsive
                delay(300)
                toggleSelectionMode(false)
                _isSyncing.value = false
            }
        }
    }

    fun filterBestTakes(context: Context, photos: List<MediaPhoto>) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            
            // 1. Try to load from DB first
            val savedEntities = withContext(Dispatchers.IO) {
                repository.getMediaEntities(photos.map { it.id })
            }
            
            // Populate current state from DB so we don't start from an empty grid
            _analysisResults.value = savedEntities.mapNotNull { entity ->
                val photo = allPhotosFromSession.find { it.id == entity.id }
                if (photo != null) {
                    CuratedResult(
                        photo = photo.copy(dateModified = entity.dateModified),
                        score = entity.aiScore,
                        isBestTake = entity.isBestTake,
                        rejectionReason = runCatching { 
                            RejectionReason.valueOf(entity.rejectionReason ?: "NONE")
                        }.getOrDefault(RejectionReason.NONE),
                        clusterId = entity.clusterId,
                        aiDescription = entity.aiDescription ?: "",
                        mainFaceCount = entity.mainFaceCount
                    )
                } else null
            }
            
            val needsAi = savedEntities.any { it.aiScore == -1f }
            
            if (!needsAi) {
                _isAnalyzing.value = false
                return@launch
            }

            // 2. Run AI Analysis with real-time updates
            val isDeep = UserPreferences(context).isDeepAnalysisEnabled
            val fullAnalysis = withContext(Dispatchers.Default) {
                imageCurator.analyzePhotos(
                    context = context, 
                    photos = photos,
                    isDeepAnalysis = isDeep,
                    onProgress = { current, total ->
                        _progress.value = CurationProgress(current, total, current.toFloat() / total)
                    },
                    onResult = { partialResult ->
                        // Update UI immediately with the newly analyzed result
                        val currentResults = _analysisResults.value.toMutableList()
                        val existingIndex = currentResults.indexOfFirst { it.photo.id == partialResult.photo.id }
                        if (existingIndex != -1) {
                            currentResults[existingIndex] = partialResult
                        } else {
                            currentResults.add(partialResult)
                        }
                        _analysisResults.value = currentResults
                    }
                )
            }
            
            // 3. Save to DB and EXIF
            withContext(Dispatchers.IO) {
                val updatedEntities = savedEntities.map { entity ->
                    val aiResult = fullAnalysis.find { it.photo.id == entity.id }
                    if (aiResult != null && !entity.isManuallyModified) {
                        entity.copy(
                            aiScore = aiResult.score,
                            isBestTake = aiResult.isBestTake,
                            rejectionReason = aiResult.rejectionReason.name,
                            clusterId = aiResult.clusterId,
                            aiDescription = aiResult.aiDescription,
                            mainFaceCount = aiResult.mainFaceCount
                        )
                    } else {
                        entity
                    }
                }
                val errors = repository.saveAiResults(updatedEntities)
                // We don't pass context here because filterBestTakes is often automatic.
                // If it fails, users can use the "Save to Files" button to trigger batch permission.
                handleSecurityExceptions(null, MediaRepository.SyncResult(0, emptyList(), errors))
                
                // If analysis finished, we are "dirty" until manual Save
                _isDirty.value = true
            }
            
            _isAnalyzing.value = false
            _progress.value = null
        }
    }

    fun toggleBestTake(photoId: Long) {
        viewModelScope.launch {
            val currentList = _analysisResults.value.toMutableList()
            val index = currentList.indexOfFirst { it.photo.id == photoId }
            if (index != -1) {
                val item = currentList[index]
                val newState = !item.isBestTake

                // If user is unchecking a best take, mark it as MANUAL ("Your choice")
                // If it was un-analyzed (-1f), mark as MANUAL as well.
                val newScore = if (item.score == -1f) 1.0f else item.score
                val newReason = if (!newState || item.score == -1f) {
                    RejectionReason.MANUAL 
                } else {
                    item.rejectionReason
                }

                currentList[index] = item.copy(
                    isBestTake = newState,
                    score = newScore,
                    rejectionReason = newReason
                )
                _analysisResults.value = currentList

                withContext(Dispatchers.IO) {
                    val error = repository.updateBestTakeStatus(photoId, newState)
                    if (error != null) {
                        handleSecurityExceptions(null, MediaRepository.SyncResult(0, listOf(item.photo.contentUri), listOf(error)))
                    }
                    // Mark as dirty when user manually changes a rating
                    _isDirty.value = true
                }
            }
        }
    }

    private fun handleSecurityExceptions(context: Context?, result: MediaRepository.SyncResult) {
        val errors = result.securityExceptions
        if (errors.isEmpty()) return

        val securityExceptions = errors.filterIsInstance<RecoverableSecurityException>()
        if (securityExceptions.isNotEmpty()) {
            if (context != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && result.failedUris.size > 1) {
                // Batch request for Android 11+
                val pendingIntent = MediaStore.createWriteRequest(
                    context.contentResolver, 
                    result.failedUris
                )
                _permissionRequest.value = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
            } else {
                // Fallback to individual request (Standard Android 10 or single file)
                val first = securityExceptions.first()
                _permissionRequest.value = IntentSenderRequest.Builder(first.userAction.actionIntent.intentSender).build()
            }
        }
    }

    fun consumePermissionRequest() {
        _permissionRequest.value = null
    }

    fun syncAllToExif(context: Context) {
        viewModelScope.launch {
            val ids = _analysisResults.value.map { it.photo.id }
            if (ids.isNotEmpty()) {
                _isSyncing.value = true
                withContext(Dispatchers.IO) {
                    val result = repository.syncToExif(ids)
                    handleSecurityExceptions(context, result)
                    if (result.successCount > 0) {
                        _syncStatus.value = "Saved ${result.successCount} photos to files"
                    } else if (result.failedUris.isEmpty()) {
                        _syncStatus.value = "All photos already synced"
                    }
                    
                    // If no failures, we are no longer dirty
                    if (result.failedUris.isEmpty()) {
                        _isDirty.value = false
                    }
                }
                _isSyncing.value = false
            }
        }
    }

    fun consumeSyncStatus() {
        _syncStatus.value = null
    }

    fun resetCuration(photos: List<MediaPhoto>) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            withContext(Dispatchers.IO) {
                repository.resetAiMetadata(photos.map { it.id })
            }
            _isDirty.value = false
            _analysisResults.value = emptySet<CuratedResult>().toList() // Trigger empty
            _isAnalyzing.value = false
        }
    }

    fun deleteAllUnderReview() {
        viewModelScope.launch {
            val toDelete = _analysisResults.value.filter { !it.isBestTake }.map { it.photo.id }
            if (toDelete.isNotEmpty()) {
                deletePhotos(toDelete)
            }
        }
    }

    fun deletePhoto(photoId: Long) {
        deletePhotos(listOf(photoId))
    }

    fun deletePhotos(photoIds: List<Long>) {
        viewModelScope.launch {
            if (photoIds.isNotEmpty()) {
                _isSyncing.value = true
                withContext(Dispatchers.IO) {
                    repository.deleteMediaFromDb(photoIds)
                }
                // Small delay to allow MediaStore to update its index
                delay(500)
                mediaIndexer.indexMedia()
                _isSyncing.value = false
            }
        }
    }
}

class AICurationViewModelFactory(
    private val repository: MediaRepository,
    private val mediaIndexer: MediaIndexer
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AICurationViewModel(repository, mediaIndexer) as T
    }
}
