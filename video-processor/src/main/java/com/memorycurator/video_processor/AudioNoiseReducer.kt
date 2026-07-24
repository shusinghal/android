package com.memorycurator.video_processor

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.ByteOrder
import java.nio.ShortBuffer

class AudioNoiseReducer(private val context: Context) {

    private var interpreter: Interpreter? = null
    
    // AI Parameters (Based on standard DTLN model)
    private val sampleRate = 16000
    private val frameSize = 512
    private val shiftSize = 128
    
    // LSTM States for the AI model
    private var state1 = FloatArray(128 * 2) 
    private var state2 = FloatArray(128 * 2)

    /**
     * Processes the audio file to reduce noise using AI.
     */
    suspend fun process(audioFile: File): File = withContext(Dispatchers.IO) {
        val outputFile = File(audioFile.parent, "denoised_${audioFile.name}")
        
        val extractor = MediaExtractor()
        extractor.setDataSource(audioFile.absolutePath)
        
        val trackIndex = selectAudioTrack(extractor)
        if (trackIndex < 0) return@withContext audioFile
        
        extractor.selectTrack(trackIndex)
        val inputFormat = extractor.getTrackFormat(trackIndex)
        
        val decoder = MediaCodec.createDecoderByType(inputFormat.getString(MediaFormat.KEY_MIME)!!)
        decoder.configure(inputFormat, null, null, 0)
        decoder.start()

        val outputFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1)
        outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, 128000)
        outputFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var audioTrackIndex = -1
        
        val bufferInfo = MediaCodec.BufferInfo()
        var isExtractorDone = false
        var isDecoderDone = false
        var isEncoderDone = false

        while (!isEncoderDone) {
            // A. Feed Extractor to Decoder
            if (!isExtractorDone) {
                val inputBufferIndex = decoder.dequeueInputBuffer(10000)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inputBufferIndex)!!
                    val size = extractor.readSampleData(inputBuffer, 0)
                    if (size < 0) {
                        decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        isExtractorDone = true
                    } else {
                        decoder.queueInputBuffer(inputBufferIndex, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            // B. Decoder -> AI -> Encoder
            if (!isDecoderDone) {
                val outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, 10000)
                if (outputBufferIndex >= 0) {
                    val outputBuffer = decoder.getOutputBuffer(outputBufferIndex)!!
                    
                    // 1. Convert ByteBuffer (raw PCM) to ShortArray
                    val pcmData = ShortArray(bufferInfo.size / 2)
                    outputBuffer.asShortBuffer().get(pcmData)
                    
                    // 2. Perform AI Noise Reduction
                    val denoisedPcm = performAIInference(pcmData)
                    
                    // 3. Feed to Encoder
                    val encoderInputIndex = encoder.dequeueInputBuffer(10000)
                    if (encoderInputIndex >= 0) {
                        val encoderInputBuffer = encoder.getInputBuffer(encoderInputIndex)!!
                        encoderInputBuffer.order(ByteOrder.nativeOrder())
                        encoderInputBuffer.asShortBuffer().put(denoisedPcm)
                        encoder.queueInputBuffer(encoderInputIndex, 0, denoisedPcm.size * 2, bufferInfo.presentationTimeUs, bufferInfo.flags)
                    }
                    
                    decoder.releaseOutputBuffer(outputBufferIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        isDecoderDone = true
                    }
                }
            }

            // C. Encoder to Muxer
            val encoderOutputIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000)
            if (encoderOutputIndex >= 0) {
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                    encoder.releaseOutputBuffer(encoderOutputIndex, false)
                    continue
                }
                
                val encoderOutputBuffer = encoder.getOutputBuffer(encoderOutputIndex)!!
                if (audioTrackIndex == -1) {
                    audioTrackIndex = muxer.addTrack(encoder.outputFormat)
                    muxer.start()
                }
                
                muxer.writeSampleData(audioTrackIndex, encoderOutputBuffer, bufferInfo)
                encoder.releaseOutputBuffer(encoderOutputIndex, false)
                
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    isEncoderDone = true
                }
            }
        }

        cleanup(decoder, encoder, muxer, extractor)
        outputFile
    }

    private fun performAIInference(inputPcm: ShortArray): ShortArray {
        // AI Denoising Logic:
        // Normally we'd use a rolling buffer here because DTLN expects 512 samples with 128 shift.
        // For this increment, we implement the normalization and skeleton for the TFLite call.
        
        val floatInput = FloatArray(inputPcm.size) { inputPcm[it] / 32768f }
        val floatOutput = FloatArray(floatInput.size)

        // TODO: Loop through floatInput in 512 chunks, run interpreter, and overlap-add to floatOutput
        // interpreter?.runForMultipleInputsOutputs(...)
        
        // Placeholder: copying input to output (until model is integrated)
        System.arraycopy(floatInput, 0, floatOutput, 0, floatInput.size)

        return ShortArray(floatOutput.size) { (floatOutput[it] * 32767f).toInt().toShort() }
    }

    private fun selectAudioTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) return i
        }
        return -1
    }

    private fun cleanup(decoder: MediaCodec, encoder: MediaCodec, muxer: MediaMuxer, extractor: MediaExtractor) {
        try {
            decoder.stop()
            decoder.release()
            encoder.stop()
            encoder.release()
            muxer.stop()
            muxer.release()
            extractor.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun close() {
        interpreter?.close()
    }
}
