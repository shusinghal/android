package com.memorycurator.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.memorycurator.app.data.local.DatabaseProvider
import com.memorycurator.app.data.media.MediaIndexer
import com.memorycurator.app.data.media.MediaRepository
import com.memorycurator.app.data.media.MediaRepositoryImpl
import com.memorycurator.app.feature.albums.data.AlbumsRepository
import com.memorycurator.app.feature.albums.ui.AlbumsScreen
import com.memorycurator.app.feature.albums.ui.AlbumsViewModel
import com.memorycurator.app.feature.albums.ui.AlbumsViewModelFactory
import com.memorycurator.app.feature.gallery.ui.GalleryScreen
import com.memorycurator.app.feature.maps.ui.MapsScreen
import com.memorycurator.app.feature.timeline.data.TimelineGrouper
import com.memorycurator.app.feature.albums.model.Album
import com.memorycurator.app.feature.timeline.model.TimelineGroup
import com.memorycurator.app.feature.timeline.ui.TimelineDetailScreen
import com.memorycurator.app.feature.timeline.ui.TimelineScreen
import com.memorycurator.app.ui.screens.AICurationScreen
import com.memorycurator.app.ui.screens.OnboardingScreen
import com.memorycurator.app.ui.gallery.GalleryViewModel
import com.memorycurator.app.ui.theme.GlassTheme
import com.memorycurator.app.ui.components.BlurBackground
import kotlinx.coroutines.launch

