package com.memorycurator.app.feature.albums.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import coil.compose.AsyncImage
import com.memorycurator.app.data.media.MediaPhoto
import com.memorycurator.app.feature.albums.model.Album
import com.memorycurator.app.feature.albums.ui.components.GlassAlbumCard
import com.memorycurator.app.ui.theme.MemoryCuratorTheme
import kotlinx.coroutines.launch

@Composable
fun AlbumsScreen(
    viewModel: AlbumsViewModel,
    onAlbumClick: (Album) -> Unit,
    onPhotosForCuration: (List<MediaPhoto>) -> Unit,
    state: LazyListState = rememberLazyListState()
) {
    val albums by viewModel.albums.collectAsState()
    val scope = rememberCoroutineScope()

    AlbumsScreenContent(
        albums = albums,
        onAlbumClick = onAlbumClick,
        onBestTakesClick = { album ->
            scope.launch {
                val photos = viewModel.getPhotosInAlbum(album)
                onPhotosForCuration(photos)
            }
        },
        state = state
    )
}


@Composable
fun AlbumsScreenContent(
    albums: List<Album>,
    onAlbumClick: (Album) -> Unit,
    onBestTakesClick: (Album) -> Unit,
    state: LazyListState = rememberLazyListState()
) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = state,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 80.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Text(
                    text = "Library",
                    color = Color.White,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            items(albums) { album ->
                GlassAlbumCard(
                    album = album,
                    onClick = { onAlbumClick(album) },
                    onBestTakesClick = { onBestTakesClick(album) }
                )
            }
        }
    }
}

@Composable
fun TabItem(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) Color.White.copy(alpha = 0.2f) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text = text, color = if (isSelected) Color.White else Color.Gray, fontSize = 12.sp)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun AlbumsScreenPreview() {
    val mockAlbums = listOf(
        Album("Summer 2023", "", 156),
        Album("Family Trip", "", 42),
        Album("Screenshots", "", 89)
    )

    MemoryCuratorTheme {
        Box(modifier = Modifier.background(Color.Black)) {
            AlbumsScreenContent(
                albums = mockAlbums,
                onAlbumClick = {},
                onBestTakesClick = {}
            )
        }
    }
}

@Preview
@Composable
fun TabItemPreview() {
    MemoryCuratorTheme {
        Row(
            modifier = Modifier.padding(16.dp).background(Color.Black),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TabItem(text = "Selected", isSelected = true, onClick = {})
            TabItem(text = "Not Selected", isSelected = false, onClick = {})
        }
    }
}
