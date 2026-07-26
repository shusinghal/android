package com.memorycurator.app.feature.maps.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.memorycurator.app.data.media.MediaPhoto
import com.memorycurator.app.feature.albums.model.Album
import com.memorycurator.app.feature.albums.ui.AlbumsViewModel
import com.memorycurator.app.feature.albums.ui.components.GlassAlbumCard
import kotlinx.coroutines.launch

@Composable
fun MapsScreen(
    viewModel: AlbumsViewModel,
    onAlbumClick: (Album) -> Unit,
    onPhotosForCuration: (List<MediaPhoto>) -> Unit
) {
    val locationAlbums by viewModel.locationAlbums.collectAsState()
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 80.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Text(
                text = "Locations",
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        if (locationAlbums.isEmpty()) {
            item {
                Text(
                    text = "No location data found in your photos.",
                    color = Color.LightGray,
                    fontSize = 14.sp
                )
            }
        }

        items(locationAlbums) { album ->
            GlassAlbumCard(
                album = album,
                onClick = { onAlbumClick(album) },
                onBestTakesClick = {
                    scope.launch {
                        val photos = viewModel.getPhotosAtLocation(album)
                        onPhotosForCuration(photos)
                    }
                }
            )
        }
    }
}
