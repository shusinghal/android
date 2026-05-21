package com.memorycurator.app.feature.timeline.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.memorycurator.app.feature.timeline.model.TimelineGroup

@Composable
fun TimelineScreen(
    groups: List<TimelineGroup>
) {

    LazyVerticalGrid(

        columns = GridCells.Adaptive(
            minSize = 120.dp
        ),

        contentPadding = PaddingValues(
            10.dp
        ),

        verticalArrangement =
            Arrangement.spacedBy(8.dp),

        horizontalArrangement =
            Arrangement.spacedBy(8.dp)
    ) {

        groups.forEach { group ->

            item(

                span = {

                    androidx.compose.foundation.lazy.grid.GridItemSpan(
                        maxLineSpan
                    )
                }
            ) {

                Text(

                    text = group.title,

                    color = Color.White,

                    fontWeight =
                        FontWeight.Bold,

                    modifier = Modifier
                        .padding(
                            top = 18.dp,
                            bottom = 8.dp
                        )
                )
            }

            items(group.photos) { photo ->

                AsyncImage(

                    model = ImageRequest.Builder(
                        LocalContext.current
                    )
                        .data(photo.contentUri)
                        .crossfade(true)
                        .build(),

                    contentDescription = null,

                    modifier = Modifier

                        .fillMaxWidth()

                        .aspectRatio(1f),

                    contentScale =
                        ContentScale.Crop
                )
            }
        }
    }
}