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
import com.memorycurator.app.data.media.MediaRepository
import com.memorycurator.app.data.media.MediaRepositoryImpl
import com.memorycurator.app.feature.albums.data.AlbumsRepository
import com.memorycurator.app.feature.albums.ui.AlbumsScreen
import com.memorycurator.app.feature.albums.ui.AlbumsViewModel
import com.memorycurator.app.feature.albums.ui.AlbumsViewModelFactory
import com.memorycurator.app.feature.gallery.ui.GalleryScreen
import com.memorycurator.app.feature.timeline.data.TimelineGrouper
import com.memorycurator.app.feature.timeline.model.TimelineGroup
import com.memorycurator.app.feature.timeline.ui.TimelineDetailScreen
import com.memorycurator.app.feature.timeline.ui.TimelineScreen
import com.memorycurator.app.ui.screens.AICurationScreen
import com.memorycurator.app.ui.screens.OnboardingScreen
import com.memorycurator.app.ui.gallery.GalleryViewModel
import com.memorycurator.app.ui.theme.GlassTheme
import com.memorycurator.app.ui.components.BlurBackground

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

    var selectedGroup by remember { mutableStateOf<TimelineGroup?>(null) }
    var curationGroup by remember { mutableStateOf<TimelineGroup?>(null) }

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
        selectedGroup = selectedGroup,
        onGroupSelected = { selectedGroup = it },
        curationGroup = curationGroup,
        onCurationGroupSelected = { curationGroup = it },
        mediaRepository = mediaRepository
    )
}

@Composable
fun MainNavigationContent(
    selectedRoute: String,
    onRouteSelected: (String) -> Unit,
    timelineGroups: List<TimelineGroup>,
    galleryViewModel: GalleryViewModel?,
    albumsViewModel: AlbumsViewModel?,
    selectedGroup: TimelineGroup?,
    onGroupSelected: (TimelineGroup?) -> Unit,
    curationGroup: TimelineGroup?,
    onCurationGroupSelected: (TimelineGroup?) -> Unit,
    mediaRepository: MediaRepository?
) {
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
                    "timeline" -> {
                        if (curationGroup != null) {
                            if (mediaRepository != null) {
                                AICurationScreen(
                                    photos = curationGroup.photos,
                                    onBack = { onCurationGroupSelected(null) },
                                    repository = mediaRepository
                                )
                            }
                        } else if (selectedGroup != null) {
                            TimelineDetailScreen(
                                group = selectedGroup,
                                onBack = { onGroupSelected(null) },
                                onBestTakesClick = { onCurationGroupSelected(it) }
                            )
                        } else {
                            TimelineScreen(
                                groups = timelineGroups,
                                onGroupClick = { onGroupSelected(it) },
                                onBestTakesClick = { onCurationGroupSelected(it) }
                            )
                        }
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
            albumsViewModel = null,
            selectedGroup = null, // Added
            onGroupSelected = {},
            curationGroup = null,
            onCurationGroupSelected = {},
            mediaRepository = null
        )
    }
}
