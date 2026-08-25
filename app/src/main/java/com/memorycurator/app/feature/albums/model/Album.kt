package com.memorycurator.app.feature.albums.model

data class Album(

    val folderName: String,

    val thumbnailUri: String,

    val photoCount: Int,
    
    val lastModified: Long = 0
)