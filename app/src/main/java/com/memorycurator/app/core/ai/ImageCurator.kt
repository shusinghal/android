package com.memorycurator.app.core.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.memorycurator.app.data.media.MediaPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.pow

enum class RejectionReason {
    NONE,
    BLURRY,
    EYES_CLOSED,
    BAD_EXPRESSION,
    POOR_LIGHTING,
    POOR_COMPOSITION,
    DUPLICATE,
    LOW_QUALITY,
    MANUAL;

    // --- UI DISPLAY HELPERS FOR OTHER SCREENS ---

    val label: String
        get() = when (this) {
            NONE -> "Best Take"
            BLURRY -> "Blurry Focus"
            EYES_CLOSED -> "Eyes Closed"
            BAD_EXPRESSION -> "Awkward Expression"
            POOR_LIGHTING -> "Poor Lighting"
            POOR_COMPOSITION -> "Poor Composition"
            DUPLICATE -> "Similar Take"
            LOW_QUALITY -> "Subpar Quality"
            MANUAL -> "Manually Removed"
        }

    val subtitle: String
        get() = when (this) {
            NONE -> "High quality, sharp, and well-framed."
            BLURRY -> "Image appears out of focus or motion-blurred."
            EYES_CLOSED -> "One or more people blinked or closed their eyes."
            BAD_EXPRESSION -> "Subject looking away or awkward facial expression."
            POOR_LIGHTING -> "Image is underexposed, dark, or washed out."
            POOR_COMPOSITION -> "Subject off-center or framing could be improved."
            DUPLICATE -> "A similar or better shot exists in this burst."
            LOW_QUALITY -> "Does not meet overall quality standards."
            MANUAL -> "Removed by user preference."
        }

    val icon: ImageVector
        get() = when (this) {
            NONE -> Icons.Default.CheckCircle
            BLURRY -> Icons.Default.BlurOn
            EYES_CLOSED -> Icons.Default.VisibilityOff
            BAD_EXPRESSION -> Icons.Default.SentimentDissatisfied
            POOR_LIGHTING -> Icons.Default.WbSunny
            POOR_COMPOSITION -> Icons.Default.Crop
            DUPLICATE -> Icons.Default.CopyAll
            LOW_QUALITY -> Icons.Default.ThumbDown
            MANUAL -> Icons.Default.Block
        }
}

data class ScoreBreakdown(
    val technicalScore: Float,   // Sharpness & Lighting (0.0 - 1.0)
    val expressionScore: Float,  // Smiles & Eye Openness (0.0 - 1.0)
    val aestheticScore: Float,   // Color harmony & composition (0.0 - 1.0)
    val symmetryScore: Float     // Centering & Framing (0.0 - 1.0)
)

