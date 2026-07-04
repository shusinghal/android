package com.memorycurator.app.core.ai

import android.content.Context
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.memorycurator.app.data.media.MediaPhoto
import kotlinx.coroutines.tasks.await

data class CuratedResult(
    val photo: MediaPhoto,
    val score: Float
)

interface ImageCurator {
    suspend fun analyzePhotos(context: Context, photos: List<MediaPhoto>): List<CuratedResult>
    suspend fun filterBestTakes(context: Context, photos: List<MediaPhoto>): List<MediaPhoto>
}

class ImageCuratorImpl : ImageCurator {
    
    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()
    )

    private val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)

    override suspend fun analyzePhotos(context: Context, photos: List<MediaPhoto>): List<CuratedResult> {
        val results = mutableListOf<CuratedResult>()

        for (photo in photos) {
            try {
                val image = InputImage.fromFilePath(context, photo.contentUri)
                
                // Concurrent analysis could be faster but let's stay safe for on-device resources
                val faces = faceDetector.process(image).await()
                val labels = labeler.process(image).await()

                val score = calculateQualityScore(faces, labels)
                results.add(CuratedResult(photo, score))
            } catch (e: Exception) {
                // Log or handle error (e.g., file not found or unsupported format)
                results.add(CuratedResult(photo, 0f))
            }
        }
        return results
    }

    override suspend fun filterBestTakes(context: Context, photos: List<MediaPhoto>): List<MediaPhoto> {
        val analysis = analyzePhotos(context, photos)
        // Filter by score threshold (0.6) and sort by best first
        return analysis
            .filter { it.score >= 0.6f }
            .sortedByDescending { it.score }
            .map { it.photo }
            .ifEmpty { 
                // Fallback: if AI thinks all are bad, return the most recent one
                photos.take(1) 
            }
    }

    private fun calculateQualityScore(faces: List<Face>, labels: List<ImageLabel>): Float {
        var score = 0.2f // Base quality

        // 1. Face Analysis (Major factor)
        if (faces.isNotEmpty()) {
            score += 0.3f // Presence of people is usually a 'best take' indicator in a gallery
            
            // Look for the "hero" face
            val bestFace = faces.maxByOrNull { it.smilingProbability ?: 0f }
            bestFace?.let {
                score += (it.smilingProbability ?: 0f) * 0.3f
                score += (it.leftEyeOpenProbability ?: 0f) * 0.1f
                score += (it.rightEyeOpenProbability ?: 0f) * 0.1f
            }
        }

        // 2. Scene/Subject Analysis
        val qualityLabels = setOf(
            "Nature", "Landscape", "Architecture", "Monument", 
            "Event", "Party", "Celebration", "Flower", "Pet"
        )
        
        val matchingLabels = labels.filter { label ->
            qualityLabels.any { q -> label.text.contains(q, ignoreCase = true) } && label.confidence > 0.8f
        }
        
        score += (matchingLabels.size * 0.05f).coerceAtMost(0.2f)

        return score.coerceAtMost(1.0f)
    }
}
