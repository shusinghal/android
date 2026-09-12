package com.memorycurator.app.core.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.util.Log
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
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock
import kotlin.math.*

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

data class SubjectAnalysis(
    val category: SubjectCategory,
    val bounds: Rect?,
    val normalizedBounds: RectF?,
    val prominence: Float,
    val isolationScore: Float,
    val mainSubjectName: String
)

data class ScoreBreakdown(
    val technicalScore: Float,
    val expressionScore: Float,
    val aestheticScore: Float,
    val symmetryScore: Float
)

data class TechnicalMetrics(
    val sharpness: Float,
    val exposureHealth: Float,
    val highlightClipped: Float,
    val shadowCrushed: Float
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
    private val TAG = "ImageCurator"

    // Thread-safe ML Kit clients initialized lazily
    private val faceDetector by lazy {
        FaceDetection.getClient(FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build())
    }
    private val labeler by lazy { ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS) }
    private val objectDetector by lazy {
        ObjectDetection.getClient(ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build())
    }

    private val aestheticScorer = AestheticScorer()
    private var embeddingEngine: EmbeddingEngine? = null
    private var nimaEngine: NIMAEngine? = null
    private var nanoEngine: GeminiNanoEngine? = null
    private var faceIdentityEngine: FaceIdentityEngine? = null
    private var segmentationEngine: SegmentationEngine? = null

    private val engineInitMutex = Mutex()
    private val aiSemaphore = Semaphore(2) // Concurrency limit to prevent OOM

    /**
     * Compact metadata for memory safety.
     * Replaces massive arrays with robust neural embeddings.
     */
    private data class PhotoMetadata(
        val labels: Set<String>,
        val mainFaceCount: Int,
        val dateTaken: Long,
        val visualEmbedding: FloatArray,
        val faceEmbeddings: List<FloatArray> = emptyList()
    )

    override suspend fun analyzePhotos(
        context: Context,
        photos: List<MediaPhoto>,
        isDeepAnalysis: Boolean,
        onProgress: (Int, Int) -> Unit,
        onResult: (CuratedResult) -> Unit
    ): List<CuratedResult> = withContext(Dispatchers.Default) {

        // Requirement 3: Thread-safe lazy engine initialization
        if (isDeepAnalysis) {
            engineInitMutex.withLock {
                if (embeddingEngine == null) embeddingEngine = EmbeddingEngine(context)
                if (nimaEngine == null) nimaEngine = NIMAEngine(context)
                if (nanoEngine == null) nanoEngine = GeminiNanoEngine(context)
                if (faceIdentityEngine == null) faceIdentityEngine = FaceIdentityEngine(context)
                if (segmentationEngine == null) segmentationEngine = SegmentationEngine()
            }
        }

        val metadataCache = ConcurrentHashMap<Long, PhotoMetadata>()
        val progressCounter = AtomicInteger(0)

        val analysisResults = photos.map { photo ->
            async {
                aiSemaphore.withPermit {
                    var orientedBitmap: Bitmap? = null
                    try {
                        // Requirement 6: Dynamic Sample Size targeting 1080p
                        orientedBitmap = loadOrientedBitmap(context, photo.contentUri, 1920, 1080)
                        val imgW = orientedBitmap.width
                        val imgH = orientedBitmap.height

                        val image = InputImage.fromBitmap(orientedBitmap, 0)
                        val allFaces = faceDetector.process(image).await()
                        val labels = labeler.process(image).await()
                        val detectedObjects = runCatching { objectDetector.process(image).await() }.getOrDefault(emptyList())

                        // Refined Main Subject Selection
                        val mainFaces = allFaces.filter { face ->
                            val normBox = normalizeRect(face.boundingBox, imgW.toFloat(), imgH.toFloat())
                            (normBox.width() * normBox.height()) > 0.015f
                        }.sortedByDescending { it.boundingBox.width() * it.boundingBox.height() }.take(4)

                        val subjectAnalysis = extractSubjectAnalysis(photo, labels, mainFaces, detectedObjects, imgW.toFloat(), imgH.toFloat())
                        val techMetrics = calculateTechnicalMetrics(orientedBitmap, subjectAnalysis)

                        // Requirement 4: Strictly clamp bounds for safe pixel sampling
                        val clampedBounds = subjectAnalysis.bounds?.let {
                            Rect(max(0, it.left), max(0, it.top), min(imgW, it.right), min(imgH, it.bottom))
                        }
                        val colorLabels = extractColorLabels(orientedBitmap, clampedBounds)
                        val allLabels = (labels.map { it.text } + colorLabels).toSet()

                        val aestheticResult = aestheticScorer.calculateAestheticScore(orientedBitmap, mainFaces, labels, clampedBounds, isDeepAnalysis, nimaEngine)
                        val segmentationResult = if (isDeepAnalysis) segmentationEngine?.analyzeSegmentation(orientedBitmap) else null

                        // Requirement 2: Neural visual embedding for perceptual matching
                        val visualEmbedding = if (isDeepAnalysis) {
                            embeddingEngine?.extractEmbedding(orientedBitmap) ?: FloatArray(0)
                        } else {
                            computePerceptualHash(orientedBitmap) // Requirement 2: Compact fallback
                        }

                        val faceEmbeddings = if (isDeepAnalysis && mainFaces.isNotEmpty()) {
                            mainFaces.map { face ->
                                val faceRect = Rect(max(0, face.boundingBox.left), max(0, face.boundingBox.top),
                                                   min(imgW, face.boundingBox.right), min(imgH, face.boundingBox.bottom))
                                faceIdentityEngine?.extractFaceEmbedding(orientedBitmap, faceRect) ?: FloatArray(0)
                            }
                        } else emptyList()

                        metadataCache[photo.id] = PhotoMetadata(allLabels, mainFaces.size, photo.dateTaken, visualEmbedding, faceEmbeddings)

                        val evaluation = evaluateQuality(mainFaces, techMetrics, aestheticResult, subjectAnalysis, segmentationResult, imgW.toFloat(), isDeepAnalysis)

                        val curatedResult = CuratedResult(
                            photo = photo,
                            score = evaluation.score,
                            isBestTake = evaluation.score >= 0.6f && evaluation.reason == RejectionReason.NONE,
                            rejectionReason = if (evaluation.score < 0.6f && evaluation.reason == RejectionReason.NONE) RejectionReason.LOW_QUALITY else evaluation.reason,
                            aiDescription = buildSemanticDescription(subjectAnalysis, allLabels, mainFaces, techMetrics, aestheticResult, isDeepAnalysis, segmentationResult, nanoEngine, orientedBitmap),
                            breakdown = evaluation.breakdown,
                            mainFaceCount = mainFaces.size
                        )

                        onResult(curatedResult)
                        curatedResult
                    } catch (e: Exception) {
                        Log.e(TAG, "Analysis failed for photo ${photo.id}", e)
                        CuratedResult(photo, 0f, false, RejectionReason.LOW_QUALITY, "Processing failed")
                    } finally {
                        orientedBitmap?.recycle()
                        onProgress(progressCounter.incrementAndGet(), photos.size)
                    }
                }
            }
        }.awaitAll()

        // Requirement 1: Final DSU clustering pass for deduplication
        applyClustering(analysisResults, metadataCache, isDeepAnalysis)
    }

    override fun close() {
        runCatching {
            faceDetector.close()
            labeler.close()
            objectDetector.close()
            embeddingEngine?.close()
            nimaEngine?.close()
            faceIdentityEngine?.close()
        }
        embeddingEngine = null
        nimaEngine = null
        faceIdentityEngine = null
        segmentationEngine = null
    }

    /**
     * Requirement 1: Union-Find (DSU) implementation for robust cluster grouping.
     */
    private fun applyClustering(results: List<CuratedResult>, cache: Map<Long, PhotoMetadata>, isDeep: Boolean): List<CuratedResult> {
        if (results.isEmpty()) return results
        val sorted = results.sortedBy { it.photo.dateTaken }
        val dsu = IntArray(sorted.size) { it }

        fun find(i: Int): Int {
            if (dsu[i] == i) return i
            dsu[i] = find(dsu[i])
            return dsu[i]
        }

        fun union(i: Int, j: Int) {
            val rootI = find(i)
            val rootJ = find(j)
            if (rootI != rootJ) dsu[rootI] = rootJ
        }

        // Temporal search window (burst detection)
        val window = if (isDeep) 15 else 5
        for (i in sorted.indices) {
            for (j in i + 1 until min(i + window, sorted.size)) {
                if (checkSemanticSimilarity(sorted[i].photo.id, sorted[j].photo.id, cache, isDeep)) {
                    union(i, j)
                }
            }
        }

        val clusters = mutableMapOf<Int, MutableList<CuratedResult>>()
        for (i in sorted.indices) {
            clusters.getOrPut(find(i)) { mutableListOf() }.add(sorted[i])
        }

        return clusters.values.flatMap { items ->
            if (items.size == 1) {
                listOf(items[0].copy(clusterId = null))
            } else {
                val best = items.maxByOrNull { it.score }
                val cid = "cluster_${items.first().photo.id}"
                items.map { item ->
                    val isBest = (item == best && item.score >= 0.58f)
                    item.copy(
                        clusterId = cid,
                        isBestTake = isBest,
                        rejectionReason = if (!isBest && item.rejectionReason == RejectionReason.NONE) RejectionReason.DUPLICATE else item.rejectionReason
                    )
                }
            }
        }
    }

    /**
     * Requirement 2: Sophisticated similarity engine using neural embeddings and face identity booster.
     */
    private fun checkSemanticSimilarity(id1: Long, id2: Long, cache: Map<Long, PhotoMetadata>, isDeep: Boolean): Boolean {
        val m1 = cache[id1] ?: return false
        val m2 = cache[id2] ?: return false

        // 1. Hard Temporal Filter (Max 5 mins for deep, 1 min for standard)
        val timeDiff = abs(m1.dateTaken - m2.dateTaken)
        if (timeDiff > (if (isDeep) 300000 else 60000)) return false

        // 2. Face Identity Booster (High Confidence Match)
        if (isDeep && m1.faceEmbeddings.isNotEmpty() && m2.faceEmbeddings.isNotEmpty()) {
            for (f1 in m1.faceEmbeddings) {
                for (f2 in m2.faceEmbeddings) {
                    if (calculateCosineSimilarity(f1, f2) > 0.82f) return true
                }
            }
        }

        // 3. Primary Visual Signal: 1280-dim embedding or perceptual hash
        if (m1.visualEmbedding.isNotEmpty() && m2.visualEmbedding.isNotEmpty()) {
            val sim = calculateCosineSimilarity(m1.visualEmbedding, m2.visualEmbedding)
            val threshold = if (isDeep) 0.95f else 0.85f
            if (sim > threshold) return true
        }

        // 4. Label Overlap (IoU)
        val common = m1.labels.intersect(m2.labels).size.toFloat()
        val total = m1.labels.union(m2.labels).size.toFloat()
        return (common / total) > 0.92f
    }

    /**
     * Requirement 7: Fix exposure health logic and provide better technical metrics.
     */
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
            // Requirement 7: Saturated colors aren't clipped. White/Overexposed area is.
            if (r > 250 && g > 250 && b > 250) highlightCount++
            if (r < 6 && g < 6 && b < 6) shadowCount++
        }

        val highRatio = highlightCount.toFloat() / pixels.size
        val lowRatio = shadowCount.toFloat() / pixels.size
        // Penalty for clipping
        val exposureHealth = (1.0f - (highRatio * 1.8f + lowRatio * 1.3f)).coerceIn(0f, 1f)

        // Localized Sharpness (Laplacian Variance)
        val b = subject.bounds ?: Rect(0, 0, w, h)
        // Clamp scanning window
        val s = Rect(max(1, b.left), max(1, b.top), min(w - 2, b.right), min(h - 2, b.bottom))

        var lapVar = 0.0
        var samples = 0
        for (y in s.top until s.bottom step 4) {
            for (x in s.left until s.right step 4) {
                val i = y * w + x
                val lum = getLuminance(pixels[i])
                val lap = getLuminance(pixels[i-1]) + getLuminance(pixels[i+1]) +
                          getLuminance(pixels[i-w]) + getLuminance(pixels[i+w]) - 4 * lum
                lapVar += lap * lap
                samples++
            }
        }

        val sharpness = if (samples > 0) ((lapVar / samples) * 1000f).toFloat().coerceIn(0f, 1f) else 0.5f
        return TechnicalMetrics(sharpness, exposureHealth, highRatio, lowRatio)
    }

    /**
     * Requirement 6: Memory-efficient image loader with dynamic sample size.
     */
    private fun loadOrientedBitmap(context: Context, uri: Uri, maxW: Int, maxH: Int): Bitmap {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }

        var sampleSize = 1
        while ((options.outWidth / sampleSize) > maxW || (options.outHeight / sampleSize) > maxH) {
            sampleSize *= 2
        }

        options.inJustDecodeBounds = false
        options.inSampleSize = sampleSize

        val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                     ?: throw Exception("Decryption or decoding failed")

        val orientation = context.contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL

        val rotation = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }

        return if (rotation != 0f) {
            val matrix = Matrix().apply { postRotate(rotation) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
                if (it != bitmap) bitmap.recycle()
            }
        } else bitmap
    }

    private fun extractSubjectAnalysis(photo: MediaPhoto, labels: List<ImageLabel>, faces: List<Face>, objects: List<DetectedObject>, imgW: Float, imgH: Float): SubjectAnalysis {
        // Utility check
        val util = setOf("Text", "Screenshot", "Document", "Label")
        if (labels.count { l -> util.any { it.contains(l.text, true) } } >= 2) {
            return SubjectAnalysis(SubjectCategory.UTILITY, null, null, 0f, 0f, "Utility")
        }

        if (faces.isNotEmpty()) {
            val bounds = Rect(faces.minOf { it.boundingBox.left }, faces.minOf { it.boundingBox.top }, faces.maxOf { it.boundingBox.right }, faces.maxOf { it.boundingBox.bottom })
            val norm = normalizeRect(bounds, imgW, imgH)
            return SubjectAnalysis(SubjectCategory.PEOPLE, bounds, norm, norm.width() * norm.height(), 0.9f, "People")
        }

        val obj = objects.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
        if (obj != null) {
            val norm = normalizeRect(obj.boundingBox, imgW, imgH)
            return SubjectAnalysis(SubjectCategory.OBJECTS, obj.boundingBox, norm, norm.width() * norm.height(), 0.7f, obj.labels.firstOrNull()?.text ?: "Object")
        }

        return SubjectAnalysis(SubjectCategory.SCENERY, null, null, 1f, 0.5f, labels.firstOrNull()?.text ?: "Scene")
    }

    private fun evaluateQuality(faces: List<Face>, tech: TechnicalMetrics, aesthetic: AestheticScorer.AestheticResult, subject: SubjectAnalysis, seg: SegmentationEngine.SegmentationResult?, width: Float, isDeep: Boolean): AnalysisEvaluation {
        val technicalScore = (tech.sharpness * 0.65f) + (tech.exposureHealth * 0.35f)

        if (subject.category == SubjectCategory.UTILITY) return AnalysisEvaluation(0.2f, RejectionReason.UTILITY, ScoreBreakdown(technicalScore, 0f, 0f, 0f))
        if (tech.sharpness < 0.12f) return AnalysisEvaluation(0.15f, RejectionReason.BLURRY, ScoreBreakdown(technicalScore, 0f, 0f, 0f))

        var exprScore = 0.8f
        var eyesClosed = 0
        if (faces.isNotEmpty()) {
            var total = 0f
            for (f in faces) {
                val eyes = ((f.leftEyeOpenProbability ?: 1f) + (f.rightEyeOpenProbability ?: 1f)) / 2f
                val smile = f.smilingProbability ?: 0f
                if (eyes < 0.4f) eyesClosed++
                total += if (eyes > 0.5f) 0.7f + (smile * 0.3f) else eyes
            }
            exprScore = total / faces.size
        }

        val base = (exprScore * 0.4f) + (technicalScore * 0.2f) + (aesthetic.overallScore * 0.4f)
        val score = (base + (seg?.subjectProminence ?: 0f) * 0.1f).coerceIn(0f, 1f)

        val reason = when {
            eyesClosed > 0 && !aesthetic.isIntentionalClosedEyes -> RejectionReason.EYES_CLOSED
            exprScore < 0.4f -> RejectionReason.BAD_EXPRESSION
            tech.exposureHealth < 0.3f -> RejectionReason.POOR_LIGHTING
            score < 0.55f -> RejectionReason.LOW_QUALITY
            else -> RejectionReason.NONE
        }

        return AnalysisEvaluation(score, reason, ScoreBreakdown(technicalScore, exprScore, aesthetic.overallScore, 0.5f))
    }

    private fun extractColorLabels(bitmap: Bitmap, bounds: Rect?): List<String> {
        val target = bounds ?: Rect(0, 0, bitmap.width, bitmap.height)
        val samples = 5
        val stepX = (target.width() / samples).coerceAtLeast(1)
        val stepY = (target.height() / samples).coerceAtLeast(1)
        val colors = mutableMapOf<String, Int>()
        for (iy in 0 until samples) {
            for (ix in 0 until samples) {
                val px = bitmap.getPixel((target.left + ix * stepX).coerceIn(0, bitmap.width - 1), (target.top + iy * stepY).coerceIn(0, bitmap.height - 1))
                val name = identifyColor(px)
                colors[name] = (colors[name] ?: 0) + 1
            }
        }
        return colors.entries.sortedByDescending { it.value }.take(2).map { "COLOR_${it.key}" }
    }

    private suspend fun buildSemanticDescription(subject: SubjectAnalysis, labels: Set<String>, faces: List<Face>, tech: TechnicalMetrics, aesthetic: AestheticScorer.AestheticResult, isDeep: Boolean, seg: SegmentationEngine.SegmentationResult?, nano: GeminiNanoEngine?, bitmap: Bitmap): String {
        val res = mutableListOf<String>()
        if (isDeep) {
            val deep = nano?.generateDeepDescription(bitmap)
            if (deep != null) return deep
            res.add("AI Analysis Active")
        }
        res.add(subject.mainSubjectName)
        res.add(labels.filter { !it.startsWith("COLOR_") }.take(10).joinToString(", "))
        if (tech.sharpness > 0.7f) res.add("Sharp")
        return res.joinToString(" | ")
    }

    private fun calculateCosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
        if (v1.isEmpty() || v1.size != v2.size) return 0f
        var dot = 0f; var n1 = 0f; var n2 = 0f
        for (i in v1.indices) {
            dot += v1[i] * v2[i]
            n1 += v1[i] * v1[i]
            n2 += v2[i] * v2[i]
        }
        val den = sqrt(n1) * sqrt(n2)
        return if (den > 0) dot / den else 0f
    }

    private fun computePerceptualHash(bitmap: Bitmap): FloatArray {
        val scaled = Bitmap.createScaledBitmap(bitmap, 32, 32, true)
        val hash = FloatArray(1024)
        for (y in 0 until 32) {
            for (x in 0 until 32) {
                hash[y * 32 + x] = getLuminance(scaled.getPixel(x, y))
            }
        }
        scaled.recycle()
        return hash
    }

    private fun getLuminance(p: Int) = (0.299f * ((p shr 16) and 0xFF) + 0.587f * ((p shr 8) and 0xFF) + 0.114f * (p and 0xFF)) / 255f
    private fun normalizeRect(r: Rect, w: Float, h: Float) = RectF(r.left / w, r.top / h, r.right / w, r.bottom / h)
    private fun identifyColor(p: Int): String {
        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF, hsv)
        return when {
            hsv[2] < 0.15f -> "Black"
            hsv[1] < 0.15f -> "Gray"
            hsv[0] < 20 || hsv[0] > 340 -> "Red"
            hsv[0] < 50 -> "Orange"
            hsv[0] < 70 -> "Yellow"
            hsv[0] < 160 -> "Green"
            hsv[0] < 260 -> "Blue"
            else -> "Purple"
        }
    }

    private data class AnalysisEvaluation(val score: Float, val reason: RejectionReason, val breakdown: ScoreBreakdown)
}
