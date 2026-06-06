package com.gallery.photocleaner.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder
import com.google.mediapipe.tasks.components.containers.Embedding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class ImageAIService(private val context: Context) {

    private var imageEmbedder: ImageEmbedder? = null

    init {
        try {
            // MediaPipe Embedder configuration utilizing GPU delegation for fast performance
            val baseOptionsBuilder = BaseOptions.builder()
                .setModelAssetPath("mobilenet_v3_small_100_224_embedder.tflite")
                .setDelegate(BaseOptions.Delegate.GPU)

            val options = ImageEmbedder.ImageEmbedderOptions.builder()
                .setBaseOptions(baseOptionsBuilder.build())
                .setRunningMode(RunningMode.IMAGE)
                .setQuantize(true)
                .build()

            imageEmbedder = ImageEmbedder.createFromOptions(context, options)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Extracts a high-dimensional feature vector from a photo.
     */
    suspend fun extractFeatureVector(uri: Uri): Embedding? = withContext(Dispatchers.IO) {
        val bitmap = loadBitmapFromUri(uri) ?: return@withContext null
        try {
            val mpImage = com.google.mediapipe.framework.image.BitmapImageBuilder(bitmap).build()
            val result = imageEmbedder?.embed(mpImage)
            result?.embeddingResult()?.embeddings()?.firstOrNull()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Determines cosine similarity index between two feature vectors.
     */
    fun calculateSimilarity(emb1: Embedding, emb2: Embedding): Float {
        return try {
            ImageEmbedder.cosineSimilarity(emb1, emb2).toFloat()
        } catch (e: Exception) {
            0f
        }
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.isMutableRequired = true
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
        } catch (e: IOException) {
            null
        }
    }
}