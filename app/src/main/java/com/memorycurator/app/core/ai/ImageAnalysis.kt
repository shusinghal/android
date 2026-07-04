package com.memorycurator.app.core.ai

data class ImageAnalysis(
    val score: Float,
    val labels: List<String> = emptyList(),
    val isBestTake: Boolean = false
)
