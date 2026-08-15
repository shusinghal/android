package com.memorycurator.app.ui.preview

import android.net.Uri
import com.memorycurator.app.data.media.MediaPhoto

/**
 * Stock photos for Compose Previews.
 * Using common high-quality placeholder images.
 */
object PreviewStockPhotos {
    private val urls = listOf(
        "https://picsum.photos/id/10/800/800",
        "https://picsum.photos/id/11/800/800",
        "https://picsum.photos/id/12/800/800",
        "https://picsum.photos/id/13/800/800",
        "https://picsum.photos/id/14/800/800",
        "https://picsum.photos/id/15/800/800",
        "https://picsum.photos/id/16/800/800",
        "https://picsum.photos/id/17/800/800",
        "https://picsum.photos/id/18/800/800",
        "https://picsum.photos/id/19/800/800"
    )

    val photos: List<MediaPhoto> = urls.mapIndexed { index, url ->
        MediaPhoto(
            id = index.toLong(),
            contentUri = Uri.parse(url),
            dateTaken = System.currentTimeMillis() - (index * 86400000L),
            dateModified = System.currentTimeMillis() - (index * 86400000L),
            isVideo = false
        )
    }

    fun getPhotos(count: Int): List<MediaPhoto> {
        return List(count) { i -> photos[i % photos.size] }
    }
}
