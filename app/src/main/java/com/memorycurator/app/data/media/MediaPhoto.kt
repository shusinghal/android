package com.memorycurator.app.data.media

import android.net.Uri

data class MediaPhoto(

    val id: Long,

    val contentUri: Uri,

    val dateTaken: Long,

    val dateModified: Long,

    val isVideo: Boolean = false,

    val aiScore: Float = -1f,

    val isBestTake: Boolean = false,

    val rejectionReason: String? = null
)