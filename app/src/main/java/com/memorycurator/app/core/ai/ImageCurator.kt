package com.memorycurator.app.core.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
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
import kotlin.math.pow
import kotlin.math.sqrt

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
    private val aestheticScorer = AestheticScorer()

    // Cache to store semantic "fingerprints" of images for better similarity detection
    private val photoMetadataCache = mutableMapOf<Long, PhotoMetadata>()

    data class PhotoMetadata(
        val labels: List<ImageLabel>,
        val faceCount: Int,
        val dateTaken: Long
    )

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

                // Technical Analysis (Blur & Lighting)
                val smallBitmap = loadSmallBitmap(context, photo.contentUri)
                val sharpness = calculateSharpness(smallBitmap)
                val lighting = calculateLighting(smallBitmap)

                // Aesthetic Analysis (Rule of Thirds, Harmony, Context)
                val aestheticResult = aestheticScorer.calculateAestheticScore(smallBitmap, faces, labels)

                // Store metadata for clustering
                photoMetadataCache[photo.id] = PhotoMetadata(labels, faces.size, photo.dateTaken)

                val (score, reason) = calculateQualityScoreWithReason(
                    faces, 
                    labels, 
                    image.width, 
                    image.height,
                    sharpness,
                    lighting,
                    aestheticResult
                )
                
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
            
            // 1. Time-based Similarity (Burst)
            val timeDiff = if (prev != null) abs(current.photo.dateTaken - prev.photo.dateTaken) else Long.MAX_VALUE
            
            // 2. Semantic Similarity (Same background/people even if time is slightly apart)
            val isSemanticallySimilar = if (prev != null) {
                checkSemanticSimilarity(current.photo.id, prev.photo.id)
            } else false

            // Cluster if taken within 2 seconds OR if semantically very similar within a wider window (30s)
            if (prev != null && (timeDiff < 2000 || (timeDiff < 30000 && isSemanticallySimilar))) {
                if (currentClusterId == null) {
                    currentClusterId = "cluster_${prev.photo.id}"
                    val lastIdx = clustered.size - 1
                    clustered[lastIdx] = clustered[lastIdx].copy(clusterId = currentClusterId)
                }
            } else {
                currentClusterId = null
            }
            
            clustered.add(current.copy(clusterId = currentClusterId))
        }
        
        // Final pass: Within each cluster, keep only the best one
        val groupedResults = clustered.groupBy { it.clusterId }.flatMap { (clusterId, items) ->
            if (clusterId == null) return@flatMap items
            
            val bestInCluster = items.maxByOrNull { it.score }
            items.map { item ->
                if (item == bestInCluster) {
                    item 
                } else {
                    item.copy(
                        isBestTake = false, 
                        rejectionReason = if (item.rejectionReason == RejectionReason.NONE) RejectionReason.DUPLICATE else item.rejectionReason
                    )
                }
            }
        }

        // Clean up cache
        photoMetadataCache.clear()
        return groupedResults
    }

    private fun checkSemanticSimilarity(id1: Long, id2: Long): Boolean {
        val meta1 = photoMetadataCache[id1] ?: return false
        val meta2 = photoMetadataCache[id2] ?: return false

        // Must have same number of people
        if (meta1.faceCount != meta2.faceCount) return false

        // Check labels (background/objects)
        val labels1 = meta1.labels.filter { it.confidence > 0.7f }.map { it.text }.toSet()
        val labels2 = meta2.labels.filter { it.confidence > 0.7f }.map { it.text }.toSet()
        
        if (labels1.isEmpty() || labels2.isEmpty()) return false

        // Calculate Jaccard similarity between label sets
        val intersect = labels1.intersect(labels2).size
        val union = labels1.union(labels2).size
        val similarity = intersect.toFloat() / union

        // High overlap in labels suggests same background/scene
        return similarity > 0.7f
    }

    private fun calculateQualityScoreWithReason(
        faces: List<Face>, 
        labels: List<ImageLabel>,
        width: Int,
        height: Int,
        sharpness: Float,
        lighting: Float,
        aestheticResult: AestheticScorer.AestheticResult
    ): Pair<Float, RejectionReason> {
        
        // 1. Technical Filtering (Fail early on poor quality)
        if (sharpness < 0.2f) return 0.2f to RejectionReason.BLURRY
        if (lighting < 0.2f) return 0.25f to RejectionReason.POOR_LIGHTING

        if (faces.isEmpty()) {
            // Landscape/Architecture scoring
            var score = 0.3f 
            val sceneryLabels = setOf(
                "Nature", "Landscape", "Architecture", "Sunset", "Beach", 
                "Mountain", "Food", "Pet", "Dog", "Cat", "Flower"
            )
            val matches = labels.filter { it.confidence > 0.7f }.count { label ->
                sceneryLabels.any { q -> label.text.contains(q, ignoreCase = true) }
            }
            score += (matches * 0.12f).coerceAtMost(0.4f)
            
            // Factor in technical and aesthetic quality
            val finalSceneryScore = (score * 0.4f) + (sharpness * 0.2f) + (lighting * 0.1f) + (aestheticResult.overallScore * 0.3f)
            return finalSceneryScore.coerceIn(0f, 1f) to RejectionReason.NONE
        }

        // --- ENHANCED EXPRESSION & SYMMETRY ANALYSIS ---
        var totalExpressionScore = 0f
        var totalPoseScore = 0f
        var anyEyesClosed = false
        var wideSmilesCount = 0
        
        for (face in faces) {
            val smile = face.smilingProbability ?: 0f
            if (smile > 0.7f) wideSmilesCount++
            
            val leftEye = face.leftEyeOpenProbability ?: 1f
            val rightEye = face.rightEyeOpenProbability ?: 1f
            val eyeOpen = (leftEye + rightEye) / 2f
            
            if (leftEye < 0.4f || rightEye < 0.4f) {
                anyEyesClosed = true
            }
            
            val expressionQuality = (smile * 0.6f) + (eyeOpen * 0.4f)
            totalExpressionScore += expressionQuality

            val yAngle = abs(face.headEulerAngleY) 
            val zAngle = abs(face.headEulerAngleZ)
            val poseQuality = (1f - (yAngle / 50f).coerceIn(0f, 1f)) * (1f - (zAngle / 35f).coerceIn(0f, 1f))
            totalPoseScore += poseQuality
        }

        val avgExpression = totalExpressionScore / faces.size
        val avgPose = totalPoseScore / faces.size

        // Group Symmetry & Centering
        val collectiveLeft = faces.minOf { it.boundingBox.left }
        val collectiveRight = faces.maxOf { it.boundingBox.right }
        val groupCenter = (collectiveLeft + collectiveRight) / 2f
        val symmetryScore = 1f - (abs(groupCenter - (width / 2f)) / width).coerceIn(0f, 1f)

        val completenessBonus = (faces.size * 0.04f).coerceAtMost(0.12f)
        val smileBonus = (wideSmilesCount.toFloat() / faces.size) * 0.1f

        // Final Composite Score (Production Weighting)
        // 40% Content, 20% Technical, 25% Aesthetic/Composition, 15% Symmetry
        val contentScore = (avgExpression * 0.75f) + (avgPose * 0.25f)
        val technicalScore = (sharpness * 0.65f) + (lighting * 0.35f)
        
        var finalScore = (contentScore * 0.4f) + (technicalScore * 0.2f) + 
                         (aestheticResult.overallScore * 0.25f) + (symmetryScore * 0.15f) + 
                         completenessBonus + smileBonus

        var reason = RejectionReason.NONE
        
        // Handle "Artistic" Closed Eyes (Prayer, Sleep, Meditation)
        if (anyEyesClosed) {
            if (aestheticResult.isIntentionalClosedEyes) {
                // High aesthetic score + spiritual/sleep context = Intentional.
                // We give a small "serenity" penalty but NOT a rejection.
                finalScore *= 0.9f 
                reason = RejectionReason.NONE
            } else {
                // Likely a blink.
                finalScore *= 0.6f 
                reason = RejectionReason.EYES_CLOSED
            }
        }

        return finalScore.coerceIn(0f, 1f) to reason
    }

    private fun calculateSharpness(bitmap: Bitmap): Float {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        var sum = 0.0
        var sumSq = 0.0
        val count = (width - 2) * (height - 2).toDouble()
        
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val center = getLuminance(pixels[y * width + x])
                val left = getLuminance(pixels[y * width + (x - 1)])
                val right = getLuminance(pixels[y * width + (x + 1)])
                val top = getLuminance(pixels[(y - 1) * width + x])
                val bottom = getLuminance(pixels[(y + 1) * width + x])
                
                // Laplacian Filter
                val laplacian = (left + right + top + bottom - 4 * center)
                sum += laplacian
                sumSq += laplacian * laplacian
            }
        }
        
        val variance = (sumSq / count) - (sum / count).pow(2.0)
        // Standard threshold for 'sharp' images: variance > 0.002
        return (variance.toFloat() * 500f).coerceIn(0f, 1f)
    }

    private fun calculateLighting(bitmap: Bitmap): Float {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        var totalLuminance = 0f
        for (pixel in pixels) {
            totalLuminance += getLuminance(pixel)
        }
        
        val avgLuminance = totalLuminance / pixels.size
        
        // Ideal luminance is 0.4 - 0.7. Penalize outliers.
        return when {
            avgLuminance < 0.2f -> avgLuminance * 2f
            avgLuminance > 0.8f -> 1f - (avgLuminance - 0.8f) * 3f
            else -> 1.0f
        }.coerceIn(0f, 1f)
    }

    private fun getLuminance(pixel: Int): Float {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return (0.299f * r + 0.587f * g + 0.114f * b) / 255f
    }

    private fun loadSmallBitmap(context: Context, uri: android.net.Uri): Bitmap {
        return try {
            val options = BitmapFactory.Options().apply {
                inSampleSize = 8 // Downsample for performance
            }
            context.contentResolver.openInputStream(uri).use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: Bitmap.createBitmap(100, 100, Bitmap.Config.RGB_565)
        } catch (e: Exception) {
            Bitmap.createBitmap(100, 100, Bitmap.Config.RGB_565)
        }
    }
}
