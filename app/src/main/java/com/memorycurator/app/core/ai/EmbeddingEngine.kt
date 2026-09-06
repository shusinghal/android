package com.memorycurator.app.core.ai

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.io.FileInputStream
import java.nio.channels.FileChannel

class EmbeddingEngine(context: Context) {
    private var interpreter: Interpreter? = null
    private val modelPath = "models/mobilenet_v3_feature_vector.tflite"

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
            android.util.Log.e("EmbeddingEngine", "Error: Deep AI model '$modelPath' not found. Fallback to pixel analysis.")
        }
    }

    /**
     * Extracts a semantic feature vector from an image.
     * Dimensions: 1280 (Standard for MobileNetV3 Feature Vector)
     */
    fun extractEmbedding(bitmap: Bitmap): FloatArray {
        val interp = interpreter ?: return FloatArray(0)

        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true)
        val inputBuffer = ByteBuffer.allocateDirect(1 * 224 * 224 * 3 * 4).apply {
            order(ByteOrder.nativeOrder())
        }
        
        val intValues = IntArray(224 * 224)
        scaledBitmap.getPixels(intValues, 0, 224, 0, 0, 224, 224)
        
        for (pixelValue in intValues) {
            inputBuffer.putFloat(((pixelValue shr 16 and 0xFF) - 127.5f) / 127.5f)
            inputBuffer.putFloat(((pixelValue shr 8 and 0xFF) - 127.5f) / 127.5f)
            inputBuffer.putFloat(((pixelValue and 0xFF) - 127.5f) / 127.5f)
        }

        val outputBuffer = ByteBuffer.allocateDirect(1280 * 4).apply {
            order(ByteOrder.nativeOrder())
        }

        interp.run(inputBuffer, outputBuffer)

        val result = FloatArray(1280)
        outputBuffer.rewind()
        outputBuffer.asFloatBuffer().get(result)
        
        scaledBitmap.recycle()
        return result
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
