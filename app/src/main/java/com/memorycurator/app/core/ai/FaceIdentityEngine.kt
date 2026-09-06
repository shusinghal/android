package com.memorycurator.app.core.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.io.FileInputStream
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min

/**
 * Point 4: Facial Identity Engine
 * Uses FaceNet to generate unique identity vectors for detected faces.
 */
class FaceIdentityEngine(context: Context) {
    private var interpreter: Interpreter? = null
    private val modelPath = "models/facenet_mobile.tflite"
    private val inputSize = 112 // Updated for MobileFaceNet standard

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
            android.util.Log.e("FaceIdentityEngine", "Note: FaceNet model not found. Identity-based clustering will be unavailable.")
        }
    }

    /**
     * Generates a 192-dimension embedding for a face.
     */
    fun extractFaceEmbedding(fullBitmap: Bitmap, faceBounds: Rect): FloatArray {
        val interp = interpreter ?: return FloatArray(0)

        // 1. Crop face with slight margin
        val margin = (faceBounds.width() * 0.1f).toInt()
        val cropRect = Rect(
            max(0, faceBounds.left - margin),
            max(0, faceBounds.top - margin),
            min(fullBitmap.width, faceBounds.right + margin),
            min(fullBitmap.height, faceBounds.bottom + margin)
        )
        
        val faceCrop = Bitmap.createBitmap(fullBitmap, cropRect.left, cropRect.top, cropRect.width(), cropRect.height())
        val scaledFace = Bitmap.createScaledBitmap(faceCrop, inputSize, inputSize, true)

        // 2. Prepare Input Buffer
        val inputBuffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4).apply {
            order(ByteOrder.nativeOrder())
        }
        
        val intValues = IntArray(inputSize * inputSize)
        scaledFace.getPixels(intValues, 0, inputSize, 0, 0, inputSize, inputSize)
        
        for (pixelValue in intValues) {
            // FaceNet usually expects normalization: (x - 127.5) / 128.0
            inputBuffer.putFloat(((pixelValue shr 16 and 0xFF) - 127.5f) / 128f)
            inputBuffer.putFloat(((pixelValue shr 8 and 0xFF) - 127.5f) / 128f)
            inputBuffer.putFloat(((pixelValue and 0xFF) - 127.5f) / 128f)
        }

        // 3. Prepare Output Buffer (192 dimensions for MobileFaceNet)
        val outputBuffer = ByteBuffer.allocateDirect(192 * 4).apply {
            order(ByteOrder.nativeOrder())
        }

        // 4. Run Inference
        interp.run(inputBuffer, outputBuffer)

        // 5. Results
        val result = FloatArray(192)
        outputBuffer.rewind()
        outputBuffer.asFloatBuffer().get(result)
        
        faceCrop.recycle()
        scaledFace.recycle()
        
        return result
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
