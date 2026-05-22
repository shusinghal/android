package com.memorycurator.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import com.memorycurator.app.data.local.DatabaseProvider
import com.memorycurator.app.feature.albums.data.AlbumsRepository
import com.memorycurator.app.feature.albums.ui.AlbumsScreen
import com.memorycurator.app.feature.albums.ui.AlbumsViewModel
import com.memorycurator.app.feature.albums.ui.AlbumsViewModelFactory
import com.memorycurator.app.feature.gallery.ui.GalleryScreen
import com.memorycurator.app.feature.timeline.data.TimelineGrouper
import com.memorycurator.app.feature.timeline.model.TimelineGroup
import com.memorycurator.app.feature.timeline.ui.TimelineScreen
import com.memorycurator.app.ui.gallery.GalleryViewModel
import com.memorycurator.app.ui.theme.GlassTheme
import com.memorycurator.app.ui.components.BlurBackground

@Composable
fun MainNavigation(
    viewModel: GalleryViewModel
) {
    val context = LocalContext.current

    val database = remember {
        DatabaseProvider.getDatabase(context)
    }

    val albumsRepository = remember {
        AlbumsRepository(
            database.mediaDao()
        )
    }

    val albumsViewModel: AlbumsViewModel =
        viewModel(
            factory = AlbumsViewModelFactory(
                albumsRepository
            )
        )

    val pagingPhotos =
        viewModel.photos.collectAsLazyPagingItems()

    val allPhotos = remember(
        pagingPhotos.itemCount
    ) {
        List(pagingPhotos.itemCount) { index ->
            pagingPhotos[index]
        }.filterNotNull()
    }

    val timelineGroups = remember(
        allPhotos
    ) {
        TimelineGrouper.groupByDate(
            allPhotos
        )
    }

    var selectedRoute by remember {
        mutableStateOf(BottomNavItem.Timeline.route)
    }

    // Call the stateless content version
    MainNavigationContent(
        selectedRoute = selectedRoute,
        onRouteSelected = { selectedRoute = it },
        timelineGroups = timelineGroups,
        galleryViewModel = viewModel,
        albumsViewModel = albumsViewModel
    )
}

@Composable
fun MainNavigationContent(
    selectedRoute: String,
    onRouteSelected: (String) -> Unit,
    timelineGroups: List<TimelineGroup>,
    galleryViewModel: GalleryViewModel?,
    albumsViewModel: AlbumsViewModel?
) {
    Box(
        modifier = Modifier.fillMaxSize()
    )
        {
            BlurBackground()
//            .background(
//                brush = Brush.verticalGradient(
//                    colors = listOf(
//                        Color(0xFF0F1115),
//                        Color(0xFF161A22),
//                        Color(0xFF10131A)
//                    )
//                )
//            )

        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                AppBottomBar(
                    selectedRoute = selectedRoute,
                    onRouteSelected = onRouteSelected
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier.padding(padding)
            ) {
                when (selectedRoute) {
                    "timeline" -> {
                        TimelineScreen(
                            groups = timelineGroups
                        )
                    }

                    "albums" -> {
                        if (albumsViewModel != null) {
                            AlbumsScreen(
                                viewModel = albumsViewModel
                            )
                        }
                    }

                    "maps" -> {
                        Text(
                            text = "Maps Screen",
                            color = Color.White,
                            modifier = Modifier.padding(16.dp)
                        )
                    }

                    "profile" -> {
                        Text(
                            text = "Profile Screen",
                            color = Color.White,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun MainNavigationPreview() {
    GlassTheme {
        MainNavigationContent(
            selectedRoute = BottomNavItem.Timeline.route,
            onRouteSelected = {},
            timelineGroups = emptyList(),
            galleryViewModel = null,
            albumsViewModel = null
        )
    }
}
