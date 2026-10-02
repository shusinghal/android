package com.memorycurator.app.core.ai

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.*
import kotlinx.coroutines.tasks.await

/**
 * Point 3: Gemini Nano for Deep Semantic Description (VQA)
 * Uses on-device GenAI via Android AICore with graceful fallback on unsupported hardware.
 */
class GeminiNanoEngine(private val context: Context) {
    
    private val client: GenerativeModel? by lazy {
        try {
            Generation.getClient()
        } catch (e: Exception) {
            android.util.Log.w("GeminiNano", "GenerativeModel client not available on this device: ${e.message}")
            null
        }
    }
    
    suspend fun generateDeepDescription(bitmap: Bitmap): String? {
        val generativeClient = client ?: return null
        return try {
            val status = generativeClient.checkStatus()
            if (status == FeatureStatus.AVAILABLE) {
                val prompt = "Describe this image in detail for a gallery app. " +
                             "Include subjects, their expressions, clothing colors, and the overall scene " +
                             "so I could generate a similar image with AI."
                
                val request = GenerateContentRequest.builder(
                    ImagePart(bitmap),
                    TextPart(prompt)
                ).build()
                
                val response = generativeClient.generateContent(request)
                response.candidates.firstOrNull()?.text
            } else if (status == FeatureStatus.DOWNLOADABLE) {
                null
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("GeminiNano", "Error generating description: ${e.message}")
            null
        }
    }
}
