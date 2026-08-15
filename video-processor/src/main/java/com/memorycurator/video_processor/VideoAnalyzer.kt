package com.memorycurator.video_processor

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class VideoAnalyzer(private val context: Context) {

    private val noiseReducer = AudioNoiseReducer(context)
    private val transcriber = AudioTranscriber(context)
    private val audioCurator = AudioCurator(context)
    private val frameAnalyzer = VideoFrameAnalyzer(context)

    /**
     * Complete pipeline to analyze a video and determine if it's a 'Keeper'.
     */
    suspend fun fullAnalysis(videoUri: Uri): VideoCurationResult = withContext(Dispatchers.IO) {
        // 1. Extract Audio
        val rawAudio = extractAudio(videoUri)
        
        // 2. Noise Reduction
        val cleanAudio = rawAudio?.let { noiseReducer.process(it) }
        
        // 3. Transcription
        val transcriptFile = cleanAudio?.let { transcriber.transcribe(it) }
        
        // 4. Identify Audio Highlights
        val highlights = transcriptFile?.let { audioCurator.identifyHighlights(it) } ?: emptyList()
        
        // 5. Visual Review (2-3 FPS)
        val visualScore = frameAnalyzer.analyzeVideoQuality(videoUri, sampleFps = 2)
        
        // 6. Categorization Logic
        val isKeeper = visualScore > 0.7f && highlights.isNotEmpty()
        
        VideoCurationResult(
            isKeeper = isKeeper,
            visualScore = visualScore,
            hasHighlights = highlights.isNotEmpty(),
            transcriptFile = transcriptFile
        )
    }

    /**
     * Extracts audio from the given video URI.
     */
    suspend fun extractAudio(videoUri: Uri): File? = suspendCoroutine { continuation ->
        val outputFile = File(context.cacheDir, "extracted_audio_${System.currentTimeMillis()}.m4a")
        val transformer = Transformer.Builder(context).setRemoveVideo(true).build()
        val mediaItem = MediaItem.fromUri(videoUri)
        
        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                continuation.resume(outputFile)
            }
            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                continuation.resumeWithException(exportException)
            }
        }
        transformer.addListener(listener)
        try {
            transformer.start(mediaItem, outputFile.absolutePath)
        } catch (e: Exception) {
            continuation.resumeWithException(e)
        }
    }
}

data class VideoCurationResult(
    val isKeeper: Boolean,
    val visualScore: Float,
    val hasHighlights: Boolean,
    val transcriptFile: File?
)
