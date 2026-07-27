package com.memorycurator.app.feature.gallery.ui

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.memorycurator.app.data.media.MediaPhoto
import com.memorycurator.app.feature.viewer.ui.ViewerScreen
import com.memorycurator.app.ui.gallery.GalleryViewModel
import com.memorycurator.app.ui.theme.MemoryCuratorTheme
import kotlinx.coroutines.flow.flowOf

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel,
    state: LazyGridState = rememberLazyGridState()
) {

    val uiState by viewModel
        .uiState
        .collectAsStateWithLifecycle()

    val isBestTakesOnly by viewModel.isBestTakesOnly.collectAsState()

    val photos =
        viewModel
            .photos
            .collectAsLazyPagingItems()

    var selectedIndex by remember {
        mutableIntStateOf(-1)
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {

        if (uiState.isLoading) {

            Box(
                modifier = Modifier.fillMaxSize()
            ) {

                Text("Indexing photos...")
            }

            return
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp, start = 20.dp, end = 20.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Gallery",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )

                FilterChip(
                    selected = isBestTakesOnly,
                    onClick = { viewModel.toggleBestTakesOnly() },
                    label = {
                        Text(
                            text = if (isBestTakesOnly) "Best Takes" else "All Photos",
                            fontSize = 12.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (isBestTakesOnly) Icons.Default.AutoAwesome else Icons.Default.FilterList,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color.White.copy(alpha = 0.05f),
                        labelColor = Color.LightGray,
                        selectedContainerColor = Color.White.copy(alpha = 0.2f),
                        selectedLabelColor = Color.White,
                        selectedLeadingIconColor = Color.White
                    ),
                    border = null,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            if (photos.itemCount == 0 && !uiState.isLoading) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (isBestTakesOnly) "No analyzed Best Takes found." else "No photos found.",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                        if (isBestTakesOnly) {
                            TextButton(onClick = { viewModel.toggleBestTakesOnly() }) {
                                Text("Show all photos", color = Color.White)
                            }
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    state = state,
                    columns = GridCells.Adaptive(
                        minSize = 120.dp
                    ),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = 6.dp,
                        end = 6.dp,
                        top = 8.dp,
                        bottom = 100.dp
                    )
                ) {
                    items(
                        count = photos.itemCount
                    ) { index ->
                        val photo = photos[index] ?: return@items
                        Box(
                            modifier = Modifier
                                .padding(3.dp)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(18.dp))
                                .clickable { selectedIndex = index }
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(photo.contentUri)
                                    .crossfade(true)
                                    .size(300)
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            if (photo.isVideo) {
                                Icon(
                                    imageVector = Icons.Default.PlayCircle,
                                    contentDescription = "Video",
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .padding(8.dp),
                                    tint = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (
            selectedIndex >= 0 &&
            selectedIndex < photos.itemCount
        ) {
            val selectedPhoto = photos[selectedIndex]
            if (selectedPhoto != null) {
                ViewerScreen(
                    photos = listOf(selectedPhoto),
                    initialIndex = 0,
                    onDismiss = {
                        selectedIndex = -1
                    }
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GalleryScreenPreview() {
    MemoryCuratorTheme {
        // Mocked preview would need a way to provide fake PagingData
        // This is a simplified preview for structural visualization
        Text("Gallery Screen Preview (requires mocked ViewModel)")
    }
}
