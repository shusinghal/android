package com.memorycurator.app.feature.albums.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.memorycurator.app.data.media.MediaPhoto
import com.memorycurator.app.data.media.MediaRepository
import com.memorycurator.app.feature.albums.data.AlbumsRepository
import com.memorycurator.app.feature.albums.model.Album
import com.memorycurator.app.ui.theme.MemoryCuratorTheme
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

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun AlbumsViewModelPreview() {
    val mockAlbums = listOf(
        Album("Summer 2023", "", 156),
        Album("Family Trip", "", 42),
        Album("Screenshots", "", 89)
    )

    MemoryCuratorTheme {
        Column(
            modifier = Modifier
                .background(Color.Black)
                .padding(16.dp)
        ) {
            Text(
                text = "Albums View Model State (Mock)",
                color = Color.White,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            mockAlbums.forEach { album ->
                Text(
                    text = "• ${album.folderName} (${album.photoCount} items)",
                    color = Color.LightGray,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}