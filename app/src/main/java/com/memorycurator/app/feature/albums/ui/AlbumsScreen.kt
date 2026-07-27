package com.memorycurator.app.feature.albums.ui

import android.net.Uri
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

    Box(modifier = Modifier.fillMaxSize()) {
        if (isArchiveViewActive) {
            ArchiveView(
                photos = archivedPhotos,
                onBack = { isArchiveViewActive = false },
                onRestore = { viewModel.restoreMedia(listOf(it)) }
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
                        GlassSurface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isArchiveViewActive = true }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(
                                    model = archivedPhotos.first().contentUri,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = "Archive",
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${archivedPhotos.size} items",
                                        color = Color.LightGray
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
                        onBestTakesClick = {
                            scope.launch {
                                val photos = viewModel.getPhotosInAlbum(album)
                                onPhotosForCuration(photos)
                            }
                        }
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
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
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
fun ArchiveViewPreview() {
    val mockPhotos = listOf(
        MediaPhoto(1, Uri.EMPTY, System.currentTimeMillis()),
        MediaPhoto(2, Uri.EMPTY, System.currentTimeMillis())
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
