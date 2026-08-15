package com.memorycurator.app.data.media

import android.net.Uri
import com.memorycurator.app.data.local.MediaEntity

fun MediaEntity.toMediaPhoto(): MediaPhoto {

    return MediaPhoto(

        id = id,

        contentUri = Uri.parse(uri),

        dateTaken = dateTaken,

        dateModified = dateModified,

        isVideo = mimeType?.startsWith("video/") == true
    )
}