package com.memorycurator.app.core.ai

import android.content.Context
import android.graphics.*
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

/**
 * Why it's not "just simple compare":
 * 1. Transitive Drift: If A looks like B, and B looks like C, but A doesn't look like C. 
 *    Simple comparison might group A and C erroneously. We need "Moment/Cluster" boundaries.
 * 2. Non-Linear Captures: Users often take Photo A, move slightly (Photo B), then go back 
 *    to the original spot (Photo C). Simple linear drop would keep B and lose A/C connection.
 * 3. Best-in-Set: We want the best pose from a "Pose Set", not just the better of two adjacent frames.
 */

enum class RejectionReason {
    NONE, BLURRY, EYES_CLOSED, BAD_EXPRESSION, POOR_LIGHTING, POOR_COMPOSITION, DUPLICATE, LOW_QUALITY, MANUAL, UTILITY, NO_SUBJECT;
    val label: String get() = name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
    val icon: ImageVector get() = when(this) {
        NONE -> Icons.Default.CheckCircle
        BLURRY -> Icons.Default.BlurOn
        EYES_CLOSED -> Icons.Default.VisibilityOff
        DUPLICATE -> Icons.Default.CopyAll
        else -> Icons.Default.ThumbDown
    }
}

enum class SubjectCategory { PEOPLE, PETS, FOOD, SCENERY, OBJECTS, UTILITY, UNCLEAR }

data class SubjectAnalysis(
    val category: SubjectCategory,
    val bounds: Rect?,
    val normalizedBounds: RectF?,
    val prominence: Float,
    val isolationScore: Float,
    val mainSubjectName: String
)

data class ScoreBreakdown(val technicalScore: Float, val expressionScore: Float, val aestheticScore: Float, val symmetryScore: Float)

data class TechnicalMetrics(val sharpness: Float, val exposureHealth: Float, val highlightClipped: Float, val shadowCrushed: Float)

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
    suspend fun analyzePhotos(context: Context, photos: List<MediaPhoto>, isDeepAnalysis: Boolean = false, onProgress: (Int, Int) -> Unit, onResult: (CuratedResult) -> Unit = {}): List<CuratedResult>
    fun close()
}

class ImageCuratorImpl : ImageCurator {
    private val TAG = "ImageCurator"

    private val engineInitMutex = Mutex()
    private val aiSemaphore = Semaphore(2)

