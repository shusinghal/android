package com.memorycurator.app.feature.albums.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.memorycurator.app.feature.albums.model.Album

@Composable
fun GlassAlbumCard(
    album: Album,
    onClick: () -> Unit
) {

    Box(

        modifier = Modifier

            .fillMaxWidth()

            .clip(
                RoundedCornerShape(30.dp)
            )

            .background(

                brush = Brush.verticalGradient(

                    colors = listOf(

                        Color.White.copy(alpha = 0.12f),

                        Color.White.copy(alpha = 0.03f)
                    )
                )
            )

            .border(

                width = 1.dp,

                color = Color.White.copy(alpha = 0.08f),

                shape = RoundedCornerShape(30.dp)
            )

            .clickable {
                onClick()
            }
    ) {

        Column {

            AsyncImage(

                model = ImageRequest.Builder(
                    LocalContext.current
                )
                    .data(album.thumbnailUri)
                    .crossfade(true)
                    .build(),

                contentDescription = null,

                modifier = Modifier

                    .fillMaxWidth()

                    .aspectRatio(1f)

                    .clip(
                        RoundedCornerShape(24.dp)
                    ),

                contentScale =
                    ContentScale.Crop
            )

            Column(

                modifier = Modifier
                    .padding(14.dp),

                verticalArrangement =
                    Arrangement.spacedBy(4.dp)
            ) {

                Text(

                    text = album.folderName,

                    color = Color.White,

                    fontSize = 17.sp,

                    fontWeight = FontWeight.SemiBold
                )

                Text(

                    text =
                        "${album.photoCount} photos",

                    color =
                        Color.White.copy(
                            alpha = 0.6f
                        ),

                    fontSize = 13.sp
                )
            }
        }
    }
}