data class CuratedResult(
    val photo: MediaPhoto,
    val score: Float,
    val isBestTake: Boolean,
    val rejectionReason: RejectionReason = RejectionReason.NONE,
    val aiDescription: String = "",
    val breakdown: ScoreBreakdown? = null,
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
    ): List<CuratedResult> = withContext(Dispatchers.Default) {
        val rawResults = mutableListOf<CuratedResult>()

        for ((index, photo) in photos.withIndex()) {
            try {
                onProgress(index + 1, photos.size)
                val image = InputImage.fromFilePath(context, photo.contentUri)
                
                val faces = faceDetector.process(image).await()
                val labels = labeler.process(image).await()

                // Load downsampled bitmap with correct EXIF orientation
                val smallBitmap = loadOrientedBitmap(context, photo.contentUri)
                
                // Single-pass Technical Metrics (Sharpness + Lighting combined)
                val (sharpness, lighting) = calculateTechnicalMetrics(smallBitmap)

                // Aesthetic Analysis
                val aestheticResult = aestheticScorer.calculateAestheticScore(smallBitmap, faces, labels)

                // Clean up bitmap memory immediately
                smallBitmap.recycle()

                // Cache metadata for clustering
                photoMetadataCache[photo.id] = PhotoMetadata(labels, faces.size, photo.dateTaken)

                // Evaluate with specific decision tree & descriptions
                val evaluation = evaluateQualityWithDescription(
                    faces = faces, 
                    labels = labels, 
                    width = image.width, 
                    height = image.height,
                    sharpness = sharpness,
                    lighting = lighting,
                    aestheticResult = aestheticResult
                )
                
                val isBestTake = evaluation.score >= 0.6f && evaluation.reason == RejectionReason.NONE
                
                // If not a best take and no specific reason found, assign LOW_QUALITY
                val finalReason = if (!isBestTake && evaluation.reason == RejectionReason.NONE) {
                    RejectionReason.LOW_QUALITY
                } else {
                    evaluation.reason
                }
                
                rawResults.add(
                    CuratedResult(
                        photo = photo,
                        score = evaluation.score,
                        isBestTake = isBestTake,
                        rejectionReason = finalReason,
                        aiDescription = evaluation.description,
                        breakdown = evaluation.breakdown
                    )
                )
            } catch (e: Exception) {
                rawResults.add(
                    CuratedResult(
                        photo = photo, 
                        score = 0f, 
                        isBestTake = false, 
                        rejectionReason = RejectionReason.LOW_QUALITY,
                        aiDescription = "Failed to process photo."
                    )
                )
            }
        }

        return@withContext applyClustering(rawResults)
    }

    private data class AnalysisEvaluation(
        val score: Float,
        val reason: RejectionReason,
        val description: String,
        val breakdown: ScoreBreakdown
    )

    private fun evaluateQualityWithDescription(
        faces: List<Face>, 
        labels: List<ImageLabel>,
        width: Int,
        height: Int,
        sharpness: Float,
        lighting: Float,
        aestheticResult: AestheticScorer.AestheticResult
    ): AnalysisEvaluation {
        
        val technicalScore = (sharpness * 0.65f) + (lighting * 0.35f)

        // 🔴 1. Hard Technical Filters (Highest Priority)
        if (sharpness < 0.2f) {
            return AnalysisEvaluation(
                score = 0.2f,
                reason = RejectionReason.BLURRY,
                description = "Image is out of focus or motion-blurred.",
                breakdown = ScoreBreakdown(technicalScore, 0f, aestheticResult.overallScore, 0f)
            )
        }
        if (lighting < 0.2f) {
            return AnalysisEvaluation(
                score = 0.25f,
                reason = RejectionReason.POOR_LIGHTING,
                description = "Poor lighting conditions (underexposed or overexposed).",
                breakdown = ScoreBreakdown(technicalScore, 0f, aestheticResult.overallScore, 0f)
            )
        }

        // 🟢 2. Scenery / Landscape Evaluation (No Faces)
        if (faces.isEmpty()) {
            var contentScore = 0.3f 
            val sceneryLabels = setOf("Nature", "Landscape", "Architecture", "Sunset", "Beach", "Mountain", "Food", "Pet", "Dog", "Cat", "Flower")
            
            val detectedCategories = labels
                .filter { it.confidence > 0.7f }
                .map { it.text }
                .filter { label -> sceneryLabels.any { q -> label.contains(q, ignoreCase = true) } }

            contentScore += (detectedCategories.size * 0.12f).coerceAtMost(0.4f)
            val finalScore = ((contentScore * 0.4f) + (technicalScore * 0.3f) + (aestheticResult.overallScore * 0.3f)).coerceIn(0f, 1f)

            val reason = when {
                finalScore >= 0.6f -> RejectionReason.NONE
                aestheticResult.overallScore < 0.35f -> RejectionReason.POOR_COMPOSITION
                else -> RejectionReason.LOW_QUALITY
            }

            val desc = if (detectedCategories.isNotEmpty()) {
                "Scenery detected (${detectedCategories.joinToString()})."
            } else {
                "General object photo."
            }

            return AnalysisEvaluation(
                score = finalScore,
                reason = reason,
                description = desc,
                breakdown = ScoreBreakdown(technicalScore, 0f, aestheticResult.overallScore, 0.5f)
            )
        }

        // 🔵 3. Human / Portrait Evaluation (With Faces)
        var totalExpressionScore = 0f
        var totalPoseScore = 0f
        var eyesClosedCount = 0
        var wideSmilesCount = 0
        
        for (face in faces) {
            val smile = face.smilingProbability ?: 0f
            if (smile > 0.65f) wideSmilesCount++
            
            val leftEye = face.leftEyeOpenProbability ?: 1f
            val rightEye = face.rightEyeOpenProbability ?: 1f
            if (leftEye < 0.4f || rightEye < 0.4f) eyesClosedCount++
            
            val eyeOpen = (leftEye + rightEye) / 2f
            val expressionQuality = (smile * 0.6f) + (eyeOpen * 0.4f)
            totalExpressionScore += expressionQuality

            val yAngle = abs(face.headEulerAngleY) 
            val zAngle = abs(face.headEulerAngleZ)
            val poseQuality = (1f - (yAngle / 50f).coerceIn(0f, 1f)) * (1f - (zAngle / 35f).coerceIn(0f, 1f))
            totalPoseScore += poseQuality
        }

        val avgExpression = totalExpressionScore / faces.size
        val avgPose = totalPoseScore / faces.size
        val contentScore = (avgExpression * 0.75f) + (avgPose * 0.25f)

        // Symmetry & Centering
        val collectiveLeft = faces.minOf { it.boundingBox.left }
        val collectiveRight = faces.maxOf { it.boundingBox.right }
        val groupCenter = (collectiveLeft + collectiveRight) / 2f
        val symmetryScore = 1f - (abs(groupCenter - (width / 2f)) / width).coerceIn(0f, 1f)

        val rawScore = ((contentScore * 0.4f) + (technicalScore * 0.2f) + 
                        (aestheticResult.overallScore * 0.25f) + (symmetryScore * 0.15f)).coerceIn(0f, 1f)

        // 🎯 SPECIFIC REASON DECISION TREE (Prioritized)
        val (reason, description) = when {
            eyesClosedCount > 0 && !aestheticResult.isIntentionalClosedEyes -> {
                RejectionReason.EYES_CLOSED to "$eyesClosedCount person(s) blinked or closed their eyes."
            }
            avgPose < 0.4f || avgExpression < 0.3f -> {
                RejectionReason.BAD_EXPRESSION to "Subject looking away or awkward facial expression."
            }
            aestheticResult.overallScore < 0.35f || symmetryScore < 0.3f -> {
                RejectionReason.POOR_COMPOSITION to "Subject off-center or poor composition."
            }
            rawScore < 0.6f -> {
                RejectionReason.LOW_QUALITY to "Overall quality below threshold."
            }
            else -> {
                RejectionReason.NONE to "High quality portrait shot."
            }
        }

        val breakdown = ScoreBreakdown(
            technicalScore = technicalScore,
            expressionScore = contentScore,
            aestheticScore = aestheticResult.overallScore,
            symmetryScore = symmetryScore
        )

        return AnalysisEvaluation(
            score = if (reason != RejectionReason.NONE) rawScore * 0.8f else rawScore,
            reason = reason,
            description = description,
            breakdown = breakdown
        )
    }

    /**
     * Single-pass Technical Analysis: Calculates Sharpness (Laplacian Variance) 
     * and Lighting (Avg Luminance) in ONE loop to save CPU and memory.
     */
    private fun calculateTechnicalMetrics(bitmap: Bitmap): Pair<Float, Float> {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        var totalLuminance = 0f
        var sumLaplacian = 0.0
        var sumSqLaplacian = 0.0
        val count = (width - 2) * (height - 2).toDouble()
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                val pixel = pixels[index]
                
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
                
                totalLuminance += lum

                if (x in 1 until width - 1 && y in 1 until height - 1) {
                    val left = getLuminance(pixels[index - 1])
                    val right = getLuminance(pixels[index + 1])
                    val top = getLuminance(pixels[index - width])
                    val bottom = getLuminance(pixels[index + width])
                    
                    val laplacian = (left + right + top + bottom - 4 * lum)
                    sumLaplacian += laplacian
                    sumSqLaplacian += laplacian * laplacian
                }
            }
        }
        
        val avgLuminance = totalLuminance / pixels.size
        val lightingScore = when {
            avgLuminance < 0.2f -> avgLuminance * 2f
            avgLuminance > 0.8f -> 1f - (avgLuminance - 0.8f) * 3f
            else -> 1.0f
        }.coerceIn(0f, 1f)

        val variance = (sumSqLaplacian / count) - (sumLaplacian / count).pow(2.0)
        val sharpnessScore = (variance.toFloat() * 300f).coerceIn(0f, 1f)

        return sharpnessScore to lightingScore
    }

    private fun getLuminance(pixel: Int): Float {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return (0.299f * r + 0.587f * g + 0.114f * b) / 255f
    }

    /**
     * Loads small bitmap while correctly applying EXIF Orientation.
     */
    private fun loadOrientedBitmap(context: Context, uri: Uri): Bitmap {
        return try {
            val options = BitmapFactory.Options().apply {
                inSampleSize = 4 // 4x downsample keeps optimal detail for blur detection
            }
            
            val bitmap = context.contentResolver.openInputStream(uri).use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return Bitmap.createBitmap(100, 100, Bitmap.Config.RGB_565)

            val orientation = context.contentResolver.openInputStream(uri).use { stream ->
                if (stream != null) ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, 
                    ExifInterface.ORIENTATION_NORMAL
                ) else ExifInterface.ORIENTATION_NORMAL
            }

            val rotationDegrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }

            if (rotationDegrees != 0f) {
                val matrix = Matrix().apply { postRotate(rotationDegrees) }
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                bitmap.recycle()
                rotated
            } else {
                bitmap
            }
        } catch (e: Exception) {
            Bitmap.createBitmap(100, 100, Bitmap.Config.RGB_565)
        }
    }

    private fun applyClustering(results: List<CuratedResult>): List<CuratedResult> {
        if (results.isEmpty()) return results
        
        val sorted = results.sortedBy { it.photo.dateTaken }
        val clustered = mutableListOf<CuratedResult>()
        var currentClusterId: String? = null
        
        for (i in sorted.indices) {
            val current = sorted[i]
            val prev = if (i > 0) sorted[i-1] else null
            
            val timeDiff = if (prev != null) abs(current.photo.dateTaken - prev.photo.dateTaken) else Long.MAX_VALUE
            val isSemanticallySimilar = if (prev != null) checkSemanticSimilarity(current.photo.id, prev.photo.id) else false

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
        
        val groupedResults = clustered.groupBy { it.clusterId }.flatMap { (clusterId, items) ->
            if (clusterId == null) return@flatMap items
            
            val bestInCluster = items.maxByOrNull { it.score }
            items.map { item ->
                if (item == bestInCluster) {
                    item 
                } else {
                    // Preserve original specific reason if it was already bad (e.g. BLURRY),
                    // only override to DUPLICATE if it was otherwise a good shot (NONE).
                    val updatedReason = if (item.rejectionReason == RejectionReason.NONE) {
                        RejectionReason.DUPLICATE 
                    } else {
                        item.rejectionReason
                    }

                    item.copy(
                        isBestTake = false, 
                        rejectionReason = updatedReason,
                        aiDescription = "${item.aiDescription} (Similar to higher-scoring photo)."
                    )
                }
            }
        }

        photoMetadataCache.clear()
        return groupedResults
    }

    private fun checkSemanticSimilarity(id1: Long, id2: Long): Boolean {
        val meta1 = photoMetadataCache[id1] ?: return false
        val meta2 = photoMetadataCache[id2] ?: return false

        if (meta1.faceCount != meta2.faceCount) return false

        val labels1 = meta1.labels.filter { it.confidence > 0.7f }.map { it.text }.toSet()
        val labels2 = meta2.labels.filter { it.confidence > 0.7f }.map { it.text }.toSet()
        
        if (labels1.isEmpty() || labels2.isEmpty()) return false

        val intersect = labels1.intersect(labels2).size
        val union = labels1.union(labels2).size
        val similarity = intersect.toFloat() / union

        return similarity > 0.7f
    }
}
