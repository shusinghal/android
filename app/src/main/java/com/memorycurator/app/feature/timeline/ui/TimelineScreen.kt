package com.memorycurator.app.feature.timeline.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.memorycurator.app.feature.albums.model.Album
import com.memorycurator.app.feature.albums.ui.components.GlassAlbumCard
import com.memorycurator.app.ui.preview.PreviewStockPhotos
import com.memorycurator.app.feature.timeline.model.TimelineGroup
import com.memorycurator.app.ui.theme.GlassTheme
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    groups: List<TimelineGroup>,
    onGroupClick: (TimelineGroup) -> Unit,
    onBestTakesClick: (TimelineGroup) -> Unit,
    onRefresh: () -> Unit = {},
    state: LazyListState = rememberLazyListState(),
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Timeline",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (groups.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No photos found.\nCheck permissions or add photos to your device.",
                        color = Color.White.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = state,
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                items(groups) { group ->
                    TimelineCard(
                        group = group,
                        onClick = { onGroupClick(group) }
                    ) { onBestTakesClick(group) }
                }
            }
        }
    }
}

@Composable
fun TimelineCard(
    group: TimelineGroup,
    onClick: () -> Unit,
    onBestTakesClick: () -> Unit
) {
    val representativePhoto = group.photos.firstOrNull()
    val date = remember(representativePhoto?.dateTaken) {
        Date(representativePhoto?.dateTaken ?: 0L)
    }

    // Date Formatters
    val monthFormat = remember { SimpleDateFormat("MMM", Locale.getDefault()) }
    val dayFormat = remember { SimpleDateFormat("dd", Locale.getDefault()) }
    val yearFormat = remember { SimpleDateFormat("yyyy", Locale.getDefault()) }
    val dayOfWeekFormat = remember { SimpleDateFormat("EEEE", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Date Text Column (The Timeline Anchor)
        Column(
            modifier = Modifier.width(45.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = monthFormat.format(date).uppercase(),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = dayFormat.format(date),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = yearFormat.format(date),
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // 2. Timeline Line and Dot
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(2.dp)
                .background(Color.White.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            // The Dot
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(Color.White.copy(alpha = 0.2f), shape = CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // 3. The Glass Card (Consistent with Albums/Maps)
        val album = remember(group, representativePhoto) {
            Album(
                folderName = group.title,
                thumbnailUri = representativePhoto?.contentUri?.toString() ?: "",
                photoCount = group.photos.size
            )
        }

        Box(modifier = Modifier.padding(vertical = 8.dp).weight(1f)) {
            GlassAlbumCard(
                album = album,
                title = dayOfWeekFormat.format(date),
                onClick = onClick,
                onBestTakesClick = onBestTakesClick
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TimelineScreenPreview() {
    val mockGroup1 = TimelineGroup(
        title = "Summer Trip",
        photos = PreviewStockPhotos.getPhotos(5)
    )
    val mockGroup2 = TimelineGroup(
        title = "Family Dinner",
        photos = PreviewStockPhotos.getPhotos(3)
    )

    GlassTheme {
        Box(modifier = Modifier.background(Color.Black)) {
            TimelineScreen(
                groups = listOf(mockGroup1, mockGroup2),
                onGroupClick = {},
                onBestTakesClick = {}
            )
        }
    }
}
