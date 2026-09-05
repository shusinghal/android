package com.memorycurator.app.ui.preview

import android.net.Uri
import com.memorycurator.app.data.media.MediaPhoto

/**
 * Stock photos for Compose Previews.
 * Uses local resources and negative IDs to prevent production data pollution.
 */
object PreviewStockPhotos {
    
    // Using a local resource ensures the Preview can render it without internet
    private val mockUri = Uri.parse("android.resource://com.memorycurator.app/drawable/bg_main")

    val photos: List<MediaPhoto> = List(10) { index ->
        MediaPhoto(
            // Use negative IDs to ensure they never clash with real MediaStore IDs (which are positive)
            id = -(index + 1).toLong(),
            contentUri = mockUri,
            dateTaken = System.currentTimeMillis() - (index * 86400000L),
            dateModified = System.currentTimeMillis() - (index * 86400000L),
            isVideo = false,
            aiScore = if (index % 3 == 0) 0.85f else 0.45f,
            isBestTake = index % 3 == 0,
            rejectionReason = if (index % 3 != 0) "BLURRY" else null
        )
    }

    fun getPhotos(count: Int): List<MediaPhoto> {
        return List(count) { i -> photos[i % photos.size] }
    }
}
