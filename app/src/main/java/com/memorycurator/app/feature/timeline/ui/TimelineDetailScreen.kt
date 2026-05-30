package com.memorycurator.app.feature.timeline.ui

import androidx.compose.ui.tooling.preview.Preview
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.memorycurator.app.feature.timeline.model.TimelineGroup
import com.memorycurator.app.ui.components.BlurBackground
import com.memorycurator.app.feature.viewer.ui.ViewerScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineDetailScreen(
    group: TimelineGroup,
    onBack: () -> Unit
) {
    var selectedIndex by remember { mutableIntStateOf(-1) }

    Box(modifier = Modifier.fillMaxSize()) {

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = group.title,
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${group.photos.size} ${if (group.photos.size == 1) "photo" else "photos"}",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    actions = {
                        // Room for "Select" / "Select All" options
                        TextButton(onClick = { /* TODO: Implement Selection */ }) {
                            Text("Best Takes", color = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 100.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(group.photos) { index, photo ->
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedIndex = index }
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(photo.contentUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }

        // Open Viewer when an image is tapped
        if (selectedIndex >= 0) {
            ViewerScreen(
                photos = group.photos,
                initialIndex = selectedIndex,
                onDismiss = { selectedIndex = -1 }
            )
        }
    }
}


// Add this at the bottom of the file
@Preview(showBackground = true)
@Composable
fun TimelineDetailScreenPreview() {
    val mockPhotos = listOf(
        com.memorycurator.app.data.media.MediaPhoto(
            id = 1L,
            contentUri = Uri.EMPTY,
            dateTaken = System.currentTimeMillis()
        )
    )
    val mockGroup = TimelineGroup("12 October 2024", mockPhotos)

    com.memorycurator.app.ui.theme.GlassTheme {
        TimelineDetailScreen(
            group = mockGroup,
            onBack = {}
        )
    }
}