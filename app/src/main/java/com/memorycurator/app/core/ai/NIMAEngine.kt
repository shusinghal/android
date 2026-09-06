package com.memorycurator.app.core.ai

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.io.FileInputStream
import java.nio.channels.FileChannel

/**
 * NIMA: Neural Image Assessment
 * Uses a deep CNN to predict the aesthetic quality of images.
 * Output is typically a distribution over scores 1-10.
 */
class NIMAEngine(context: Context) {
    private var interpreter: Interpreter? = null
    private val modelPath = "models/nima_aesthetic.tflite"

    init {
        try {
            val assetFileDescriptor = context.assets.openFd(modelPath)
            val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = assetFileDescriptor.startOffset
            val declaredLength = assetFileDescriptor.length
            val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
            
            interpreter = Interpreter(modelBuffer)
        } catch (e: Exception) {
            android.util.Log.e("NIMAEngine", "Note: NIMA model not found. Using standard heuristics.")
        }
    }

    /**
     * Calculates an aesthetic score from 0.0 to 1.0.
     */
    fun calculateAestheticScore(bitmap: Bitmap): Float {
        val interp = interpreter ?: return 0.5f

        // 1. Pre-process Image: Resize to 224x224 (Standard for MobileNet-based NIMA)
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true)
        
        // 2. Prepare Input Buffer
        val inputBuffer = ByteBuffer.allocateDirect(1 * 224 * 224 * 3 * 4).apply {
            order(ByteOrder.nativeOrder())
        }
        
        val intValues = IntArray(224 * 224)
        scaledBitmap.getPixels(intValues, 0, 224, 0, 0, 224, 224)
        
        for (pixelValue in intValues) {
            inputBuffer.putFloat(((pixelValue shr 16 and 0xFF) / 255f))
            inputBuffer.putFloat(((pixelValue shr 8 and 0xFF) / 255f))
            inputBuffer.putFloat(((pixelValue and 0xFF) / 255f))
        }

        // 3. Prepare Output Buffer (Distribution over 10 scores)
        val outputBuffer = ByteBuffer.allocateDirect(10 * 4).apply {
            order(ByteOrder.nativeOrder())
        }

        // 4. Run Inference
        interp.run(inputBuffer, outputBuffer)

        // 5. Calculate Mean Score (Expected Value)
        outputBuffer.rewind()
        val distribution = FloatArray(10)
        outputBuffer.asFloatBuffer().get(distribution)
        
        var meanScore = 0f
        for (i in distribution.indices) {
            meanScore += distribution[i] * (i + 1)
        }

        scaledBitmap.recycle()
        
        // Normalize 1-10 scale to 0-1 range
        return (meanScore / 10f).coerceIn(0f, 1f)
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
