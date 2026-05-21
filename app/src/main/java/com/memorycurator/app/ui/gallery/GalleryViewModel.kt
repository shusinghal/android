package com.memorycurator.app.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.memorycurator.app.data.media.MediaIndexer
import com.memorycurator.app.data.media.MediaPhoto
import com.memorycurator.app.data.media.MediaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GalleryViewModel(
    repository: MediaRepository,
    private val mediaIndexer: MediaIndexer
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(GalleryUiState())

    val uiState: StateFlow<GalleryUiState> =
        _uiState.asStateFlow()

    val photos: Flow<PagingData<MediaPhoto>> =
        repository
            .getPagedPhotos()
            .cachedIn(viewModelScope)

    init {

        viewModelScope.launch {

            mediaIndexer.indexMedia()

            _uiState.value =
                GalleryUiState(
                    isLoading = false
                )
        }
    }
}

class GalleryViewModelFactory(
    private val repository: MediaRepository,
    private val mediaIndexer: MediaIndexer
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {

        return GalleryViewModel(
            repository,
            mediaIndexer
        ) as T
    }
}