    private val faceDetector by lazy { FaceDetection.getClient(FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL).build()) }
    private val labeler by lazy { ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS) }
    private val objectDetector by lazy { ObjectDetection.getClient(ObjectDetectorOptions.Builder()
        .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE).enableMultipleObjects().enableClassification().build()) }

    private val aestheticScorer = AestheticScorer()
    private var embeddingEngine: EmbeddingEngine? = null
    private var nimaEngine: NIMAEngine? = null
    private var nanoEngine: GeminiNanoEngine? = null
    private var faceIdentityEngine: FaceIdentityEngine? = null
    private var segmentationEngine: SegmentationEngine? = null

    // Requirement 2: Anchor Subject Tracking (Database-like logic)
    private class AnchorSubject(var embedding: FloatArray, var appearanceCount: Int = 1)
    private val anchorGallery = mutableListOf<AnchorSubject>()
    private val galleryMutex = Mutex()

    private data class PhotoMetadata(
        val labels: Set<String>,
        val dateTaken: Long,
        val visualEmbedding: FloatArray,
        val faceEmbeddings: List<FloatArray>,
        val backgroundSig: FloatArray, // Disentangled Scene Signature
        val isPortrait: Boolean
    )

    override suspend fun analyzePhotos(
        context: Context,
        photos: List<MediaPhoto>,
        isDeepAnalysis: Boolean,
        onProgress: (Int, Int) -> Unit,
        onResult: (CuratedResult) -> Unit
    ): List<CuratedResult> = withContext(Dispatchers.Default) {

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

        // Pass 1: Analysis and Embedding Extraction
        val analyzedResults = photos.map { photo ->
            async {
                aiSemaphore.withPermit {
                    var orientedBitmap: Bitmap? = null
                    try {
                        orientedBitmap = loadOrientedBitmap(context, photo.contentUri, 1920, 1080)
                        val imgW = orientedBitmap.width; val imgH = orientedBitmap.height
                        val image = InputImage.fromBitmap(orientedBitmap, 0)

                        val allFaces = faceDetector.process(image).await()
                        val labels = labeler.process(image).await()
                        val detectedObjects = runCatching { objectDetector.process(image).await() }.getOrDefault(emptyList())

                        val mainFaces = allFaces.filter { 
                            val area = (it.boundingBox.width().toFloat() / imgW) * (it.boundingBox.height().toFloat() / imgH)
                            area > 0.015f 
                        }.sortedByDescending { it.boundingBox.width() }.take(4)

                        val subject = extractSubjectAnalysis(photo, labels, mainFaces, detectedObjects, imgW.toFloat(), imgH.toFloat())
                        val tech = calculateTechnicalMetrics(orientedBitmap, subject)
                        
                        val clampedBounds = subject.bounds?.let { 
                            Rect(max(0, it.left), max(0, it.top), min(imgW, it.right), min(imgH, it.bottom)) 
                        }
                        
                        val colorLabels = extractColorLabels(orientedBitmap, clampedBounds)
                        val allLabels = (labels.map { it.text } + colorLabels).toSet()

                        // Feature Extraction
                        val backgroundSig = computeBackgroundSignature(orientedBitmap, clampedBounds)
                        val visualEmb = if (isDeepAnalysis) embeddingEngine?.extractEmbedding(orientedBitmap) ?: FloatArray(0) else FloatArray(0)
                        val faceEmbs = if (isDeepAnalysis) mainFaces.map { 
                            val r = it.boundingBox
                            val c = Rect(max(0, r.left), max(0, r.top), min(imgW, r.right), min(imgH, r.bottom))
                            faceIdentityEngine?.extractFaceEmbedding(orientedBitmap, c) ?: FloatArray(0) 
                        } else emptyList()

                        // Enroll subjects in silent database
                        if (isDeepAnalysis && tech.sharpness > 0.4f && faceEmbs.size in 1..2) {
                            galleryMutex.withLock { faceEmbs.forEach { enrollSubject(it) } }
                        }

                        metadataCache[photo.id] = PhotoMetadata(allLabels, photo.dateTaken, visualEmb, faceEmbs, backgroundSig, mainFaces.isNotEmpty())

                        val aestheticResult = aestheticScorer.calculateAestheticScore(orientedBitmap, mainFaces, labels, clampedBounds, isDeepAnalysis, nimaEngine)
                        val eval = evaluateSubjectAwareQuality(mainFaces, tech, aestheticResult.overallScore)

                        val result = CuratedResult(
                            photo = photo, score = eval.score,
                            isBestTake = eval.score >= 0.6f && eval.reason == RejectionReason.NONE,
                            rejectionReason = if (eval.score < 0.6f && eval.reason == RejectionReason.NONE) RejectionReason.LOW_QUALITY else eval.reason,
                            aiDescription = buildDescription(subject, allLabels, isDeepAnalysis, nanoEngine, orientedBitmap),
                            breakdown = eval.breakdown, mainFaceCount = mainFaces.size
                        )
                        onResult(result); result
                    } catch (e: Exception) {
                        Log.e(TAG, "Fail ${photo.id}", e)
                        CuratedResult(photo, 0f, false, RejectionReason.LOW_QUALITY, "Failed")
                    } finally {
                        orientedBitmap?.recycle()
                        onProgress(progressCounter.incrementAndGet(), photos.size)
                    }
                }
            }
        }.awaitAll()

        // Pass 2: DSU Clustering with Disentangled Background Logic
        applyDisentangledClustering(analyzedResults, metadataCache, isDeepAnalysis)
    }

    private fun enrollSubject(emb: FloatArray) {
        val existing = anchorGallery.find { calculateCosineSimilarity(it.embedding, emb) > 0.80f }
        if (existing != null) {
            existing.appearanceCount++
            // Refine embedding centroid
            for (i in emb.indices) existing.embedding[i] = (existing.embedding[i] * 0.95f) + (emb[i] * 0.05f)
        } else {
            anchorGallery.add(AnchorSubject(emb.copyOf()))
        }
    }

    private fun applyDisentangledClustering(results: List<CuratedResult>, cache: Map<Long, PhotoMetadata>, isDeep: Boolean): List<CuratedResult> {
        if (results.isEmpty()) return results
        val sorted = results.sortedBy { it.photo.dateTaken }
        val dsu = IntArray(sorted.size) { it }

        fun find(i: Int): Int {
            var curr = i; while (dsu[curr] != curr) { dsu[curr] = dsu[dsu[curr]]; curr = dsu[curr] }; return curr
        }

        val window = if (isDeep) 15 else 5
        for (i in sorted.indices) {
            val m1 = cache[sorted[i].photo.id] ?: continue
            for (j in i + 1 until min(i + window, sorted.size)) {
                val m2 = cache[sorted[j].photo.id] ?: continue
                val timeDiff = abs(m1.dateTaken - m2.dateTaken)
                
                // Clustering Rule: High Affinity groups them
                if (calculateDisentangledAffinity(m1, m2, timeDiff, isDeep) >= 0.62f) {
                    val rI = find(i); val rJ = find(j)
                    if (rI != rJ) dsu[rI] = rJ
                }
            }
        }

        val clusters = mutableMapOf<Int, MutableList<CuratedResult>>()
        for (i in sorted.indices) clusters.getOrPut(find(i)) { mutableListOf() }.add(sorted[i])

        return clusters.values.flatMap { items ->
            if (items.size == 1) return@flatMap items
            val best = items.maxByOrNull { it.score }
            val cid = "cluster_${items.first().photo.id}"
            items.map { it.copy(clusterId = cid, isBestTake = it == best && it.score >= 0.58f,
                rejectionReason = if (it != best && it.rejectionReason == RejectionReason.NONE) RejectionReason.DUPLICATE else it.rejectionReason) }
        }
    }

    private fun calculateDisentangledAffinity(m1: PhotoMetadata, m2: PhotoMetadata, timeDiffMs: Long, isDeep: Boolean): Float {
        val timeDiffSec = timeDiffMs / 1000f
        if (timeDiffSec > 180f) return 0f // 3-minute hard boundary
        
        val tempWeight = exp(-timeDiffSec / 45f).toFloat()
        val bgSim = calculateHistogramIntersection(m1.backgroundSig, m2.backgroundSig)

        // Identity Signal
        var idSim = 0f
        if (isDeep && m1.faceEmbeddings.isNotEmpty() && m2.faceEmbeddings.isNotEmpty()) {
            for (f1 in m1.faceEmbeddings) for (f2 in m2.faceEmbeddings) {
                idSim = max(idSim, calculateCosineSimilarity(f1, f2))
            }
        }

        return when {
            // Rule: Same place + same person = SAME CLUSTER (regardless of pose)
            m1.isPortrait && m2.isPortrait && idSim > 0.78f && bgSim > 0.72f -> 0.85f * tempWeight
            // Rule: Same person + different place = NEW MOMENT
            m1.isPortrait && m2.isPortrait && idSim > 0.82f && bgSim < 0.45f -> 0.15f
            else -> {
                val visSim = if (isDeep && m1.visualEmbedding.isNotEmpty()) calculateCosineSimilarity(m1.visualEmbedding, m2.visualEmbedding) else bgSim
                ((visSim * 0.7f) + (bgSim * 0.3f)) * tempWeight
            }
        }
    }

    private fun evaluateSubjectAwareQuality(faces: List<Face>, tech: TechnicalMetrics, aesthetic: Float): AnalysisEvaluation {
        val techScore = (tech.sharpness * 0.7f) + (tech.exposureHealth * 0.3f)
        if (tech.sharpness < 0.12f) return AnalysisEvaluation(0.1f, RejectionReason.BLURRY, ScoreBreakdown(techScore, 0f, 0f, 0f))
        
        var exprScore = 0.8f; var eyesClosed = 0
        if (faces.isNotEmpty()) {
            var total = 0f
            for (f in faces) {
                val eyes = ((f.leftEyeOpenProbability ?: 1f) + (f.rightEyeOpenProbability ?: 1f)) / 2f
                if (eyes < 0.45f) eyesClosed++
                total += (if (eyes > 0.5f) 0.7f + (f.smilingProbability ?: 0f) * 0.3f else eyes)
            }
            exprScore = total / faces.size
        }

        val score = ((exprScore * 0.4f) + (techScore * 0.2f) + (aesthetic * 0.4f)).coerceIn(0f, 1f)
        val reason = when {
            eyesClosed > 0 -> RejectionReason.EYES_CLOSED
            exprScore < 0.4f -> RejectionReason.BAD_EXPRESSION
            tech.exposureHealth < 0.3f -> RejectionReason.POOR_LIGHTING
            else -> RejectionReason.NONE
        }
        return AnalysisEvaluation(score, reason, ScoreBreakdown(techScore, exprScore, aesthetic, 0.5f))
    }

    private fun computeBackgroundSignature(bitmap: Bitmap, subjectBounds: Rect?): FloatArray {
        val hsvHist = FloatArray(32); val w = bitmap.width; val h = bitmap.height; val skip = 10
        var count = 0
        for (y in 0 until h step skip) for (x in 0 until w step skip) {
            if (subjectBounds != null && subjectBounds.contains(x, y)) continue
            val p = bitmap.getPixel(x, y); val hsv = FloatArray(3); Color.colorToHSV(p, hsv)
            hsvHist[(hsv[0]/360f*15).toInt().coerceIn(0,15)]++
            hsvHist[16+(hsv[1]*7).toInt().coerceIn(0,7)]++
            hsvHist[24+(hsv[2]*7).toInt().coerceIn(0,7)]++
            count++
        }
        if (count > 0) for (i in hsvHist.indices) hsvHist[i] /= count
        return hsvHist
    }

    private fun calculateTechnicalMetrics(bitmap: Bitmap, subject: SubjectAnalysis): TechnicalMetrics {
        val w = bitmap.width; val h = bitmap.height
        val pixels = IntArray(w * h); bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        var clipped = 0; var crushed = 0
        for (p in pixels) {
            val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
            if (r > 250 && g > 250 && b > 250) clipped++
            if (r < 6 && g < 6 && b < 6) crushed++
        }
        val exp = (1.0f - (clipped.toFloat()/pixels.size * 2f + crushed.toFloat()/pixels.size * 1.2f)).coerceIn(0f, 1f)
        val b = subject.bounds ?: Rect(0, 0, w, h)
        val s = Rect(max(1, b.left), max(1, b.top), min(w-2, b.right), min(h-2, b.bottom))
        var lapVar = 0.0; var samples = 0
        for (y in s.top until s.bottom step 4) for (x in s.left until s.right step 4) {
            val i = y * w + x; val lum = getLuminance(pixels[i])
            lapVar += (getLuminance(pixels[i-1]) + getLuminance(pixels[i+1]) + getLuminance(pixels[i-w]) + getLuminance(pixels[i+w]) - 4 * lum).pow(2)
            samples++
        }
        return TechnicalMetrics(if (samples > 0) ((lapVar / samples) * 1000f).toFloat().coerceIn(0f, 1f) else 0.5f, exp, 0f, 0f)
    }

    private fun loadOrientedBitmap(context: Context, uri: Uri, maxW: Int, maxH: Int): Bitmap {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        var ss = 1; while ((opts.outWidth/ss) > maxW || (opts.outHeight/ss) > maxH) ss *= 2
        opts.inJustDecodeBounds = false; opts.inSampleSize = ss
        val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: throw Exception("Fail")
        val ori = context.contentResolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) } ?: 1
        val rot = when(ori) { 6 -> 90f; 3 -> 180f; 8 -> 270f; else -> 0f }
        return if (rot != 0f) {
            val mat = Matrix().apply { postRotate(rot) }
            Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, mat, true).also { if (it != bmp) bmp.recycle() }
        } else bmp
    }

    override fun close() {
        runCatching { faceDetector.close(); labeler.close(); objectDetector.close()
            embeddingEngine?.close(); nimaEngine?.close(); faceIdentityEngine?.close() }
    }

    private fun calculateHistogramIntersection(h1: FloatArray, h2: FloatArray): Float {
        var intersect = 0f; for (i in h1.indices) intersect += min(h1[i], h2[i]); return intersect / 3f
    }

    private fun calculateCosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
        if (v1.isEmpty() || v1.size != v2.size) return 0f
        var dot = 0f; var n1 = 0f; var n2 = 0f
        for (i in v1.indices) { dot += v1[i] * v2[i]; n1 += v1[i] * v1[i]; n2 += v2[i] * v2[i] }
        val den = sqrt(n1) * sqrt(n2); return if (den > 0) dot / den else 0f
    }

    private fun extractSubjectAnalysis(p: MediaPhoto, l: List<ImageLabel>, f: List<Face>, o: List<DetectedObject>, w: Float, h: Float): SubjectAnalysis {
        if (f.isNotEmpty()) {
            val b = Rect(f.minOf { it.boundingBox.left }, f.minOf { it.boundingBox.top }, f.maxOf { it.boundingBox.right }, f.maxOf { it.boundingBox.bottom })
            val n = normalizeRect(b, w, h); return SubjectAnalysis(SubjectCategory.PEOPLE, b, n, n.width() * n.height(), 0.9f, "People")
        }
        val obj = o.maxByOrNull { it.boundingBox.width() }
        if (obj != null) {
            val n = normalizeRect(obj.boundingBox, w, h); return SubjectAnalysis(SubjectCategory.OBJECTS, obj.boundingBox, n, n.width() * n.height(), 0.7f, obj.labels.firstOrNull()?.text ?: "Object")
        }
        return SubjectAnalysis(SubjectCategory.SCENERY, null, null, 1f, 0.5f, l.firstOrNull()?.text ?: "Scene")
    }

    private suspend fun buildDescription(s: SubjectAnalysis, l: Set<String>, deep: Boolean, nano: GeminiNanoEngine?, bmp: Bitmap): String {
        if (deep) nano?.generateDeepDescription(bmp)?.let { return it }
        return "${s.mainSubjectName} | ${l.filter { !it.startsWith("COLOR_") }.take(6).joinToString(", ")}"
    }

    private fun extractColorLabels(bitmap: Bitmap, bounds: Rect?): List<String> {
        val t = bounds ?: Rect(0, 0, bitmap.width, bitmap.height)
        val colors = mutableMapOf<String, Int>()
        for (iy in 0 until 5) for (ix in 0 until 5) {
            val p = bitmap.getPixel((t.left + ix * (t.width()/5)).coerceIn(0, bitmap.width - 1), (t.top + iy * (t.height()/5)).coerceIn(0, bitmap.height - 1))
            val hsv = FloatArray(3); Color.colorToHSV(p, hsv)
            val name = when { hsv[2] < 0.15f -> "Black"; hsv[1] < 0.15f -> "Gray"; hsv[0] < 20 || hsv[0] > 340 -> "Red"; hsv[0] < 50 -> "Orange"; hsv[0] < 70 -> "Yellow"; hsv[0] < 160 -> "Green"; hsv[0] < 260 -> "Blue"; else -> "Purple" }
            colors[name] = (colors[name] ?: 0) + 1
        }
        return colors.entries.sortedByDescending { it.value }.take(2).map { "COLOR_${it.key}" }
    }

    private fun getLuminance(p: Int) = (0.299f * ((p shr 16) and 0xFF) + 0.587f * ((p shr 8) and 0xFF) + 0.114f * (p and 0xFF)) / 255f
    private fun normalizeRect(r: Rect, w: Float, h: Float) = RectF(r.left / w, r.top / h, r.right / w, r.bottom / h)
    private data class AnalysisEvaluation(val score: Float, val reason: RejectionReason, val breakdown: ScoreBreakdown)
}
