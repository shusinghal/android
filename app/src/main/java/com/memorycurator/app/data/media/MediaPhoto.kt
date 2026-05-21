package com.memorycurator.app.data.media

import android.net.Uri

data class MediaPhoto(

    val id: Long,

    val contentUri: Uri,

    val dateTaken: Long
)