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
        val final = recognizer?.acceptWaveForm(audioData, audioData.size)
        return if (final == true) {
            onFinalResultAvailable()
        } else {
            onPartialResultAvailable()
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