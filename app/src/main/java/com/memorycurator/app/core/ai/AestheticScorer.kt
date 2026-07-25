package com.memorycurator.app.core.ai

import android.graphics.Bitmap
import android.graphics.Color
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.label.ImageLabel
import kotlin.math.abs
import kotlin.math.sqrt

class AestheticScorer {

    data class AestheticResult(
        val overallScore: Float,
        val isIntentionalClosedEyes: Boolean = false
    )

    fun calculateAestheticScore(
        bitmap: Bitmap,
        faces: List<Face>,
        labels: List<ImageLabel>
    ): AestheticResult {
        // 1. Semantic Intent Check
        // Certain scenes make "closed eyes" or "low lighting" a stylistic choice.
        val intentLabels = setOf(
            "Prayer", "Spirituality", "Worship", "Meditation", 
            "Sleep", "Baby", "Cradle", "Sunset", "Candle", "Concert"
        )
        
        val hasHighIntent = labels.any { label -> 
            intentLabels.any { intent -> label.text.contains(intent, ignoreCase = true) } 
        }

        // 2. Composition Score (Rule of Thirds)
        val compositionScore = calculateRuleOfThirds(bitmap.width, bitmap.height, faces)

        // 3. Color Harmony & Vibrancy
        val colorScore = calculateColorHarmony(bitmap)

        // 4. Contrast & Dynamic Range
        val contrastScore = calculateContrast(bitmap)

        // Final weighting for the "Art Critic" logic
        var finalScore = (compositionScore * 0.4f) + (colorScore * 0.3f) + (contrastScore * 0.3f)

        // "Intentionality" Logic: 
        // If a photo is beautifully composed and has high semantic intent, 
        // we assume closed eyes are intentional.
        val isIntentionalClosedEyes = hasHighIntent && finalScore > 0.65f

        return AestheticResult(
            overallScore = finalScore.coerceIn(0f, 1f),
            isIntentionalClosedEyes = isIntentionalClosedEyes
        )
    }

    private fun calculateRuleOfThirds(width: Int, height: Int, faces: List<Face>): Float {
        if (faces.isEmpty()) return 0.5f // Default for scenery
        
        // Ideal points for faces are at 1/3 or 2/3 of width/height
        val thirdW = width / 3f
        val thirdH = height / 3f
        val idealPoints = listOf(
            Pair(thirdW, thirdH), Pair(thirdW * 2, thirdH),
            Pair(thirdW, thirdH * 2), Pair(thirdW * 2, thirdH * 2)
        )

        var bestMatch = 0f
        for (face in faces) {
            val fx = face.boundingBox.centerX().toFloat()
            val fy = face.boundingBox.centerY().toFloat()
            
            for ((ix, iy) in idealPoints) {
                val dist = sqrt((fx - ix).toDouble().pow(2.0) + (fy - iy).toDouble().pow(2.0)).toFloat()
                val normalizedDist = 1f - (dist / (width / 2f)).coerceIn(0f, 1f)
                if (normalizedDist > bestMatch) bestMatch = normalizedDist
            }
        }
        return bestMatch
    }

    private fun calculateColorHarmony(bitmap: Bitmap): Float {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        var totalSaturation = 0f
        for (pixel in pixels) {
            val hsv = FloatArray(3)
            Color.colorToHSV(pixel, hsv)
            totalSaturation += hsv[1]
        }
        val avgSaturation = totalSaturation / pixels.size
        // 0.2 - 0.6 is a natural, pleasing range.
        return (avgSaturation / 0.6f).coerceIn(0f, 1f)
    }

    private fun calculateContrast(bitmap: Bitmap): Float {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        var minLum = 1f
        var maxLum = 0f
        
        // Sample every 10th pixel for performance
        for (i in pixels.indices step 10) {
            val lum = getLuminance(pixels[i])
            if (lum < minLum) minLum = lum
            if (lum > maxLum) maxLum = lum
        }
        
        return (maxLum - minLum).coerceIn(0f, 1f)
    }

    private fun getLuminance(pixel: Int): Float {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return (0.299f * r + 0.587f * g + 0.114f * b) / 255f
    }
    
    private fun Double.pow(n: Double) = Math.pow(this, n)
}
