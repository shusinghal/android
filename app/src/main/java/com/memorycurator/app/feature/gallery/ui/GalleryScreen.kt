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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

                        model = ImageRequest.Builder(
                            LocalContext.current
                        )
                            .data(photo.contentUri)
                            .crossfade(true)
                            .size(300)
                            .build(),

                        contentDescription = null,

                        modifier = Modifier.fillMaxSize(),

                        contentScale = ContentScale.Crop
                    )
                }
            }
        }

        if (
            selectedIndex >= 0 &&
            selectedIndex < photos.itemCount
        ) {

            val selectedPhoto =
                photos[selectedIndex]

            if (selectedPhoto != null) {

                ViewerScreen(

                    photos = List(photos.itemCount) { i ->
                        photos[i]!!
                    },

                    initialIndex = selectedIndex,

                    onDismiss = {
                        selectedIndex = -1
                    }
                )
            }
        }
    }
}