@Composable
fun MainNavigation(
    viewModel: GalleryViewModel
) {
    val context = LocalContext.current

    val mediaRepository = remember {
        MediaRepositoryImpl(context)
    }

    val database = remember {
        DatabaseProvider.getDatabase(context)
    }

    val mediaIndexer = remember {
        MediaIndexer(context, database.mediaDao())
    }

    val albumsRepository = remember {
        AlbumsRepository(
            database.mediaDao()
        )
    }

    val albumsViewModel: AlbumsViewModel =
        viewModel(
            factory = AlbumsViewModelFactory(
                albumsRepository,
                mediaRepository
            )
        )

    val pagingPhotos =
        viewModel.photos.collectAsLazyPagingItems()

    val allPhotos by viewModel.allPhotos.collectAsState(initial = emptyList())

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

    var selectedTimelineGroup by remember { mutableStateOf<TimelineGroup?>(null) }
    var selectedAlbumGroup by remember { mutableStateOf<TimelineGroup?>(null) }
    var selectedMapGroup by remember { mutableStateOf<TimelineGroup?>(null) }
    
    var curationPhotos by remember { mutableStateOf<List<com.memorycurator.app.data.media.MediaPhoto>?>(null) }
    
    val timelineListState = rememberLazyListState()
    val galleryGridState = rememberLazyGridState()
    val albumsListState = rememberLazyListState()
    val mapsListState = rememberLazyListState()

    val scope = rememberCoroutineScope()

    var showOnboarding by remember {
        mutableStateOf(
            context.getSharedPreferences("prefs", android.content.Context.MODE_PRIVATE)
                .getBoolean("first_run", true)
        )
    }

    if (showOnboarding) {
        OnboardingScreen(
            onContinue = {
                context.getSharedPreferences("prefs", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("first_run", false)
                    .apply()
                showOnboarding = false
            }
        )
        return
    }


    // Call the stateless content version
    MainNavigationContent(
        selectedRoute = selectedRoute,
        onRouteSelected = { selectedRoute = it },
        timelineGroups = timelineGroups,
        galleryViewModel = viewModel,
        albumsViewModel = albumsViewModel,
        selectedTimelineGroup = selectedTimelineGroup,
        onTimelineGroupSelected = { selectedTimelineGroup = it },
        selectedAlbumGroup = selectedAlbumGroup,
        onAlbumGroupSelected = { selectedAlbumGroup = it },
        selectedMapGroup = selectedMapGroup,
        onMapGroupSelected = { selectedMapGroup = it },
        curationPhotos = curationPhotos,
        onCurationPhotosSelected = { curationPhotos = it },
        mediaRepository = mediaRepository,
        mediaIndexer = mediaIndexer,
        timelineListState = timelineListState,
        galleryGridState = galleryGridState,
        albumsListState = albumsListState,
        mapsListState = mapsListState,
        scope = scope
    )
}

@Composable
fun MainNavigationContent(
    selectedRoute: String,
    onRouteSelected: (String) -> Unit,
    timelineGroups: List<TimelineGroup>,
    galleryViewModel: GalleryViewModel?,
    albumsViewModel: AlbumsViewModel?,
    selectedTimelineGroup: TimelineGroup?,
    onTimelineGroupSelected: (TimelineGroup?) -> Unit,
    selectedAlbumGroup: TimelineGroup?,
    onAlbumGroupSelected: (TimelineGroup?) -> Unit,
    selectedMapGroup: TimelineGroup?,
    onMapGroupSelected: (TimelineGroup?) -> Unit,
    curationPhotos: List<com.memorycurator.app.data.media.MediaPhoto>?,
    onCurationPhotosSelected: (List<com.memorycurator.app.data.media.MediaPhoto>?) -> Unit,
    mediaRepository: MediaRepository?,
    mediaIndexer: MediaIndexer?,
    timelineListState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
    galleryGridState: androidx.compose.foundation.lazy.grid.LazyGridState = rememberLazyGridState(),
    albumsListState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
    mapsListState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
    scope: kotlinx.coroutines.CoroutineScope
) {
    // Handle System Back Button
    BackHandler(enabled = curationPhotos != null || selectedTimelineGroup != null || selectedAlbumGroup != null || selectedMapGroup != null) {
        if (curationPhotos != null) {
            onCurationPhotosSelected(null)
        } else if (selectedTimelineGroup != null && selectedRoute == "timeline") {
            onTimelineGroupSelected(null)
        } else if (selectedAlbumGroup != null && selectedRoute == "albums") {
            onAlbumGroupSelected(null)
        } else if (selectedMapGroup != null && selectedRoute == "maps") {
            onMapGroupSelected(null)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    )
        {
            BlurBackground()

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
                when (selectedRoute)
                {
                    "gallery" -> {
                        if (galleryViewModel != null) {
                            GalleryScreen(
                                viewModel = galleryViewModel,
                                state = galleryGridState
                            )
                        }
                    }

                    "timeline" -> {
                        if (selectedTimelineGroup != null) {
                            TimelineDetailScreen(
                                group = selectedTimelineGroup,
                                onBack = { onTimelineGroupSelected(null) },
                                onBestTakesClick = { onCurationPhotosSelected(it.photos) },
                                repository = mediaRepository,
                                mediaIndexer = mediaIndexer
                            )
                        } else {
                            TimelineScreen(
                                groups = timelineGroups,
                                onGroupClick = { onTimelineGroupSelected(it) },
                                onBestTakesClick = { onCurationPhotosSelected(it.photos) },
                                state = timelineListState
                            )
                        }
                    }

                    "albums" -> {
                        if (albumsViewModel != null) {
                            if (selectedAlbumGroup != null) {
                                TimelineDetailScreen(
                                    group = selectedAlbumGroup,
                                    onBack = { onAlbumGroupSelected(null) },
                                    onBestTakesClick = { onCurationPhotosSelected(it.photos) },
                                    repository = mediaRepository,
                                    mediaIndexer = mediaIndexer
                                )
                            } else {
                                AlbumsScreen(
                                    viewModel = albumsViewModel,
                                    onAlbumClick = { album ->
                                        scope.launch {
                                            val photos = albumsViewModel.getPhotosInAlbum(album)
                                            onAlbumGroupSelected(TimelineGroup(album.folderName, photos))
                                        }
                                    },
                                    onPhotosForCuration = { onCurationPhotosSelected(it) },
                                    state = albumsListState
                                )
                            }
                        }
                    }

                    "maps" -> {
                        if (albumsViewModel != null) {
                            if (selectedMapGroup != null) {
                                TimelineDetailScreen(
                                    group = selectedMapGroup,
                                    onBack = { onMapGroupSelected(null) },
                                    onBestTakesClick = { onCurationPhotosSelected(it.photos) },
                                    repository = mediaRepository,
                                    mediaIndexer = mediaIndexer
                                )
                            } else {
                                MapsScreen(
                                    viewModel = albumsViewModel,
                                    onAlbumClick = { album ->
                                        scope.launch {
                                            val photos = albumsViewModel.getPhotosAtLocation(album)
                                            onMapGroupSelected(TimelineGroup(album.folderName, photos))
                                        }
                                    },
                                    onPhotosForCuration = { onCurationPhotosSelected(it) },
                                    state = mapsListState
                                )
                            }
                        }
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

        if (curationPhotos != null && mediaRepository != null && mediaIndexer != null) {
            AICurationScreen(
                photos = curationPhotos,
                onBack = { onCurationPhotosSelected(null) },
                repository = mediaRepository,
                mediaIndexer = mediaIndexer
            )
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
            albumsViewModel = null,
            selectedTimelineGroup = null,
            onTimelineGroupSelected = {},
            selectedAlbumGroup = null,
            onAlbumGroupSelected = {},
            selectedMapGroup = null,
            onMapGroupSelected = {},
            curationPhotos = null,
            onCurationPhotosSelected = {},
            mediaRepository = null,
            mediaIndexer = null,
            scope = rememberCoroutineScope()
        )
    }
}
