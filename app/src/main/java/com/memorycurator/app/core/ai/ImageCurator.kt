package com.memorycurator.app.core.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
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
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.memorycurator.app.data.media.MediaPhoto
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    MANUAL,
    UTILITY,
    NO_SUBJECT;

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
            UTILITY -> "Document / Utility"
            NO_SUBJECT -> "Unclear Subject"
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
            UTILITY -> "Screenshot, receipt, document, or utility capture."
            NO_SUBJECT -> "Lacks a clear, in-focus central subject."
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
            UTILITY -> Icons.Default.Description
            NO_SUBJECT -> Icons.Default.CenterFocusWeak
        }
}

enum class SubjectCategory {
    PEOPLE,
    PETS,
    FOOD,
    SCENERY,
    OBJECTS,
    UTILITY,
    UNCLEAR
}

/**
 * Spatial and category analysis of the main subject.
 * Note: [bounds] are raw pixels relative to the analyzed bitmap.
 */
data class SubjectAnalysis(
    val category: SubjectCategory,
    val bounds: Rect?,
    val normalizedBounds: RectF?,
    val prominence: Float, // 0.0 to 1.0 (subject area / total area)
    val isolationScore: Float, // subject focus vs background
    val mainSubjectName: String
)

data class ScoreBreakdown(
    val technicalScore: Float,   // Sharpness & Lighting (0.0 - 1.0)
    val expressionScore: Float,  // Smiles & Eye Openness or Subject Focus (0.0 - 1.0)
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
    val clusterId: String? = null,
    val mainFaceCount: Int = 0
)

interface ImageCurator {
    suspend fun analyzePhotos(
        context: Context, 
        photos: List<MediaPhoto>, 
        onProgress: (Int, Int) -> Unit,
        onResult: (CuratedResult) -> Unit = {}
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
    
    // Use Object Detection API for accurate subject localization (Requirement 1)
    private val objectDetector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()
    )

    private val aestheticScorer = AestheticScorer()

    data class PhotoMetadata(
        val labels: Set<String>, // Fix: Store string set including Color AI results
        val mainFaceCount: Int,
        val dateTaken: Long,
        val visualSignature: FloatArray
    )

