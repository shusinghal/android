package com.memorycurator.app.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memorycurator.app.core.ai.CuratedResult
import com.memorycurator.app.core.ai.ImageCurator
import com.memorycurator.app.core.ai.ImageCuratorImpl
import com.memorycurator.app.data.media.MediaPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class CurationProgress(
    val current: Int,
    val total: Int,
    val percentage: Float
)

class AICurationViewModel(
    private val imageCurator: ImageCurator = ImageCuratorImpl()
) : ViewModel() {

    private val _analysisResults = MutableStateFlow<List<CuratedResult>>(emptyList())
    val analysisResults: StateFlow<List<CuratedResult>> = _analysisResults

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing

    private val _progress = MutableStateFlow<CurationProgress?>(null)
    val progress: StateFlow<CurationProgress?> = _progress

    fun filterBestTakes(context: Context, photos: List<MediaPhoto>) {
        viewModelScope.launch(Dispatchers.Default) {
            _isAnalyzing.value = true
            
            val fullAnalysis = imageCurator.analyzePhotos(context, photos) { current, total ->
                _progress.value = CurationProgress(current, total, current.toFloat() / total)
            }
            
            _analysisResults.value = fullAnalysis
            _isAnalyzing.value = false
            _progress.value = null
        }
    }
}
