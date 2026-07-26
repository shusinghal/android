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

    val locationAlbums: StateFlow<List<Album>> =
        repository
            .getLocationAlbums()
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

    suspend fun getPhotosInAlbum(album: Album): List<MediaPhoto> {
        return repository.getPhotosInAlbum(album.folderName)
    }

    suspend fun getPhotosAtLocation(album: Album): List<MediaPhoto> {
        // album.folderName is "Location lat, lon"
        val coords = album.folderName.replace("Location ", "").split(", ")
        val lat = coords[0].toDoubleOrNull() ?: 0.0
        val lon = coords[1].toDoubleOrNull() ?: 0.0
        return repository.getPhotosAtLocation(lat, lon)
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