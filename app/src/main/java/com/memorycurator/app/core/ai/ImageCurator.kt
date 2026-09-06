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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
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
        isDeepAnalysis: Boolean = false,
        onProgress: (Int, Int) -> Unit,
        onResult: (CuratedResult) -> Unit = {}
    ): List<CuratedResult>

    fun close()
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
    private var embeddingEngine: EmbeddingEngine? = null
    private var nimaEngine: NIMAEngine? = null
    private var nanoEngine: GeminiNanoEngine? = null
    private var faceIdentityEngine: FaceIdentityEngine? = null
    private var segmentationEngine: SegmentationEngine? = null
    
    private val aiSemaphore = Semaphore(3) // Limit concurrent AI tasks to prevent OOM

    data class PhotoMetadata(
        val labels: Set<String>, // Fix: Store string set including Color AI results
        val mainFaceCount: Int,
        val dateTaken: Long,
        val visualSignature: FloatArray,
        val faceEmbeddings: List<FloatArray> = emptyList()
    )

    override suspend fun analyzePhotos(
        context: Context, 
        photos: List<MediaPhoto>,
        isDeepAnalysis: Boolean,
        onProgress: (Int, Int) -> Unit,
        onResult: (CuratedResult) -> Unit
    ): List<CuratedResult> = withContext(Dispatchers.Default) {
        if (isDeepAnalysis) {
            if (embeddingEngine == null) embeddingEngine = EmbeddingEngine(context)
            if (nimaEngine == null) nimaEngine = NIMAEngine(context)
            if (nanoEngine == null) nanoEngine = GeminiNanoEngine(context)
            if (faceIdentityEngine == null) faceIdentityEngine = FaceIdentityEngine(context)
            if (segmentationEngine == null) segmentationEngine = SegmentationEngine()
        }

        val photoMetadataCache = ConcurrentHashMap<Long, PhotoMetadata>()
        val progressCounter = AtomicInteger(0)

        val rawResults = coroutineScope {
            photos.map { photo ->
                async {
                    aiSemaphore.withPermit {
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
                                subjectBounds = subjectAnalysis.bounds,
                                isDeep = isDeepAnalysis,
                                nimaEngine = nimaEngine
                            )

                            val segmentationResult = if (isDeepAnalysis) {
                                segmentationEngine?.analyzeSegmentation(orientedBitmap)
                            } else null

                            val semanticContent = if (isDeepAnalysis) {
                                val nanoDescription = nanoEngine?.generateDeepDescription(orientedBitmap)
                                val segInfo = segmentationResult?.let { 
                                    " | Subject: ${(it.subjectProminence * 100).toInt()}% of frame, Bg Clutter: ${(it.backgroundClutter * 100).toInt()}%"
                                } ?: ""
                                (nanoDescription ?: buildSemanticDescription(
                                    subject = subjectAnalysis,
                                    labels = allLabels,
                                    faces = mainFaces,
                                    techMetrics = techMetrics,
                                    aestheticResult = aestheticResult,
                                    isDeep = isDeepAnalysis
                                )) + segInfo
                            } else {
                                buildSemanticDescription(
                                    subject = subjectAnalysis,
                                    labels = allLabels,
                                    faces = mainFaces,
                                    techMetrics = techMetrics,
                                    aestheticResult = aestheticResult,
                                    isDeep = isDeepAnalysis
                                )
                            }
                            val visualSig = if (isDeepAnalysis) {
                                embeddingEngine?.extractEmbedding(orientedBitmap) ?: computeVisualSignature(orientedBitmap, false)
                            } else {
                                computeVisualSignature(orientedBitmap, false)
                            }

                            val faceEmbeddings = if (isDeepAnalysis && mainFaces.isNotEmpty()) {
                                mainFaces.map { face -> 
                                    faceIdentityEngine?.extractFaceEmbedding(orientedBitmap, face.boundingBox) ?: FloatArray(0)
                                }
                            } else emptyList()

                            photoMetadataCache[photo.id] = PhotoMetadata(
                                labels = allLabels, 
                                mainFaceCount = mainFaces.size, 
                                dateTaken = photo.dateTaken, 
                                visualSignature = visualSig,
                                faceEmbeddings = faceEmbeddings
                            )

                            val evaluation = evaluateQuality(
                                faces = mainFaces, 
                                techMetrics = techMetrics,
                                aestheticResult = aestheticResult,
                                subjectAnalysis = subjectAnalysis,
                                segmentationResult = segmentationResult,
                                width = imgW,
                                isDeep = isDeepAnalysis
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
                }
            }.awaitAll()
        }

        val clusteredResults = applyClustering(rawResults, photoMetadataCache, isDeepAnalysis)
        
        clusteredResults.forEach { result ->
            onResult(result)
        }

        return@withContext clusteredResults
    }

    override fun close() {
        embeddingEngine?.close()
        nimaEngine?.close()
        faceIdentityEngine?.close()
        embeddingEngine = null
        nimaEngine = null
        faceIdentityEngine = null
        segmentationEngine = null
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

        // 3. Object Detection
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
        aestheticResult: AestheticScorer.AestheticResult,
        isDeep: Boolean
    ): String {
        val elements = mutableListOf<String>()

        if (isDeep) {
            elements.add("AI Deep Analysis Active")
            
            val mood = when {
                aestheticResult.overallScore > 0.8f -> "Professional masterpiece"
                aestheticResult.overallScore > 0.6f -> "Artistically framed"
                else -> "Standard capture"
            }
            elements.add("Mood: $mood")
            
            if (faces.isNotEmpty()) {
                val faceDetails = faces.joinToString(", ") { face ->
                    val smile = (face.smilingProbability ?: 0f) * 100
                    val eyes = (((face.leftEyeOpenProbability ?: 1f) + (face.rightEyeOpenProbability ?: 1f)) / 2f) * 100
                    "Person(Smile:${smile.toInt()}%, Eyes:${eyes.toInt()}%)"
                }
                elements.add("Faces: $faceDetails")
            }
        }

        val coreLabels = labels.filter { !it.startsWith("COLOR_") }.take(if (isDeep) 20 else 10)
        if (coreLabels.isNotEmpty()) {
            elements.add(coreLabels.joinToString(", "))
        }

        val colors = labels.filter { it.startsWith("COLOR_") }.map { it.removePrefix("COLOR_") }
        if (colors.isNotEmpty()) {
            elements.add("Colors: ${colors.joinToString(", ")}")
        }

        if (faces.isNotEmpty()) {
            val faceData = mutableListOf<String>()
            faceData.add("${faces.size} Face(s)")
            val smiles = faces.count { (it.smilingProbability ?: 0f) > 0.6f }
            if (smiles > 0) faceData.add("$smiles Smiling")
            val blinking = faces.count { ((it.leftEyeOpenProbability ?: 1f) + (it.rightEyeOpenProbability ?: 1f)) / 2f < 0.45f }
            if (blinking > 0) faceData.add("$blinking Blinking/Eyes-Closed")
            elements.add(faceData.joinToString(" "))
        }

        val actions = mutableListOf<String>()
        if (labels.any { it.contains("Outdoor", true) }) actions.add("Outdoor scene")
        if (labels.any { it.contains("Indoor", true) }) actions.add("Indoor scene")
        
        if (actions.isNotEmpty()) {
            elements.add("Activity: ${actions.joinToString(", ")}")
        }

        if (aestheticResult.overallScore > 0.7f) elements.add("Balanced framing")
        if (techMetrics.sharpness > 0.6f) elements.add("Sharp focus")

        return elements.joinToString(" | ")
    }

    private fun evaluateQuality(
        faces: List<Face>, 
        techMetrics: TechnicalMetrics,
        aestheticResult: AestheticScorer.AestheticResult,
        subjectAnalysis: SubjectAnalysis,
        segmentationResult: SegmentationEngine.SegmentationResult?,
        width: Float,
        isDeep: Boolean
    ): AnalysisEvaluation {
        
        val technicalScore = (techMetrics.sharpness * 0.6f) + (techMetrics.exposureHealth * 0.4f)

        if (subjectAnalysis.category == SubjectCategory.UTILITY) {
            return AnalysisEvaluation(0.25f, RejectionReason.UTILITY, ScoreBreakdown(technicalScore, 0f, 0f, 0f))
        }
        if (techMetrics.sharpness < 0.15f) {
            return AnalysisEvaluation(0.2f, RejectionReason.BLURRY, ScoreBreakdown(technicalScore, 0f, 0f, 0f))
        }

        var expressionScore = 0.8f 
        var eyesClosed = 0
        
        if (faces.isNotEmpty()) {
            var totalExpr = 0f
            for (face in faces) {
                val eyeOpen = ((face.leftEyeOpenProbability ?: 1f) + (face.rightEyeOpenProbability ?: 1f)) / 2f
                val smile = face.smilingProbability ?: 0f
                
                val exprScore = if (eyeOpen > 0.5f) 0.75f + (smile * 0.25f) else eyeOpen
                totalExpr += exprScore
                if (eyeOpen < 0.4f) eyesClosed++
            }
            expressionScore = totalExpr / faces.size
        } else {
            expressionScore = (subjectAnalysis.prominence * 0.5f) + 0.5f
        }

        val finalAesthetic = aestheticResult.overallScore
        val prominenceBonus = (segmentationResult?.subjectProminence ?: 0f) * 0.15f
        val clutterPenalty = (segmentationResult?.backgroundClutter ?: 0f) * 0.1f
        
        val baseScore = (expressionScore * 0.35f) + (technicalScore * 0.25f) + (finalAesthetic * 0.40f)
        val curatedScore = (baseScore + prominenceBonus - clutterPenalty).coerceIn(0f, 1f)

        val reason = when {
            eyesClosed > 0 && !aestheticResult.isIntentionalClosedEyes -> RejectionReason.EYES_CLOSED
            expressionScore < 0.4f -> RejectionReason.BAD_EXPRESSION
            techMetrics.exposureHealth < 0.3f -> RejectionReason.POOR_LIGHTING
            finalAesthetic < 0.4f && isDeep -> RejectionReason.LOW_QUALITY
            curatedScore < 0.5f -> RejectionReason.LOW_QUALITY
            else -> RejectionReason.NONE
        }
        
        val collectiveCenter = if (faces.isNotEmpty()) faces.map { it.boundingBox.centerX() }.average().toFloat() else width/2
        val symmetry = 1f - (abs(collectiveCenter - (width / 2f)) / (width / 2f)).coerceIn(0f, 1f)
        
        return AnalysisEvaluation(curatedScore, reason, ScoreBreakdown(technicalScore, expressionScore, finalAesthetic, symmetry))
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

    private fun computeVisualSignature(bitmap: Bitmap, isDeep: Boolean = false): FloatArray {
        val size = if (isDeep) 1024 else 512 
        val sig = FloatArray(size * size * 3) 
        
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
        metadataCache: Map<Long, PhotoMetadata>,
        isDeep: Boolean = false
    ): List<CuratedResult> {
        if (results.isEmpty()) return results
        val sorted = results.sortedBy { it.photo.dateTaken }
        val clustered = mutableListOf<CuratedResult>()
        
        val searchWindow = if (isDeep) 15 else 3 
        
        for (i in sorted.indices) {
            val current = sorted[i]
            var foundClusterId: String? = null
            
            for (j in (i - 1) downTo max(0, i - searchWindow)) {
                val prev = sorted[j]
                val timeDiff = abs(current.photo.dateTaken - prev.photo.dateTaken)
                val maxTimeDiff = if (isDeep) 300000L else 45000L 
                
                val isVisuallySimilar = checkSemanticSimilarity(current.photo.id, prev.photo.id, metadataCache, isDeep)

                if (timeDiff < 3000 || (timeDiff < maxTimeDiff && isVisuallySimilar)) {
                    foundClusterId = clustered.find { it.photo.id == prev.photo.id }?.clusterId ?: "cluster_${prev.photo.id}"
                    break
                }
            }
            
            clustered.add(current.copy(clusterId = foundClusterId))
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

    private fun checkSemanticSimilarity(id1: Long, id2: Long, cache: Map<Long, PhotoMetadata>, isDeep: Boolean = false): Boolean {
        val m1 = cache[id1] ?: return false
        val m2 = cache[id2] ?: return false
        
        if (m1.mainFaceCount != m2.mainFaceCount) return false
        
        val sig1 = m1.visualSignature
        val sig2 = m2.visualSignature
        
        if (sig1.isEmpty() || sig2.isEmpty() || sig1.size != sig2.size) return false

        var isPerceptuallySimilar = false

        if (sig1.size > 1000) {
            var dotProduct = 0f
            var normA = 0f
            var normB = 0f
            for (i in sig1.indices) {
                dotProduct += sig1[i] * sig2[i]
                normA += sig1[i] * sig1[i]
                normB += sig2[i] * sig2[i]
            }
            val similarity = dotProduct / (kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB))
            if (similarity > 0.90f) isPerceptuallySimilar = true
        } else {
            var mse = 0f
            for (i in sig1.indices) {
                mse += (sig1[i] - sig2[i]).pow(2)
            }
            mse /= sig1.size
            val targetMse = if (isDeep) 0.004f else 0.006f
            if (mse < targetMse) isPerceptuallySimilar = true 
        }
        
        val l1 = m1.labels
        val l2 = m2.labels
        
        val labelSimilarity = if (l1.isNotEmpty() && l2.isNotEmpty()) {
            l1.intersect(l2).size.toFloat() / l1.union(l2).size
        } else 0f
        
        val colorLabels1 = l1.filter { it.startsWith("COLOR_") }
        val colorLabels2 = l2.filter { it.startsWith("COLOR_") }
        val hasDifferentColors = colorLabels1.isNotEmpty() && colorLabels2.isNotEmpty() && 
                                 colorLabels1.intersect(colorLabels2.toSet()).isEmpty()

        if (hasDifferentColors && m1.mainFaceCount > 0) return false

        val targetLabelSim = if (isDeep) 0.92f else 0.85f
        val isSemanticallySimilar = labelSimilarity > targetLabelSim

        return isPerceptuallySimilar || (isSemanticallySimilar && labelSimilarity > 0.95f)
    }

    private fun calculateCosineSimilarity(vec1: FloatArray, vec2: FloatArray): Float {
        if (vec1.isEmpty() || vec2.isEmpty() || vec1.size != vec2.size) return 0f
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f
        for (i in vec1.indices) {
            dotProduct += vec1[i] * vec2[i]
            normA += vec1[i] * vec1[i]
            normB += vec2[i] * vec2[i]
        }
        return dotProduct / (kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB))
    }

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
