package com.memorycurator.app.feature.albums.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.memorycurator.app.data.media.MediaPhoto
import com.memorycurator.app.feature.albums.model.Album
import com.memorycurator.app.feature.albums.ui.components.GlassAlbumCard
import com.memorycurator.app.ui.components.GlassSurface
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
    val archivedPhotos by viewModel.archivedPhotos.collectAsState(initial = emptyList())
    var isArchiveViewActive by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    AlbumsScreenContent(
        albums = albums,
        archivedPhotos = archivedPhotos,
        isArchiveViewActive = isArchiveViewActive,
        onArchiveToggle = { isArchiveViewActive = it },
        onAlbumClick = onAlbumClick,
        onBestTakesClick = { album ->
            scope.launch {
                val photos = viewModel.getPhotosInAlbum(album)
                onPhotosForCuration(photos)
            }
        },
        onRestorePhoto = { viewModel.restoreMedia(listOf(it)) },
        state = state
    )
}


@Composable
fun AlbumsScreenContent(
    albums: List<Album>,
    archivedPhotos: List<MediaPhoto>,
    isArchiveViewActive: Boolean,
    onArchiveToggle: (Boolean) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onBestTakesClick: (Album) -> Unit,
    onRestorePhoto: (Long) -> Unit,
    state: LazyListState = rememberLazyListState()
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (isArchiveViewActive) {
            ArchiveView(
                photos = archivedPhotos,
                onBack = { onArchiveToggle(false) },
                onRestore = onRestorePhoto
            )
        } else {
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

                // Special Archive Card
                if (archivedPhotos.isNotEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.12f),
                                            Color.White.copy(alpha = 0.03f)
                                        )
                                    )
                                )
                                .border(
                                    width = 1.dp,
                                    color = Color.White.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(24.dp)
                                )
                                .clickable { onArchiveToggle(true) }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(archivedPhotos.first().contentUri)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(100.dp)
                                        .clip(RoundedCornerShape(16.dp)),
                                    contentScale = ContentScale.Crop
                                )

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 16.dp, end = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Archive",
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )

                                    Text(
                                        text = "${archivedPhotos.size} items",
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
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

@Composable
fun ArchiveView(
    photos: List<MediaPhoto>,
    onBack: () -> Unit,
    onRestore: (Long) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 25.dp)) {
        Row(
            modifier = Modifier.padding(top = 60.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(
                text = "Archive",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 100.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(photos.size) { index ->
                val photo = photos[index]
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    AsyncImage(
                        model = photo.contentUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    IconButton(
                        onClick = { onRestore(photo.id) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(24.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Restore,
                            contentDescription = "Restore",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
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
    val mockArchivedPhotos = listOf(
        MediaPhoto(1, Uri.EMPTY, 0, 0)
    )

    MemoryCuratorTheme {
        Box(modifier = Modifier.background(Color.Black)) {
            AlbumsScreenContent(
                albums = mockAlbums,
                archivedPhotos = mockArchivedPhotos,
                isArchiveViewActive = false,
                onArchiveToggle = {},
                onAlbumClick = {},
                onBestTakesClick = {},
                onRestorePhoto = {}
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

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun ArchiveViewPreview() {
    val mockPhotos = listOf(
        MediaPhoto(1, Uri.EMPTY, System.currentTimeMillis(), System.currentTimeMillis()),
        MediaPhoto(2, Uri.EMPTY, System.currentTimeMillis(), System.currentTimeMillis()),
        MediaPhoto(3, Uri.EMPTY, System.currentTimeMillis(), System.currentTimeMillis())
    )
    MemoryCuratorTheme {
        Box(Modifier.background(Color.Black)) {
            ArchiveView(
                photos = mockPhotos,
                onBack = {},
                onRestore = {}
            )
        }
    }
}
