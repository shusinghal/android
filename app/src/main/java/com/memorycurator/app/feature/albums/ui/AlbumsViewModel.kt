package com.memorycurator.app.feature.albums.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.memorycurator.app.feature.albums.data.AlbumsRepository
import com.memorycurator.app.feature.albums.model.Album
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class AlbumsViewModel(
    repository: AlbumsRepository
) : ViewModel() {

    val albums: StateFlow<List<Album>> =

        repository
            .getAlbums()

            .stateIn(

                scope = viewModelScope,

                started = SharingStarted.WhileSubscribed(5000),

                initialValue = emptyList()
            )
}

class AlbumsViewModelFactory(
    private val repository: AlbumsRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {

        return AlbumsViewModel(
            repository
        ) as T
    }
}