    override suspend fun analyzePhotos(
        context: Context, 
        photos: List<MediaPhoto>,
        onProgress: (Int, Int) -> Unit,
        onResult: (CuratedResult) -> Unit
    ): List<CuratedResult> = withContext(Dispatchers.Default) {
        val photoMetadataCache = ConcurrentHashMap<Long, PhotoMetadata>()
        val progressCounter = AtomicInteger(0)

        val rawResults = coroutineScope {
            photos.map { photo ->
                async {
                    var orientedBitmap: Bitmap? = null
                    try {
                        orientedBitmap = loadOrientedBitmap(context, photo.contentUri)
                        
                        val image = InputImage.fromBitmap(orientedBitmap, 0)
                        val allFaces = faceDetector.process(image).await()
                        val labels = labeler.process(image).await()
                        
                        val detectedObjects = runCatching { 
                            objectDetector.process(image).await() 
                        }.getOrElse { emptyList() }

                        val imgW = orientedBitmap.width.toFloat()
                        val imgH = orientedBitmap.height.toFloat()

                        val mainFaces = allFaces.filter { face ->
                            val normBox = normalizeRect(face.boundingBox, imgW, imgH)
                            val faceArea = normBox.width() * normBox.height()
                            faceArea > 0.02f
                        }.sortedByDescending { it.boundingBox.width() * it.boundingBox.height() }
                         .take(4)

                        val subjectAnalysis = extractSubjectAnalysis(
                            photo = photo,
                            labels = labels,
                            faces = mainFaces,
                            objects = detectedObjects,
                            imgW = imgW,
                            imgH = imgH
                        )

                        val techMetrics = calculateTechnicalMetrics(orientedBitmap, subjectAnalysis)
                        val colorLabels = extractColorLabels(orientedBitmap, subjectAnalysis.bounds)
                        val allLabels = (labels.map { it.text } + colorLabels).toSet()

                        val aestheticResult = aestheticScorer.calculateAestheticScore(
                            bitmap = orientedBitmap, 
                            faces = mainFaces, 
                            labels = labels,
                            subjectBounds = subjectAnalysis.bounds
                        )

                        val semanticContent = buildSemanticDescription(
                            subject = subjectAnalysis,
                            labels = allLabels,
                            faces = mainFaces,
                            techMetrics = techMetrics,
                            aestheticResult = aestheticResult
                        )
                        val visualSig = computeVisualSignature(orientedBitmap)

                        photoMetadataCache[photo.id] = PhotoMetadata(allLabels, mainFaces.size, photo.dateTaken, visualSig)

                        val evaluation = evaluateQuality(
                            faces = mainFaces, 
                            techMetrics = techMetrics,
                            aestheticResult = aestheticResult,
                            subjectAnalysis = subjectAnalysis,
                            width = imgW
                        )
                        
                        val isBestTake = evaluation.score >= 0.6f && evaluation.reason == RejectionReason.NONE
                        
                        val finalReason = if (!isBestTake && evaluation.reason == RejectionReason.NONE) {
                            RejectionReason.LOW_QUALITY
                        } else {
                            evaluation.reason
                        }
                        
                        val result = CuratedResult(
                            photo = photo,
                            score = evaluation.score,
                            isBestTake = isBestTake,
                            rejectionReason = finalReason,
                            aiDescription = semanticContent,
                            breakdown = evaluation.breakdown,
                            mainFaceCount = mainFaces.size
                        )

                        val currentProgress = progressCounter.incrementAndGet()
                        onProgress(currentProgress, photos.size)

                        result
                    } catch (e: Exception) {
                        val currentProgress = progressCounter.incrementAndGet()
                        onProgress(currentProgress, photos.size)
                        
                        CuratedResult(
                            photo = photo, 
                            score = 0f, 
                            isBestTake = false, 
                            rejectionReason = RejectionReason.LOW_QUALITY,
                            aiDescription = "Processing failed: ${e.message}"
                        )
                    } finally {
                        orientedBitmap?.recycle()
                    }
                }
            }.awaitAll()
        }

        val clusteredResults = applyClustering(rawResults, photoMetadataCache)
        
        clusteredResults.forEach { result ->
            onResult(result)
        }

        return@withContext clusteredResults
    }

    private data class AnalysisEvaluation(
        val score: Float,
        val reason: RejectionReason,
        val breakdown: ScoreBreakdown
    )

    private fun extractSubjectAnalysis(
        photo: MediaPhoto,
        labels: List<ImageLabel>,
        faces: List<Face>,
        objects: List<DetectedObject>,
        imgW: Float,
        imgH: Float
    ): SubjectAnalysis {
        // 1. Utility & Screenshot Detection
        val utilityKeywords = setOf("Text", "Font", "Document", "Receipt", "Screenshot", "Label", "Barcode")
        val isUriScreenshot = photo.contentUri.toString().lowercase().contains("screenshot")
        val hasUtilityLabels = labels.count { label -> 
            utilityKeywords.any { kw -> label.text.contains(kw, ignoreCase = true) } 
        } >= 2

        if (isUriScreenshot || hasUtilityLabels) {
            return SubjectAnalysis(
                category = SubjectCategory.UTILITY,
                bounds = null,
                normalizedBounds = null,
                prominence = 0f,
                isolationScore = 0f,
                mainSubjectName = "Document / Utility"
            )
        }

        // 2. People Subject Detection (Primary)
        if (faces.isNotEmpty()) {
            val minX = faces.minOf { it.boundingBox.left }
            val minY = faces.minOf { it.boundingBox.top }
            val maxX = faces.maxOf { it.boundingBox.right }
            val maxY = faces.maxOf { it.boundingBox.bottom }
            val bounds = Rect(minX, minY, maxX, maxY)
            val normalized = normalizeRect(bounds, imgW, imgH)
            val area = normalized.width() * normalized.height()

            return SubjectAnalysis(
                category = SubjectCategory.PEOPLE,
                bounds = bounds,
                normalizedBounds = normalized,
                prominence = area.coerceIn(0f, 1f),
                isolationScore = 0.85f,
                mainSubjectName = if (faces.size == 1) "Person" else "Group"
            )
        }

        // 3. Object Detection (Requirement 1: Replaced Laplacian with ML Kit)
        val primaryObject = objects.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
        if (primaryObject != null) {
            val normalized = normalizeRect(primaryObject.boundingBox, imgW, imgH)
            val area = normalized.width() * normalized.height()
            
            val objLabel = primaryObject.labels.firstOrNull()?.text ?: "Object"
            val category = mapToCategory(objLabel)

            return SubjectAnalysis(
                category = category,
                bounds = primaryObject.boundingBox,
                normalizedBounds = normalized,
                prominence = area,
                isolationScore = 0.7f,
                mainSubjectName = objLabel
            )
        }

        // 4. Scenery Fallback
        return SubjectAnalysis(
            category = SubjectCategory.SCENERY,
            bounds = null,
            normalizedBounds = null,
            prominence = 0.8f,
            isolationScore = 0.5f,
            mainSubjectName = labels.firstOrNull()?.text ?: "Landscape"
        )
    }

