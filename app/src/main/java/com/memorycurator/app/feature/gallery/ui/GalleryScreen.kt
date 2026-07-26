package com.memorycurator.app.feature.gallery.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.memorycurator.app.feature.viewer.ui.ViewerScreen
import com.memorycurator.app.ui.gallery.GalleryViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel
) {

    val uiState by viewModel
        .uiState
        .collectAsStateWithLifecycle()

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

        LazyVerticalGrid(

            columns = GridCells.Adaptive(
                minSize = 120.dp
            ),

            modifier = Modifier.fillMaxSize(),

            contentPadding = PaddingValues(
                start = 6.dp,
                end = 6.dp,
                top = 40.dp,
                bottom = 100.dp
            )
        ) {

            items(
                count = photos.itemCount
            ) { index ->

                val photo = photos[index]
                    ?: return@items

                Box(

                    modifier = Modifier
                        .padding(3.dp)
                        .aspectRatio(1f)
                        .clip(
                            RoundedCornerShape(18.dp)
                        )
                        .clickable {

                            selectedIndex = index
                        }
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

        if (
            selectedIndex >= 0 &&
            selectedIndex < photos.itemCount
        ) {
            val selectedPhoto = photos[selectedIndex]
            if (selectedPhoto != null) {
                // To avoid loading the entire library into a List (which crashes with !!),
                // we'll pass just the selected photo for now or a limited window.
                // For a proper implementation, ViewerScreen should take LazyPagingItems.
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
