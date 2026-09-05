package com.memorycurator.app.core.ai

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.label.ImageLabel
import kotlin.math.abs
import kotlin.math.sqrt

class AestheticScorer {

    data class AestheticResult(
        val overallScore: Float,
        val isIntentionalClosedEyes: Boolean = false
    )

    /**
     * Scores an image based on color harmony, contrast, and brightness distribution.
     * Uses pixel sampling for performance.
     */
    fun calculateAestheticScore(
        bitmap: Bitmap,
        faces: List<Face>,
        labels: List<ImageLabel>,
        subjectBounds: Rect? = null
    ): AestheticResult {
        // 1. Semantic Intent Check
        val intentLabels = setOf(
            "Prayer", "Spirituality", "Worship", "Meditation", 
            "Sleep", "Baby", "Cradle", "Sunset", "Candle", "Concert"
        )
        
        val hasHighIntent = labels.any { label -> 
            intentLabels.any { intent -> label.text.contains(intent, ignoreCase = true) } 
        }

        // 2. Composition Score (Rule of Thirds + Visual Salience)
        val compositionScore = calculateComposition(bitmap, faces, subjectBounds)

        // 3. Color Harmony & Vibrancy
        val colorScore = calculateColorHarmony(bitmap)

        // 4. Contrast & Dynamic Range
        val contrastScore = calculateContrast(bitmap)

        // 5. Brightness Distribution & Exposure Health
        val brightnessScore = calculateBrightnessDistribution(bitmap)

        // Final weighting for the "Art Critic" logic
        val finalScore = (compositionScore * 0.35f) + 
                         (colorScore * 0.25f) + 
                         (contrastScore * 0.20f) + 
                         (brightnessScore * 0.20f)

        // "Intentionality" Logic: 
        // If a photo is beautifully composed and has high semantic intent, 
        // we assume closed eyes are intentional.
        val isIntentionalClosedEyes = hasHighIntent && (finalScore > 0.65f)

        return AestheticResult(
            overallScore = finalScore.coerceIn(0f, 1f),
            isIntentionalClosedEyes = isIntentionalClosedEyes
        )
    }

    private fun calculateComposition(
        bitmap: Bitmap, 
        faces: List<Face>,
        subjectBounds: Rect? = null
    ): Float {
        val width = bitmap.width
        val height = bitmap.height
        val thirdW = width / 3f
        val thirdH = height / 3f
        val idealPoints = listOf(
            Pair(thirdW, thirdH), Pair(thirdW * 2, thirdH),
            Pair(thirdW, thirdH * 2), Pair(thirdW * 2, thirdH * 2)
        )

        if (faces.isNotEmpty()) {
            var bestMatch = 0f
            for (face in faces) {
                val fx = face.boundingBox.centerX().toFloat()
                val fy = face.boundingBox.centerY().toFloat()
                
                for ((ix, iy) in idealPoints) {
                    val normDx = abs(fx - ix) / (width / 2f)
                    val normDy = abs(fy - iy) / (height / 2f)
                    val dist = sqrt((normDx * normDx + normDy * normDy).toDouble()).toFloat()
                    val normalizedDist = (1f - dist / 1.2f).coerceIn(0f, 1f)
                    if (normalizedDist > bestMatch) bestMatch = normalizedDist
                }
            }
            return bestMatch
        } else if (subjectBounds != null && !subjectBounds.isEmpty) {
            val sx = subjectBounds.centerX().toFloat()
            val sy = subjectBounds.centerY().toFloat()
            var bestMatch = 0f
            for ((ix, iy) in idealPoints) {
                val normDx = abs(sx - ix) / (width / 2f)
                val normDy = abs(sy - iy) / (height / 2f)
                val dist = sqrt((normDx * normDx + normDy * normDy).toDouble()).toFloat()
                val normalizedDist = (1f - dist / 1.2f).coerceIn(0f, 1f)
                if (normalizedDist > bestMatch) bestMatch = normalizedDist
            }
            return bestMatch
        } else {
            return calculateNonFacialComposition(bitmap)
        }
    }

    private fun calculateNonFacialComposition(bitmap: Bitmap): Float {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 6 || height < 6) return 0.5f

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val gridEdges = FloatArray(9)
        val cellW = width / 3
        val cellH = height / 3

        for (cy in 0 until 3) {
            for (cx in 0 until 3) {
                var edgeSum = 0.0
                var samples = 0
                val startX = cx * cellW
                val startY = cy * cellH
                val endX = if (cx == 2) width - 1 else startX + cellW - 1
                val endY = if (cy == 2) height - 1 else startY + cellH - 1

                for (y in startY until endY step 3) {
                    for (x in startX until endX step 3) {
                        val idx = y * width + x
                        val rightIdx = y * width + (x + 1)
                        val downIdx = (y + 1) * width + x
                        if (rightIdx < pixels.size && downIdx < pixels.size) {
                            val lum = getLuminance(pixels[idx])
                            val lumR = getLuminance(pixels[rightIdx])
                            val lumD = getLuminance(pixels[downIdx])
                            val dx = lumR - lum
                            val dy = lumD - lum
                            edgeSum += sqrt(dx * dx + dy * dy)
                            samples++
                        }
                    }
                }
                gridEdges[cy * 3 + cx] = if (samples > 0) (edgeSum / samples).toFloat() else 0f
            }
        }

