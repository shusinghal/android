package com.memorycurator.app.ui.models

data class PhotoItem(
    val id: Long,
    val imageUrl: String,
    val score: Float,
    val badge: String
)