    private fun mapToCategory(label: String): SubjectCategory {
        val petKeywords = setOf("Dog", "Cat", "Pet", "Animal")
        val foodKeywords = setOf("Food", "Dish", "Meal", "Drink")
        val sceneryKeywords = setOf("Nature", "Landscape", "Beach", "Mountain", "Sky")
        
        return when {
            petKeywords.any { label.contains(it, true) } -> SubjectCategory.PETS
            foodKeywords.any { label.contains(it, true) } -> SubjectCategory.FOOD
            sceneryKeywords.any { label.contains(it, true) } -> SubjectCategory.SCENERY
            else -> SubjectCategory.OBJECTS
        }
    }

    private fun buildSemanticDescription(
        subject: SubjectAnalysis,
        labels: Set<String>,
        faces: List<Face>,
        techMetrics: TechnicalMetrics,
        aestheticResult: AestheticScorer.AestheticResult
    ): String {
        val elements = mutableListOf<String>()

        // 1. Identify all semantic elements (Labels)
        val coreLabels = labels.filter { !it.startsWith("COLOR_") }.take(10)
        if (coreLabels.isNotEmpty()) {
            elements.add(coreLabels.joinToString(", "))
        }

        // 2. Identify Colors (Distinguishing factors)
        val colors = labels.filter { it.startsWith("COLOR_") }.map { it.removePrefix("COLOR_") }
        if (colors.isNotEmpty()) {
            elements.add("Colors: ${colors.joinToString(", ")}")
        }

        // 3. Subject State & Expressions
        if (faces.isNotEmpty()) {
            val faceData = mutableListOf<String>()
            faceData.add("${faces.size} Face(s)")
            val smiles = faces.count { (it.smilingProbability ?: 0f) > 0.6f }
            if (smiles > 0) faceData.add("$smiles Smiling")
            val blinking = faces.count { ((it.leftEyeOpenProbability ?: 1f) + (it.rightEyeOpenProbability ?: 1f)) / 2f < 0.45f }
            if (blinking > 0) faceData.add("$blinking Blinking/Eyes-Closed")
            elements.add(faceData.joinToString(" "))
        }

        // 4. What's happening (Actions & Scene Context)
        val actions = mutableListOf<String>()
        if (labels.any { it.contains("Outdoor", true) }) actions.add("Outdoor scene")
        if (labels.any { it.contains("Indoor", true) }) actions.add("Indoor scene")
        if (labels.any { it.contains("Thread") || it.contains("Bracelet") } && labels.contains("Hand")) {
            actions.add("Tying ceremony/Interaction")
        }
        if (labels.any { it.contains("Flower") || it.contains("Tradition") }) actions.add("Festive atmosphere")
        
        if (actions.isNotEmpty()) {
            elements.add("Activity: ${actions.joinToString(", ")}")
        }

        // 5. Composition (Technical State)
        if (aestheticResult.overallScore > 0.7f) elements.add("Balanced framing")
        if (techMetrics.sharpness > 0.6f) elements.add("Sharp focus")

        return elements.joinToString(" | ")
    }

