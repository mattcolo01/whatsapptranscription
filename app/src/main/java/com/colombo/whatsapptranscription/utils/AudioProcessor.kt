package com.colombo.whatsapptranscription.utils

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaCodec
import android.net.Uri
import android.util.Log
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class AudioProcessor {
    companion object {
        private const val TAG = "AudioProcessor"
        private const val TARGET_SAMPLE_RATE = 16000
        private const val TARGET_CHANNELS = 1
    }

    /**
     * Decodes audio from URI and converts it to 16kHz mono PCM format required by Vosk
     */
    fun processAudioFromUri(context: Context, uri: Uri): ByteArray? {
        return try {
            decodeAudioToPcm(context, uri)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing audio from URI", e)
            null
        }
    }

    private fun decodeAudioToPcm(context: Context, uri: Uri): ByteArray? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        
        try {
            // Set up MediaExtractor
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                extractor.setDataSource(pfd.fileDescriptor)
            } ?: run {
                Log.e(TAG, "Could not open file descriptor for URI")
                return null
            }

            // Find audio track
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex == -1 || audioFormat == null) {
                Log.e(TAG, "No audio track found in file")
                return null
            }

            extractor.selectTrack(audioTrackIndex)
            
            val mime = audioFormat.getString(MediaFormat.KEY_MIME) ?: ""
            Log.d(TAG, "Audio MIME type: $mime")
            
            // Get audio properties
            val originalSampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val originalChannels = audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            
            Log.d(TAG, "Original audio: ${originalSampleRate}Hz, $originalChannels channels")

            // Create decoder
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(audioFormat, null, null, 0)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            
            val pcmData = ByteArrayOutputStream()
            var isEOS = false

            while (!isEOS) {
                // Feed input to decoder
                val inputBufferIndex = codec.dequeueInputBuffer(10000)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                    inputBuffer?.clear()
                    
                    val sampleSize = if (inputBuffer != null) {
                        extractor.readSampleData(inputBuffer, 0)
                    } else -1
                    
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    } else {
                        val presentationTime = extractor.sampleTime
                        codec.queueInputBuffer(inputBufferIndex, 0, sampleSize, presentationTime, 0)
                        extractor.advance()
                    }
                }

                // Get output from decoder
                val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
                when {
                    outputBufferIndex >= 0 -> {
                        val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                        
                        if (bufferInfo.size > 0 && outputBuffer != null) {
                            // Convert decoded audio to target format
                            val audioBytes = ByteArray(bufferInfo.size)
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.get(audioBytes, 0, bufferInfo.size)
                            
                            // Convert to 16kHz mono PCM
                            val convertedBytes = convertAudioFormat(
                                audioBytes, 
                                originalSampleRate, 
                                originalChannels
                            )
                            
                            pcmData.write(convertedBytes)
                        }
                        
                        codec.releaseOutputBuffer(outputBufferIndex, false)
                        
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            isEOS = true
                        }
                    }
                    outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> {
                        // Handle buffer change (not needed for newer API levels)
                    }
                    outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFormat = codec.outputFormat
                        Log.d(TAG, "New output format: $newFormat")
                    }
                }
            }

            val result = pcmData.toByteArray()
            Log.d(TAG, "Converted to ${result.size} bytes of 16kHz mono PCM")
            return result

        } catch (e: Exception) {
            Log.e(TAG, "Error decoding audio", e)
            return null
        } finally {
            codec?.stop()
            codec?.release()
            extractor.release()
        }
    }

    private fun convertAudioFormat(
        audioData: ByteArray, 
        originalSampleRate: Int, 
        originalChannels: Int
    ): ByteArray {
        return try {
            // Simple conversion logic for demonstration
            // This assumes 16-bit PCM input and converts to target format
            
            val samplesPerChannel = audioData.size / 2 / originalChannels
            val resamplingRatio = TARGET_SAMPLE_RATE.toDouble() / originalSampleRate
            val targetSamples = (samplesPerChannel * resamplingRatio).toInt()
            
            val outputBuffer = ByteArrayOutputStream()
            
            for (i in 0 until targetSamples) {
                // Simple nearest-neighbor resampling
                val sourceIndex = (i / resamplingRatio).toInt()
                
                if (sourceIndex < samplesPerChannel) {
                    // Convert to mono by averaging channels (if stereo)
                    val sampleValue = if (originalChannels == 1) {
                        // Mono input
                        val byteIndex = sourceIndex * 2
                        if (byteIndex + 1 < audioData.size) {
                            // Convert bytes to 16-bit signed integer (little endian)
                            (audioData[byteIndex].toInt() and 0xFF) or 
                            ((audioData[byteIndex + 1].toInt() and 0xFF) shl 8)
                        } else 0
                    } else {
                        // Stereo input - average the channels
                        val leftIndex = sourceIndex * 4
                        val rightIndex = leftIndex + 2
                        
                        if (rightIndex + 1 < audioData.size) {
                            val leftSample = (audioData[leftIndex].toInt() and 0xFF) or 
                                           ((audioData[leftIndex + 1].toInt() and 0xFF) shl 8)
                            val rightSample = (audioData[rightIndex].toInt() and 0xFF) or 
                                            ((audioData[rightIndex + 1].toInt() and 0xFF) shl 8)
                            (leftSample + rightSample) / 2
                        } else 0
                    }
                    
                    // Convert back to bytes (little endian)
                    outputBuffer.write(sampleValue and 0xFF)
                    outputBuffer.write((sampleValue shr 8) and 0xFF)
                }
            }
            
            outputBuffer.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "Error converting audio format", e)
            audioData // Return original data as fallback
        }
    }
}