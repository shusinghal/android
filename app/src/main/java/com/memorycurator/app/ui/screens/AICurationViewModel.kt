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
import kotlinx.coroutines.flow.StateFlow
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

    fun filterBestTakes(context: Context, photos: List<MediaPhoto>) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            
            // 1. Try to load from DB first
            val savedEntities = withContext(Dispatchers.IO) {
                repository.getMediaEntities(photos.map { it.id })
            }
            
            val needsAi = savedEntities.any { it.aiScore == -1f }
            
            if (!needsAi) {
                // All photos have been analyzed, just load them
                _analysisResults.value = savedEntities.map { entity ->
                    CuratedResult(
                        photo = photos.find { it.id == entity.id } ?: photos.first(),
                        score = entity.aiScore,
                        isBestTake = entity.isBestTake,
                        rejectionReason = RejectionReason.valueOf(entity.rejectionReason ?: "NONE"),
                        clusterId = entity.clusterId
                    )
                }
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
            
            _analysisResults.value = fullAnalysis
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
                currentList[index] = item.copy(isBestTake = newState)
                _analysisResults.value = currentList
                
                // Update DB
                withContext(Dispatchers.IO) {
                    repository.updateBestTakeStatus(photoId, newState)
                }
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
