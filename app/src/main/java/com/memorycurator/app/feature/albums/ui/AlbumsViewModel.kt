package com.memorycurator.app.feature.albums.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.memorycurator.app.data.media.MediaPhoto
import com.memorycurator.app.data.media.MediaRepository
import com.memorycurator.app.feature.albums.data.AlbumsRepository
import com.memorycurator.app.feature.albums.model.Album
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlbumsViewModel(
    private val repository: AlbumsRepository,
    private val mediaRepository: MediaRepository
) : ViewModel() {

    val albums: StateFlow<List<Album>> =
        repository
            .getAlbums()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    val archivedPhotos: Flow<List<MediaPhoto>> =
        mediaRepository.getArchivedPhotos()

    fun restoreMedia(ids: List<Long>) {
        viewModelScope.launch {
            mediaRepository.restoreMedia(ids)
        }
    }
}

class AlbumsViewModelFactory(
    private val repository: AlbumsRepository,
    private val mediaRepository: MediaRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {

        return AlbumsViewModel(
            repository,
            mediaRepository
        ) as T
    }
}