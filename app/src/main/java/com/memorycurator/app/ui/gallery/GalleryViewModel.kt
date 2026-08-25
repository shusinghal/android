package com.memorycurator.app.ui.gallery

import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.memorycurator.app.data.media.MediaIndexer
import com.memorycurator.app.data.media.MediaPhoto
import com.memorycurator.app.data.media.MediaRepository
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import androidx.core.content.edit

class GalleryViewModel(
    private val repository: MediaRepository,
    private val mediaIndexer: MediaIndexer,
    private val prefs: SharedPreferences,
    private val contentResolver: ContentResolver
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(GalleryUiState())

    val uiState: StateFlow<GalleryUiState> =
        _uiState.asStateFlow()

    private val _isBestTakesOnly = MutableStateFlow(prefs.getBoolean("best_takes_only", false))
    val isBestTakesOnly: StateFlow<Boolean> = _isBestTakesOnly.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val photos: Flow<PagingData<MediaPhoto>> =
        _isBestTakesOnly.flatMapLatest { onlyBest ->
            repository.getPagedPhotos(onlyBest)
        }.cachedIn(viewModelScope)

    val allPhotos: Flow<List<MediaPhoto>> =
        repository.getAllPhotos()

    private val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            viewModelScope.launch {
                // Reduced delay for "instant" appearance (from 1000ms to 300ms)
                kotlinx.coroutines.delay(300)
                
                if (uri != null) {
                    val hasItemId = runCatching { ContentUris.parseId(uri) }.isSuccess
                    if (hasItemId) {
                        mediaIndexer.indexUri(uri)
                    } else {
                        // Quick sync of latest 50 items
                        mediaIndexer.indexMedia(limit = 50)
                    }
                } else {
                    mediaIndexer.indexMedia(limit = 50)
                }
            }
        }
    }

    init {
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            observer
        )
        contentResolver.registerContentObserver(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            true,
            observer
        )
    }

    override fun onCleared() {
        super.onCleared()
        contentResolver.unregisterContentObserver(observer)
    }

    fun toggleBestTakesOnly() {
        val newValue = !_isBestTakesOnly.value
        _isBestTakesOnly.value = newValue
        prefs.edit { putBoolean("best_takes_only", newValue) }
    }

    fun toggleBestTake(photoId: Long, isBest: Boolean) {
        viewModelScope.launch {
            repository.updateBestTakeStatus(photoId, isBest)
            // No need to call indexMedia() here as PagingSource is reactive
        }
    }

    fun indexMedia(limit: Int? = null) {
        viewModelScope.launch {
            try {
                _uiState.value = GalleryUiState(isLoading = true)
                mediaIndexer.indexMedia(limit)
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
            context.getSharedPreferences("gallery_prefs", Context.MODE_PRIVATE),
            context.contentResolver
        ) as T
    }
}
