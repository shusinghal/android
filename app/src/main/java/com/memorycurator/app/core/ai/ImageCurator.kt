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
import kotlin.math.abs

enum class RejectionReason {
    NONE, BLURRY, EYES_CLOSED, DUPLICATE, POOR_LIGHTING, LOW_QUALITY
}

data class CuratedResult(
    val photo: MediaPhoto,
    val score: Float,
    val isBestTake: Boolean,
    val rejectionReason: RejectionReason = RejectionReason.NONE,
    val clusterId: String? = null
)

interface ImageCurator {
    suspend fun analyzePhotos(
        context: Context, 
        photos: List<MediaPhoto>, 
        onProgress: (Int, Int) -> Unit
    ): List<CuratedResult>
}

class ImageCuratorImpl : ImageCurator {
    
    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()
    )

    private val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)

    override suspend fun analyzePhotos(
        context: Context, 
        photos: List<MediaPhoto>,
        onProgress: (Int, Int) -> Unit
    ): List<CuratedResult> {
        val rawResults = mutableListOf<CuratedResult>()

        for ((index, photo) in photos.withIndex()) {
            try {
                onProgress(index + 1, photos.size)
                val image = InputImage.fromFilePath(context, photo.contentUri)
                
                val faces = faceDetector.process(image).await()
                val labels = labeler.process(image).await()

                val (score, reason) = calculateQualityScoreWithReason(faces, labels)
                
                rawResults.add(
                    CuratedResult(
                        photo = photo,
                        score = score,
                        isBestTake = score >= 0.6f,
                        rejectionReason = reason
                    )
                )
            } catch (e: Exception) {
                rawResults.add(CuratedResult(photo, 0f, false, RejectionReason.LOW_QUALITY))
            }
        }

        // Apply clustering for similar photos taken within 2 seconds
        return applyClustering(rawResults)
    }

    private fun applyClustering(results: List<CuratedResult>): List<CuratedResult> {
        if (results.isEmpty()) return results
        
        val sorted = results.sortedBy { it.photo.dateTaken }
        val clustered = mutableListOf<CuratedResult>()
        
        var currentClusterId: String? = null
        
        for (i in sorted.indices) {
            val current = sorted[i]
            val prev = if (i > 0) sorted[i-1] else null
            
            // If taken within 2 seconds of previous, same cluster
            if (prev != null && abs(current.photo.dateTaken - prev.photo.dateTaken) < 2000) {
                if (currentClusterId == null) {
                    currentClusterId = "cluster_${prev.photo.id}"
                    // Update previous item's clusterId in the list we are building
                    val lastIdx = clustered.size - 1
                    clustered[lastIdx] = clustered[lastIdx].copy(clusterId = currentClusterId)
                }
            } else {
                currentClusterId = null
            }
            
            clustered.add(current.copy(clusterId = currentClusterId))
        }
        
        // Within each cluster, mark only the highest score as "Best Take" if it meets threshold
        // Others in cluster become "Duplicate" if they were previously "Best Take" candidates
        return clustered.groupBy { it.clusterId }.flatMap { (clusterId, items) ->
            if (clusterId == null) return@flatMap items
            
            val bestInCluster = items.maxByOrNull { it.score }
            items.map { item ->
                if (item == bestInCluster) {
                    item // Keep its status
                } else {
                    item.copy(
                        isBestTake = false, 
                        rejectionReason = if (item.rejectionReason == RejectionReason.NONE) RejectionReason.DUPLICATE else item.rejectionReason
                    )
                }
            }
        }
    }

    private fun calculateQualityScoreWithReason(
        faces: List<Face>, 
        labels: List<ImageLabel>
    ): Pair<Float, RejectionReason> {
        var score = 0.3f
        var reason = RejectionReason.NONE

        if (faces.isNotEmpty()) {
            val bestFace = faces.maxByOrNull { it.smilingProbability ?: 0f }
            bestFace?.let {
                val eyesClosed = (it.leftEyeOpenProbability ?: 1f) < 0.4f || (it.rightEyeOpenProbability ?: 1f) < 0.4f
                if (eyesClosed) {
                    score -= 0.2f
                    reason = RejectionReason.EYES_CLOSED
                }
                
                score += (it.smilingProbability ?: 0f) * 0.4f
                score += (it.leftEyeOpenProbability ?: 0f) * 0.1f
            }
        }

        val qualityLabels = setOf("Nature", "Portrait", "Architecture", "Party")
        val matchingLabels = labels.filter { label ->
            qualityLabels.any { q -> label.text.contains(q, ignoreCase = true) } && label.confidence > 0.8f
        }
        score += (matchingLabels.size * 0.1f).coerceAtMost(0.3f)

        return score.coerceAtMost(1.0f) to reason
    }
}