        val totalEnergy = gridEdges.sum()
        if (totalEnergy <= 0.0001f) return 0.5f

        val centerEnergy = gridEdges[4]
        val powerPointEnergy = gridEdges[1] + gridEdges[3] + gridEdges[5] + gridEdges[7]
        val powerRatio = (powerPointEnergy + centerEnergy * 0.8f) / totalEnergy
        val balance = 1f - abs(gridEdges[3] + gridEdges[0] + gridEdges[6] - (gridEdges[5] + gridEdges[2] + gridEdges[8])) / totalEnergy

        return (powerRatio * 0.6f + balance * 0.4f).coerceIn(0.35f, 0.95f)
    }

    private fun calculateColorHarmony(bitmap: Bitmap): Float {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        var totalSaturation = 0f
        val step = (pixels.size / 500).coerceAtLeast(1)
        var sampleCount = 0
        val hsv = FloatArray(3)
        var minSat = 1f
        var maxSat = 0f

        for (i in pixels.indices step step) {
            Color.colorToHSV(pixels[i], hsv)
            val sat = hsv[1]
            totalSaturation += sat
            if (sat < minSat) minSat = sat
            if (sat > maxSat) maxSat = sat
            sampleCount++
        }
        val avgSaturation = if (sampleCount > 0) totalSaturation / sampleCount else 0.5f
        val satSpread = (maxSat - minSat).coerceIn(0f, 1f)
        
        val satScore = when {
            avgSaturation in 0.15f..0.75f -> 1.0f
            avgSaturation < 0.15f -> (avgSaturation / 0.15f).coerceIn(0.3f, 1.0f)
            else -> (1.0f - (avgSaturation - 0.75f) * 2f).coerceIn(0.4f, 1.0f)
        }

        return (satScore * 0.7f + satSpread * 0.3f).coerceIn(0f, 1f)
    }

    private fun calculateContrast(bitmap: Bitmap): Float {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        var minLum = 1f
        var maxLum = 0f
        var sumLum = 0.0
        var sumSqLum = 0.0
        val step = (pixels.size / 500).coerceAtLeast(1)
        var count = 0
        
        for (i in pixels.indices step step) {
            val lum = getLuminance(pixels[i])
            if (lum < minLum) minLum = lum
            if (lum > maxLum) maxLum = lum
            sumLum += lum
            sumSqLum += lum * lum
            count++
        }

        if (count == 0) return 0.5f
        val meanLum = sumLum / count
        val variance = (sumSqLum / count) - (meanLum * meanLum)
        val stdDev = sqrt(variance.coerceAtLeast(0.0)).toFloat()
        val dynamicRange = (maxLum - minLum).coerceIn(0f, 1f)
        
        val stdDevScore = (stdDev / 0.25f).coerceIn(0.3f, 1.0f)
        return (dynamicRange * 0.5f + stdDevScore * 0.5f).coerceIn(0f, 1f)
    }

    /**
     * Scores brightness distribution based on histogram balance.
     * Penalizes overexposure (clipping) and underexposure (crushing).
     */
    private fun calculateBrightnessDistribution(bitmap: Bitmap): Float {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var highlightClipped = 0
        var shadowCrushed = 0
        var totalLum = 0.0
        val step = (pixels.size / 1000).coerceAtLeast(1)
        var count = 0

        for (i in pixels.indices step step) {
            val lum = getLuminance(pixels[i])
            if (lum > 0.98f) highlightClipped++
            if (lum < 0.02f) shadowCrushed++
            totalLum += lum
            count++
        }

        if (count == 0) return 0.5f

        val avgBrightness = (totalLum / count).toFloat()
        val highlightRatio = highlightClipped.toFloat() / count
        val shadowRatio = shadowCrushed.toFloat() / count

        // Ideal average brightness is around 0.5 (middle gray)
        val brightnessBalance = 1.0f - abs(avgBrightness - 0.5f)
        
        // Penalize heavy clipping or crushing
        val exposureScore = (1.0f - (highlightRatio * 2f + shadowRatio * 1.5f)).coerceIn(0f, 1f)

        return (brightnessBalance * 0.4f + exposureScore * 0.6f).coerceIn(0f, 1f)
    }

    private fun getLuminance(pixel: Int): Float {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return (0.299f * r + 0.587f * g + 0.114f * b) / 255f
    }
}
