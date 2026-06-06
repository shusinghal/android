package com.gallery.photocleaner.model

import android.net.Uri

data class PhotoItem(
    val id: Long,
    val uri: Uri,
    val path: String,
    val score: Float = 0f,
    val isDuplicate: Boolean = false,
    val isProcessed: Boolean = false
)