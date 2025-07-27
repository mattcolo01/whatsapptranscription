package com.colombo.whatsapptranscription.utils

import android.util.Log
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import java.io.IOException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class Speech2Text (context: android.content.Context) {
    private val tag = this.javaClass.name
    private var recognizer: Recognizer? = null
    private var isInitialized = false
    private var initializationError: String? = null

    init {
        StorageService.unpack(
            context, "models/vosk-model-small-it-0.22", "model",
            { model: Model ->
                try {
                    recognizer = Recognizer(model, 16_000.0f).apply {
                        setWords(true)
                    }
                    isInitialized = true
                    Log.d(tag, "Speech recognizer initialized successfully")
                } catch (e: Exception) {
                    Log.e(tag, "Failed to create recognizer", e)
                    initializationError = "Failed to create recognizer: ${e.message}"
                }
            },
            { exception: IOException -> 
                Log.e(tag, "Failed to unpack model", exception)
                initializationError = "Failed to unpack model: ${exception.message}"
            }
        )
    }

    /**
     * Suspends until the speech recognizer is ready to use
     */
    suspend fun waitForInitialization(): Boolean {
        return suspendCancellableCoroutine { continuation ->
            // Check if already initialized
            if (isInitialized && recognizer != null) {
                continuation.resume(true)
                return@suspendCancellableCoroutine
            }
            
            // Check if initialization failed
            if (initializationError != null) {
                Log.e(tag, "Initialization failed: $initializationError")
                continuation.resume(false)
                return@suspendCancellableCoroutine
            }
            
            // Poll for initialization (simple approach)
            val checkInterval = 100L // 100ms
            val maxWaitTime = 10000L // 10 seconds
            var elapsedTime = 0L
            
            fun checkInitialization() {
                if (isInitialized && recognizer != null) {
                    continuation.resume(true)
                } else if (initializationError != null) {
                    continuation.resume(false)
                } else if (elapsedTime >= maxWaitTime) {
                    Log.e(tag, "Initialization timeout after ${maxWaitTime}ms")
                    continuation.resume(false)
                } else {
                    elapsedTime += checkInterval
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        checkInitialization()
                    }, checkInterval)
                }
            }
            
            checkInitialization()
        }
    }

    fun isReady(): Boolean {
        return isInitialized && recognizer != null
    }

    fun getInitializationError(): String? {
        return initializationError
    }

    fun filterAudioData(audioData: ByteArray): String? {
        return try {
            Log.d(tag, "Processing ${audioData.size} bytes of PCM audio data")
            
            if (!isReady()) {
                Log.e(tag, "Recognizer not ready: ${initializationError ?: "Still initializing"}")
                return initializationError?.let { "Error: $it" } ?: "Error: Speech recognizer not ready"
            }
            
            // Process audio data with Vosk recognizer
            val wasAccepted = recognizer?.acceptWaveForm(audioData, audioData.size) ?: false
            Log.d(tag, "Audio data accepted by recognizer: $wasAccepted")
            
            // Always try to get final result after processing all data
            recognizer?.finalResult?.let { resultJson ->
                try {
                    val result = org.json.JSONObject(resultJson).optString("text", "")
                    Log.d(tag, "Final transcription result: '$result'")
                    return result.ifBlank { null }
                } catch (e: Exception) {
                    Log.e(tag, "Failed to parse final result JSON", e)
                }
            }
            
            // If no final result, try partial result
            recognizer?.partialResult?.let { resultJson ->
                try {
                    val result = org.json.JSONObject(resultJson).optString("text", "")
                    Log.d(tag, "Partial transcription result: '$result'")
                    return if (result.isNotBlank()) result else null
                } catch (e: Exception) {
                    Log.e(tag, "Failed to parse partial result JSON", e)
                }
            }
            
            Log.w(tag, "No transcription result available")
            null
        } catch (e: Exception) {
            Log.e(tag, "Error processing audio data", e)
            "Error during speech recognition: ${e.message}"
        }
    }

    private fun onPartialResultAvailable(): String? {
        val partialResultJson = recognizer?.partialResult
        return partialResultJson?.let {
            try {
                org.json.JSONObject(it).optString("text")
            } catch (e: Exception) {
                Log.e(tag, "Failed to parse result JSON", e)
                null
            }
        }
    }

    private fun onFinalResultAvailable(): String? {
        val textResultJson = recognizer?.finalResult
        return textResultJson?.let {
            try {
                org.json.JSONObject(it).optString("text")
            } catch (e: Exception) {
                Log.e(tag, "Failed to parse result JSON", e)
                null
            }
        }
    }
}