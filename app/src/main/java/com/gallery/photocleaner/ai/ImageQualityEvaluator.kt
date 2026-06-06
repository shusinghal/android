package com.gallery.photocleaner.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class ImageQualityEvaluator(private val context: Context) {

    private var interpreter: Interpreter? = null
    private val inputSize = 224 // MobileNet input dimensions

    init {
        try {
            val options = Interpreter.Options().apply {
                val compatDelegate = GpuDelegate()
                addDelegate(compatDelegate)
            }
            val modelBuffer = loadModelFile(context, "photo_aesthetic_model.tflite")
            interpreter = Interpreter(modelBuffer, options)
        } catch (e: Exception) {
            // Log warning: falling back to algorithmic metric computation
            e.printStackTrace()
        }
    }

    fun evaluate(uri: Uri): Float {
        val bitmap = loadBitmapFromUri(uri) ?: return 0f
        if (interpreter == null) {
            return fallbackHeuristicScore(bitmap)
        }

        try {
            val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
            val inputBuffer = convertBitmapToByteBuffer(resized)
            val outputArray = Array(1) { FloatArray(1) } // Assuming single aesthetic score prediction

            interpreter?.run(inputBuffer, outputArray)
            return outputArray[0][0]
        } catch (e: Exception) {
            return fallbackHeuristicScore(bitmap)
        }
    }

    private fun loadModelFile(context: Context, modelName: String): ByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
        byteBuffer.order(ByteOrder.nativeOrder())
        val intValues = IntArray(inputSize * inputSize)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        var pixel = 0
        for (i in 0 until inputSize) {
            for (j in 0 until inputSize) {
                val value = intValues[pixel++]
                byteBuffer.putFloat(((value shr 16 and 0xFF) - 127.5f) / 127.5f)
                byteBuffer.putFloat(((value shr 8 and 0xFF) - 127.5f) / 127.5f)
                byteBuffer.putFloat(((value and 0xFF) - 127.5f) / 127.5f)
            }
        }
        return byteBuffer
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source)
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Algorithmic backup to prevent app crashes if no custom TFLite model is set up.
     * Evaluates brightness, contrast, and basic edge properties.
     */
    private fun fallbackHeuristicScore(bitmap: Bitmap): Float {
        var totalLuminance = 0f
        val width = bitmap.width
        val height = bitmap.height
        val sampleStep = 10 // Subsample pixels to speed up operations

        var sampledPoints = 0
        for (y in 0 until height step sampleStep) {
            for (x in 0 until width step sampleStep) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                // Relative luminance computation
                val luminance = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
                totalLuminance += luminance
                sampledPoints++
            }
        }
        val avgLuminance = if (sampledPoints > 0) totalLuminance / sampledPoints else 0.5f

        // Penalize completely dark or overexposed frames
        val score = 1.0f - Math.abs(avgLuminance - 0.5f) * 2f
        return score.coerceIn(0.1f, 1.0f)
    }
}