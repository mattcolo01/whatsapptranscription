package com.colombo.whatsapptranscription.utils

import android.util.Log
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import java.io.IOException

class Speech2Text (context: android.content.Context) {
    private val tag = this.javaClass.name
    private var recognizer: Recognizer? = null

    init {
        StorageService.unpack(
            context, "models/vosk-model-small-it-0.22", "model",
            { model: Model ->
                recognizer = Recognizer(model, 16_000.0f).apply {
                    setWords(true)
                }
            },
            { exception: IOException -> Log.e("SpeechRecognitionService", "Failed to unpack model", exception) }
        )
    }

    fun filterAudioData(audioData: ByteArray): String? {
        return try {
            Log.d(tag, "Processing ${audioData.size} bytes of PCM audio data")
            
            if (recognizer == null) {
                Log.e(tag, "Recognizer not initialized")
                return "Error: Speech recognizer not ready"
            }
            
            // Process audio data with Vosk recognizer
            val wasAccepted = recognizer?.acceptWaveForm(audioData, audioData.size) ?: false
            Log.d(tag, "Audio data accepted by recognizer: $wasAccepted")
            
            // Always try to get final result after processing all data
            recognizer?.finalResult()?.let { resultJson ->
                try {
                    val result = org.json.JSONObject(resultJson).optString("text", "")
                    Log.d(tag, "Final transcription result: '$result'")
                    return if (result.isNotBlank()) result else null
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