    private fun evaluateQuality(
        faces: List<Face>, 
        techMetrics: TechnicalMetrics,
        aestheticResult: AestheticScorer.AestheticResult,
        subjectAnalysis: SubjectAnalysis,
        width: Float
    ): AnalysisEvaluation {
        
        // technicalScore weighted by exposure health and sharpness
        val technicalScore = (techMetrics.sharpness * 0.6f) + (techMetrics.exposureHealth * 0.4f)

        // Hard Filters
        if (subjectAnalysis.category == SubjectCategory.UTILITY) {
            return AnalysisEvaluation(0.25f, RejectionReason.UTILITY, ScoreBreakdown(technicalScore, 0f, 0f, 0f))
        }
        if (techMetrics.sharpness < 0.18f) {
            return AnalysisEvaluation(0.2f, RejectionReason.BLURRY, ScoreBreakdown(technicalScore, 0f, 0f, 0f))
        }
        if (techMetrics.exposureHealth < 0.35f) {
            return AnalysisEvaluation(0.25f, RejectionReason.POOR_LIGHTING, ScoreBreakdown(technicalScore, 0f, 0f, 0f))
        }

        // Portrait Logic (Requirement 3: Non-smiling faces aren't penalized)
        if (faces.isNotEmpty()) {
            var totalExpr = 0f
            var eyesClosed = 0
            
            for (face in faces) {
                val eyeOpen = ((face.leftEyeOpenProbability ?: 1f) + (face.rightEyeOpenProbability ?: 1f)) / 2f
                val smile = face.smilingProbability ?: 0f
                
                // Baseline 0.75 if eyes are open, smile is a bonus
                val exprScore = if (eyeOpen > 0.55f) {
                    0.75f + (smile * 0.25f) 
                } else {
                    eyeOpen // Penalized for closed eyes
                }
                totalExpr += exprScore
                if (eyeOpen < 0.45f) eyesClosed++
            }
            
            val avgExpr = totalExpr / faces.size
            val collectiveCenter = faces.map { it.boundingBox.centerX() }.average().toFloat()
            val symmetry = 1f - (abs(collectiveCenter - (width / 2f)) / (width / 2f)).coerceIn(0f, 1f)
            
            val rawScore = (avgExpr * 0.45f) + (technicalScore * 0.25f) + (aestheticResult.overallScore * 0.30f)
            val reason = when {
                eyesClosed > 0 && !aestheticResult.isIntentionalClosedEyes -> RejectionReason.EYES_CLOSED
                avgExpr < 0.45f -> RejectionReason.BAD_EXPRESSION
                rawScore < 0.55f -> RejectionReason.LOW_QUALITY
                else -> RejectionReason.NONE
            }
            
            return AnalysisEvaluation(rawScore, reason, ScoreBreakdown(technicalScore, avgExpr, aestheticResult.overallScore, symmetry))
        }

        // Non-Human Logic
        val prominenceBonus = (subjectAnalysis.prominence * 0.3f).coerceIn(0f, 0.3f)
        val finalScore = (technicalScore * 0.4f) + (aestheticResult.overallScore * 0.4f) + prominenceBonus
        
        val reason = when {
            finalScore < 0.5f -> RejectionReason.LOW_QUALITY
            subjectAnalysis.category == SubjectCategory.UNCLEAR && subjectAnalysis.prominence < 0.05f -> RejectionReason.NO_SUBJECT
            else -> RejectionReason.NONE
        }
        
        return AnalysisEvaluation(finalScore, reason, ScoreBreakdown(technicalScore, prominenceBonus, aestheticResult.overallScore, 0.5f))
    }

    data class TechnicalMetrics(
        val sharpness: Float,
        val exposureHealth: Float,
        val highlightClipped: Float,
        val shadowCrushed: Float
    )

    private fun calculateTechnicalMetrics(bitmap: Bitmap, subject: SubjectAnalysis): TechnicalMetrics {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        
        var highlightCount = 0
        var shadowCount = 0
        
        // Requirement 4: Exposure & Dynamic Range Check
        for (p in pixels) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            if (r > 250 || g > 250 || b > 250) highlightCount++
            if (r < 5 && g < 5 && b < 5) shadowCount++
        }
        
