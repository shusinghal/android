package com.memorycurator.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import com.memorycurator.app.data.local.DatabaseProvider
import com.memorycurator.app.feature.albums.data.AlbumsRepository
import com.memorycurator.app.feature.albums.ui.AlbumsScreen
import com.memorycurator.app.feature.albums.ui.AlbumsViewModel
import com.memorycurator.app.feature.albums.ui.AlbumsViewModelFactory
import com.memorycurator.app.feature.gallery.ui.GalleryScreen
import com.memorycurator.app.feature.timeline.data.TimelineGrouper
import com.memorycurator.app.feature.timeline.ui.TimelineScreen
import com.memorycurator.app.ui.gallery.GalleryViewModel
import com.memorycurator.app.data.media.toMediaPhoto

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

        TimelineGrouper.groupByMonth(
            allPhotos
        )
    }

    val items = listOf(

        BottomNavItem.Timeline,

        BottomNavItem.Maps,

        BottomNavItem.Albums,

        BottomNavItem.Profile
    )

    var selectedRoute by remember {
//        mutableStateOf("photos")
        mutableStateOf(BottomNavItem.Timeline.route)
    }

    Box(

        modifier = Modifier

            .fillMaxSize()

            .background(

                brush = Brush.verticalGradient(

                    colors = listOf(

                        Color(0xFF0F1115),

                        Color(0xFF161A22),

                        Color(0xFF10131A)
                    )
                )
            )
    ) {

        Scaffold(

            containerColor = Color.Transparent,

            bottomBar = {


                    AppBottomBar(

                        selectedRoute =
                            selectedRoute,

                        onRouteSelected = {

                            selectedRoute = it
                        }
                    )
            }

        ) { padding ->

            Box(
                modifier = Modifier.padding(padding)
            ) {

                when (selectedRoute) {

                    "photos" -> {

                        GalleryScreen(
                            viewModel = viewModel
                        )
                    }

                    "albums" -> {

                        AlbumsScreen(
                            viewModel = albumsViewModel
                        )
                    }

                    "timeline" -> {

                        TimelineScreen(
                            groups = timelineGroups
                        )
                    }

                    "cleanup" -> {

                        Text(
                            text = "Cleanup",
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}