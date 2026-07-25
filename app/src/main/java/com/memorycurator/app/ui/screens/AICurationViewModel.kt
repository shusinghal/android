package com.memorycurator.app.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.memorycurator.app.core.ai.CuratedResult
import com.memorycurator.app.core.ai.ImageCurator
import com.memorycurator.app.core.ai.ImageCuratorImpl
import com.memorycurator.app.core.ai.RejectionReason
import com.memorycurator.app.data.media.MediaPhoto
import com.memorycurator.app.data.media.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    private var allPhotosFromSession: List<MediaPhoto> = emptyList()

    fun setSessionPhotos(photos: List<MediaPhoto>) {
        allPhotosFromSession = photos
    }

    val archivedPhotos: StateFlow<List<MediaPhoto>> = repository.getArchivedPhotos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
        }
        _selectedIds.value = idsToSelect.toSet()
        _isSelectionMode.value = true
    }

    fun archiveSelected() {
        val idsToArchive = _selectedIds.value.toList()
        if (idsToArchive.isNotEmpty()) {
            archivePhotos(idsToArchive)
            toggleSelectionMode(false)
        }
    }

    fun filterBestTakes(context: Context, photos: List<MediaPhoto>) {
        allPhotosFromSession = photos
        viewModelScope.launch {
            _isAnalyzing.value = true
            
            // 1. Try to load from DB first
            val savedEntities = withContext(Dispatchers.IO) {
                repository.getMediaEntities(photos.map { it.id })
            }
            
            val needsAi = savedEntities.any { it.aiScore == -1f }
            
            if (!needsAi) {
                refreshResults()
                _isAnalyzing.value = false
                return@launch
            }

            // 2. Run AI Analysis
            val fullAnalysis = withContext(Dispatchers.Default) {
                imageCurator.analyzePhotos(context, photos) { current, total ->
                    _progress.value = CurationProgress(current, total, current.toFloat() / total)
                }
            }
            
            // 3. Save to DB
            withContext(Dispatchers.IO) {
                val updatedEntities = savedEntities.map { entity ->
                    val aiResult = fullAnalysis.find { it.photo.id == entity.id }
                    if (aiResult != null && !entity.isManuallyModified) {
                        entity.copy(
                            aiScore = aiResult.score,
                            isBestTake = aiResult.isBestTake,
                            rejectionReason = aiResult.rejectionReason.name,
                            clusterId = aiResult.clusterId
                        )
                    } else {
                        entity
                    }
                }
                repository.saveAiResults(updatedEntities)
            }
            
            refreshResults()
            _isAnalyzing.value = false
            _progress.value = null
        }
    }

    private suspend fun refreshResults() {
        if (allPhotosFromSession.isEmpty()) return
        val savedEntities = withContext(Dispatchers.IO) {
            repository.getMediaEntities(allPhotosFromSession.map { it.id })
        }
        
        _analysisResults.value = savedEntities.filter { !it.isArchived }.map { entity ->
            CuratedResult(
                photo = allPhotosFromSession.find { it.id == entity.id } ?: allPhotosFromSession.first(),
                score = entity.aiScore,
                isBestTake = entity.isBestTake,
                rejectionReason = RejectionReason.valueOf(entity.rejectionReason ?: "NONE"),
                clusterId = entity.clusterId
            )
        }
    }

    fun toggleBestTake(photoId: Long) {
        viewModelScope.launch {
            val currentList = _analysisResults.value.toMutableList()
            val index = currentList.indexOfFirst { it.photo.id == photoId }
            if (index != -1) {
                val item = currentList[index]
                val newState = !item.isBestTake
                currentList[index] = item.copy(isBestTake = newState)
                _analysisResults.value = currentList
                
                withContext(Dispatchers.IO) {
                    repository.updateBestTakeStatus(photoId, newState)
                }
            }
        }
    }

    fun resetCuration(photos: List<MediaPhoto>) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            withContext(Dispatchers.IO) {
                repository.resetAiMetadata(photos.map { it.id })
            }
            _analysisResults.value = emptySet<CuratedResult>().toList() // Trigger empty
            _isAnalyzing.value = false
        }
    }

    fun archiveAllUnderReview() {
        viewModelScope.launch {
            val toArchive = _analysisResults.value.filter { !it.isBestTake }.map { it.photo.id }
            if (toArchive.isNotEmpty()) {
                archivePhotos(toArchive)
            }
        }
    }

    fun archivePhoto(photoId: Long) {
        archivePhotos(listOf(photoId))
    }

    fun archivePhotos(photoIds: List<Long>) {
        viewModelScope.launch {
            if (photoIds.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    repository.archiveMedia(photoIds)
                }
                refreshResults()
            }
        }
    }

    fun restorePhotos(photoIds: List<Long>) {
        viewModelScope.launch {
            if (photoIds.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    repository.restoreMedia(photoIds)
                }
                refreshResults()
            }
        }
    }
}

class AICurationViewModelFactory(
    private val repository: MediaRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AICurationViewModel(repository) as T
    }
}
