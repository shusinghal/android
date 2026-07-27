package com.memorycurator.app.ui.gallery

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.memorycurator.app.data.media.MediaIndexer
import com.memorycurator.app.data.media.MediaPhoto
import com.memorycurator.app.data.media.MediaRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

class GalleryViewModel(
    private val repository: MediaRepository,
    private val mediaIndexer: MediaIndexer,
    context: Context
) : ViewModel() {

    private val prefs = context.getSharedPreferences("gallery_prefs", Context.MODE_PRIVATE)

    private val _uiState =
        MutableStateFlow(GalleryUiState())

    val uiState: StateFlow<GalleryUiState> =
        _uiState.asStateFlow()

    private val _isBestTakesOnly = MutableStateFlow(prefs.getBoolean("best_takes_only", true))
    val isBestTakesOnly: StateFlow<Boolean> = _isBestTakesOnly.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val photos: Flow<PagingData<MediaPhoto>> =
        _isBestTakesOnly.flatMapLatest { onlyBest ->
            repository.getPagedPhotos(onlyBest)
        }.cachedIn(viewModelScope)

    val allPhotos: Flow<List<MediaPhoto>> =
        repository.getAllPhotos()

    init {
        indexMedia()
    }

    fun toggleBestTakesOnly() {
        val newValue = !_isBestTakesOnly.value
        _isBestTakesOnly.value = newValue
        prefs.edit().putBoolean("best_takes_only", newValue).apply()
    }

    fun indexMedia() {
        viewModelScope.launch {
            try {
                _uiState.value = GalleryUiState(isLoading = true)
                mediaIndexer.indexMedia()
                _uiState.value = GalleryUiState(isLoading = false)
            } catch (e: Exception) {
                _uiState.value = GalleryUiState(isLoading = false, error = e.message)
            }
        }
    }
}

class GalleryViewModelFactory(
    private val repository: MediaRepository,
    private val mediaIndexer: MediaIndexer,
    private val context: Context
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {

        return GalleryViewModel(
            repository,
            mediaIndexer,
            context
        ) as T
    }
}
