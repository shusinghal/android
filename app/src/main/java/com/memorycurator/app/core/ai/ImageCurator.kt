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

                val (score, reason) = calculateQualityScoreWithReason(faces, labels, image.width, image.height)
                
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
        labels: List<ImageLabel>,
        width: Int,
        height: Int
    ): Pair<Float, RejectionReason> {
        if (faces.isEmpty()) {
            // Landscape/Architecture scoring
            var score = 0.4f
            val sceneryLabels = setOf("Nature", "Landscape", "Architecture", "Sunset", "Beach", "Mountain")
            val matches = labels.filter { it.confidence > 0.8f }.count { label ->
                sceneryLabels.any { q -> label.text.contains(q, ignoreCase = true) }
            }
            score += (matches * 0.15f).coerceAtMost(0.4f)
            return score.coerceIn(0f, 1f) to RejectionReason.NONE
        }

        // --- ENHANCED EXPRESSION & SYMMETRY ANALYSIS ---
        var totalExpressionScore = 0f
        var totalPoseScore = 0f
        var anyEyesClosed = false
        
        for (face in faces) {
            // 1. Expression Engagement (Smile + Open Eyes)
            val smile = face.smilingProbability ?: 0f
            val eyeOpen = ((face.leftEyeOpenProbability ?: 1f) + (face.rightEyeOpenProbability ?: 1f)) / 2f
            
            if ((face.leftEyeOpenProbability ?: 1f) < 0.4f || (face.rightEyeOpenProbability ?: 1f) < 0.4f) {
                anyEyesClosed = true
            }
            
            // Weighting expressions heavily: Good smile + Engaged eyes
            val expressionQuality = (smile * 0.75f) + (eyeOpen * 0.25f)
            totalExpressionScore += expressionQuality

            // 2. Frontal Posing (Looking at camera via Euler angles)
            val yAngle = abs(face.headEulerAngleY) 
            val zAngle = abs(face.headEulerAngleZ)
            val poseQuality = (1f - (yAngle / 45f).coerceIn(0f, 1f)) * (1f - (zAngle / 30f).coerceIn(0f, 1f))
            totalPoseScore += poseQuality
        }

        val avgExpression = totalExpressionScore / faces.size
        val avgPose = totalPoseScore / faces.size

        // 3. Group Symmetry (Centering)
        val collectiveLeft = faces.minOf { it.boundingBox.left }
        val collectiveRight = faces.maxOf { it.boundingBox.right }
        val groupCenter = (collectiveLeft + collectiveRight) / 2f
        val symmetryScore = 1f - (abs(groupCenter - (width / 2f)) / width).coerceIn(0f, 1f)

        // 4. Group Completeness Bonus
        val completenessBonus = (faces.size * 0.05f).coerceAtMost(0.15f)

        // Final Weighting:
        // Expressions: 60%, Posing: 15%, Symmetry: 10%, Context: 10%, Bonus: up to 15%
        var finalScore = (avgExpression * 0.6f) + (avgPose * 0.15f) + (symmetryScore * 0.1f) + completenessBonus + 0.05f

        var reason = RejectionReason.NONE
        if (anyEyesClosed) {
            finalScore -= 0.35f
            reason = RejectionReason.EYES_CLOSED
        }

        return finalScore.coerceIn(0f, 1f) to reason
    }
}
