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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import com.memorycurator.app.ui.preview.PreviewStockPhotos
import com.memorycurator.app.feature.timeline.model.TimelineGroup
import com.memorycurator.app.ui.theme.GlassTheme
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TimelineScreen(
    groups: List<TimelineGroup>,
    onGroupClick: (TimelineGroup) -> Unit,
    onBestTakesClick: (TimelineGroup) -> Unit,
    state: LazyListState = rememberLazyListState(),
) {
    Box(modifier = Modifier.fillMaxSize()) {
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
            contentPadding = PaddingValues(top = 80.dp, bottom = 120.dp)
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Date Text Column
        Column(
            modifier = Modifier.width(45.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "${monthFormat.format(date).uppercase()} ${dayFormat.format(date)}, ${yearFormat.format(date)}",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(20.dp))

        // 2. Timeline Line and Dot
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(2.dp)
                .background(Color(0x66FFFFFF)),
            contentAlignment = Alignment.Center
        ) {
            // The Dot
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(Color.White.copy(alpha = 0.3f), shape = CircleShape)
                    .border(1.dp, Color.White, CircleShape)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // 3. The Glass Card Container
        Box(
            modifier = Modifier
                .padding(vertical = 12.dp)
                .weight(1f)
                .height(120.dp)
                .clip(RoundedCornerShape(20.dp))
        ) {
            // Background Image
            AsyncImage(
                model = representativePhoto?.contentUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Dark Overlay for readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
            )

            // Text Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    text = "${group.photos.size} ${if (group.photos.size == 1) "item" else "items"}",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 10.sp
                )
            }

            if (representativePhoto?.isVideo == true) {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = "Video",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(48.dp),
                    tint = Color.White.copy(alpha = 0.8f)
                )
            }

            // Best Takes Button
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                Box(
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .clickable { onBestTakesClick() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "BEST TAKES",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
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
