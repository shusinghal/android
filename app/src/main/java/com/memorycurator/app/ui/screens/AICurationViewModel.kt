package com.memorycurator.app.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memorycurator.app.core.ai.CuratedResult
import com.memorycurator.app.core.ai.ImageCurator
import com.memorycurator.app.core.ai.ImageCuratorImpl
import com.memorycurator.app.data.media.MediaPhoto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AICurationViewModel(
    private val imageCurator: ImageCurator = ImageCuratorImpl()
) : ViewModel() {

    private val _analysisResults = MutableStateFlow<List<CuratedResult>>(emptyList())
    val analysisResults: StateFlow<List<CuratedResult>> = _analysisResults

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing

    fun filterBestTakes(context: Context, photos: List<MediaPhoto>) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            
            // Perform real on-device AI analysis
            val fullAnalysis = imageCurator.analyzePhotos(context, photos)
            
            // Filter results based on the AI quality score
            _analysisResults.value = fullAnalysis
                .filter { it.score >= 0.5f } // Threshold for curation
                .sortedByDescending { it.score }

            _isAnalyzing.value = false
        }
    }
}