        val highRatio = highlightCount.toFloat() / pixels.size
        val lowRatio = shadowCount.toFloat() / pixels.size
        val exposureHealth = (1.0f - (highRatio * 1.5f + lowRatio * 1.2f)).coerceIn(0f, 1f)
        
        // Laplacian Sharpness (Focused on subject if available)
        var lapVar = 0.0
        val bounds = subject.bounds ?: Rect(0, 0, w, h)
        
        var samples = 0
        for (y in bounds.top + 1 until bounds.bottom - 1 step 4) {
            for (x in bounds.left + 1 until bounds.right - 1 step 4) {
                val i = y * w + x
                val lum = getLuminance(pixels[i])
                val lap = getLuminance(pixels[i-1]) + getLuminance(pixels[i+1]) + 
                          getLuminance(pixels[i-w]) + getLuminance(pixels[i+w]) - 4 * lum
                lapVar += lap * lap
                samples++
            }
        }
        
        val sharpness = if (samples > 0) {
            ((lapVar / samples) * 800f).toFloat().coerceIn(0f, 1f)
        } else 0f
        
        return TechnicalMetrics(sharpness, exposureHealth, highRatio, lowRatio)
    }

    private fun normalizeRect(rect: Rect, w: Float, h: Float): RectF {
        return RectF(
            rect.left / w,
            rect.top / h,
            rect.right / w,
            rect.bottom / h
        )
    }

    private fun getLuminance(pixel: Int): Float {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return (0.299f * r + 0.587f * g + 0.114f * b) / 255f
    }

    private fun computeVisualSignature(bitmap: Bitmap): FloatArray {
        val size = 512 // Increased resolution for much better detail (1024x1024)
        val sig = FloatArray(size * size * 3) // RGB (67,500 values)
        
        val scaled = Bitmap.createScaledBitmap(bitmap, size, size, true)
        val pixels = IntArray(size * size)
        scaled.getPixels(pixels, 0, size, 0, 0, size, size)
        
        for (i in pixels.indices) {
            val px = pixels[i]
            val base = i * 3
            sig[base] = ((px shr 16) and 0xFF) / 255f
            sig[base + 1] = ((px shr 8) and 0xFF) / 255f
            sig[base + 2] = (px and 0xFF) / 255f
        }
        
        if (scaled != bitmap) scaled.recycle()
        return sig
    }

    private fun loadOrientedBitmap(context: Context, uri: Uri): Bitmap {
        return try {
            // Fix: Opening separate streams for EXIF and Decoding to avoid FD position issues
            val orientation = context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL

            val options = BitmapFactory.Options().apply { inSampleSize = 4 }
            val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            } ?: throw Exception("Bitmap decoding failed")
                
            val rotation = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            
            if (rotation != 0f) {
                val matrix = Matrix().apply { postRotate(rotation) }
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                bitmap.recycle()
                rotated
            } else bitmap
        } catch (e: Exception) {
            throw Exception("Failed to load oriented bitmap: ${e.message}")
        }
    }

    private fun applyClustering(
        results: List<CuratedResult>, 
        metadataCache: Map<Long, PhotoMetadata>
    ): List<CuratedResult> {
        if (results.isEmpty()) return results
        val sorted = results.sortedBy { it.photo.dateTaken }
        val clustered = mutableListOf<CuratedResult>()
        var currentClusterId: String? = null
        
        for (i in sorted.indices) {
            val current = sorted[i]
            val prev = if (i > 0) sorted[i-1] else null
            val timeDiff = if (prev != null) abs(current.photo.dateTaken - prev.photo.dateTaken) else Long.MAX_VALUE
            val isVisuallySimilar = if (prev != null) checkSemanticSimilarity(current.photo.id, prev.photo.id, metadataCache) else false

            if (prev != null && (timeDiff < 3000 || (timeDiff < 45000 && isVisuallySimilar))) {
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
        
        return clustered.groupBy { it.clusterId }.flatMap { (clusterId, items) ->
            if (clusterId == null) return@flatMap items
            val bestInCluster = items.maxByOrNull { it.score }
            items.map { item ->
                if (item == bestInCluster) {
                    item.copy(isBestTake = item.score >= 0.55f && item.rejectionReason == RejectionReason.NONE)
                } else {
                    val updatedReason = if (item.rejectionReason == RejectionReason.NONE) RejectionReason.DUPLICATE else item.rejectionReason
                    item.copy(isBestTake = false, rejectionReason = updatedReason)
                }
            }
        }
    }

    private fun checkSemanticSimilarity(id1: Long, id2: Long, cache: Map<Long, PhotoMetadata>): Boolean {
        val m1 = cache[id1] ?: return false
        val m2 = cache[id2] ?: return false
        
        if (m1.mainFaceCount != m2.mainFaceCount) return false
        
        var mse = 0f
        val sig1 = m1.visualSignature
        val sig2 = m2.visualSignature
        
        var isPerceptuallySimilar = false
        if (sig1.size == sig2.size && sig1.isNotEmpty()) {
            for (i in sig1.indices) {
                mse += (sig1[i] - sig2[i]).pow(2)
            }
            mse /= sig1.size
            if (mse < 0.006f) isPerceptuallySimilar = true 
        }
        
        val l1 = m1.labels
        val l2 = m2.labels
        
        val labelSimilarity = if (l1.isNotEmpty() && l2.isNotEmpty()) {
            l1.intersect(l2).size.toFloat() / l1.union(l2).size
        } else 0f
        
        // Final similarity requires both visual AND label match
        // Mandate Color check for people to distinguish between similar-looking siblings
        val colorLabels1 = l1.filter { it.startsWith("COLOR_") }
        val colorLabels2 = l2.filter { it.startsWith("COLOR_") }
        
        val hasDifferentColors = colorLabels1.isNotEmpty() && colorLabels2.isNotEmpty() && 
                                 colorLabels1.intersect(colorLabels2.toSet()).isEmpty()

        // If MSE is borderline (0.003 - 0.01) and colors are different, they are different people
        if (mse > 0.003f && hasDifferentColors && m1.mainFaceCount > 0) {
            return false
        }

        val isSemanticallySimilar = labelSimilarity > 0.85f

        return isPerceptuallySimilar || (mse < 0.012f && isSemanticallySimilar)
    }

    /**
     * AI Color Identification: Maps pixel samples to a human-readable color palette.
     * Helps differentiate people based on clothing/environment.
     */
    private fun extractColorLabels(bitmap: Bitmap, bounds: Rect?): List<String> {
        val target = bounds ?: Rect(0, 0, bitmap.width, bitmap.height)
        val samples = 6
        val stepX = (target.width() / samples).coerceAtLeast(1)
        val stepY = (target.height() / samples).coerceAtLeast(1)
        
        val colorCounts = mutableMapOf<String, Int>()
        
        for (iy in 1 until samples) {
            for (ix in 1 until samples) {
                val px = bitmap.getPixel(
                    (target.left + ix * stepX).coerceIn(0, bitmap.width - 1),
                    (target.top + iy * stepY).coerceIn(0, bitmap.height - 1)
                )
                val colorName = identifyColorName(
                    (px shr 16) and 0xFF,
                    (px shr 8) and 0xFF,
                    px and 0xFF
                )
                colorCounts[colorName] = (colorCounts[colorName] ?: 0) + 1
            }
        }
        
        // Return top 2 colors as labels
        return colorCounts.entries
            .sortedByDescending { it.value }
            .take(2)
            .map { "COLOR_${it.key}" }
    }

    private fun identifyColorName(r: Int, g: Int, b: Int): String {
        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV(r, g, b, hsv)
        val h = hsv[0]
        val s = hsv[1]
        val v = hsv[2]

        return when {
            v < 0.15f -> "Black"
            v > 0.85f && s < 0.15f -> "White"
            s < 0.15f -> "Gray"
            h < 15 || h > 345 -> "Red"
            h < 45 -> "Orange"
            h < 75 -> "Yellow"
            h < 160 -> "Green"
            h < 195 -> "Cyan"
            h < 250 -> "Blue"
            h < 290 -> "Purple"
            h < 345 -> "Pink"
            else -> "Unknown"
        }
    }
}
