package com.memorycurator.app.feature.albums.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.memorycurator.app.feature.albums.ui.components.GlassAlbumCard
import com.memorycurator.app.feature.home.ui.components.HomeHeroSection

@Composable
fun AlbumsScreen(
    viewModel: AlbumsViewModel
) {

    val albums by viewModel
        .albums
        .collectAsState()

    Column(
        modifier = Modifier.fillMaxSize()
    ) {

        HomeHeroSection()

        LazyVerticalGrid(

            columns = GridCells.Adaptive(
                minSize = 170.dp
            ),

            modifier = Modifier

                .fillMaxSize()

                .padding(horizontal = 10.dp),

            contentPadding = PaddingValues(
                top = 12.dp,
                bottom = 120.dp
            ),

            verticalArrangement =
                Arrangement.spacedBy(14.dp),

            horizontalArrangement =
                Arrangement.spacedBy(14.dp)
        ) {

            items(albums) { album ->

                GlassAlbumCard(

                    album = album,

                    onClick = {

                    }
                )
            }
        }
    }
}