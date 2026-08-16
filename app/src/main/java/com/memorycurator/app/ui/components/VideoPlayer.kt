package com.memorycurator.app.ui.components

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.memorycurator.app.ui.theme.MemoryCuratorTheme

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    videoUri: Uri,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    showControls : Boolean = true,
    autoPlay: Boolean = false
) {
    val context = LocalContext.current

    val exoPlayer = remember(videoUri, active) {
        if (!active) return@remember null
        try {
            ExoPlayer.Builder(context).build().apply {
                val mediaItem = MediaItem.fromUri(videoUri)
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = autoPlay
            }
        } catch (e: Exception) {
            null
        }
    }

    if (exoPlayer == null) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(videoUri)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
        return
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = {
            PlayerView(context).apply {
                player = exoPlayer
                useController = showControls
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        update = { view ->
            view.player = exoPlayer
        }
    )

    DisposableEffect(videoUri, active) {
        onDispose {
            exoPlayer?.release()
        }
    }
}

@Preview(showBackground = true)
@Composable
fun VideoPlayerPreview() {
    MemoryCuratorTheme {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text("Video Player Preview (ExoPlayer requires context)", color = Color.White)
        }
    }
}
