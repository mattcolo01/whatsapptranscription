package com.colombo.whatsapptranscription.utils

import android.util.Log
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import java.io.IOException

class Speech2Text (
    context: android.content.Context,
    private val onLMAnalysisNeeded: (String, ByteArray) -> Unit,
    private val onSoccerWordDetected: (ByteArray, String) -> Unit
) {
    private val tag = "Speech2Text"
    private var recognizer: Recognizer? = null
    private val sentenceAnalysisStatus = SentenceAnalysisStatus()

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

    fun filterAudioData(
        audioData: ByteArray,
        forceSend: Boolean = false
    ) {
        if (forceSend) {
            sentenceAnalysisStatus.isApprovedYet = true
        }

        val final = recognizer?.acceptWaveForm(audioData, audioData.size)
        if (final == true) {
            onFinalResultAvailable(audioData)
        } else {
            onPartialResultAvailable(audioData)
        }
    }

    private fun onPartialResultAvailable(audioData: ByteArray) {
        val partialResultJson = recognizer?.partialResult
        val partialResult = partialResultJson?.let {
            try {
                org.json.JSONObject(it).optString("text")
            } catch (e: Exception) {
                Log.e(tag, "Failed to parse result JSON", e)
                null
            }
        }

        if (!sentenceAnalysisStatus.text.isEmpty() || (partialResult != null && !partialResult.isBlank())) {
            sentenceAnalysisStatus.text += " $partialResult"
            sentenceAnalysisStatus.audioData += audioData
            Log.d(tag, "Partial result: $partialResult")

            if (sentenceAnalysisStatus.isApprovedYet) {
                onSoccerWordDetected(audioData, "")
            } else if (soccerWords.any { word -> partialResult!!.contains(word, ignoreCase = true) }) {
                Log.d(tag, "Sending audio")
                onSoccerWordDetected(sentenceAnalysisStatus.audioData, "")
                sentenceAnalysisStatus.isApprovedYet = true
            }
        }
    }

    private fun onFinalResultAvailable(audioData: ByteArray) {
        try {
            val textResultJson = recognizer?.finalResult
            val textResult = textResultJson?.let {
                try {
                    org.json.JSONObject(it).optString("text")
                } catch (e: Exception) {
                    Log.e(tag, "Failed to parse result JSON", e)
                    null
                }
            }

            if (textResult == null || textResult.isBlank()) {
                return
            }
            Log.d(tag, "Recognized text: $textResult")

            if (!sentenceAnalysisStatus.isApprovedYet && soccerWords.any { word -> textResult.contains(word, ignoreCase = true) }) {
                Log.d(tag, "Sending audio")
                sentenceAnalysisStatus.isApprovedYet = true
            }

            if (sentenceAnalysisStatus.isApprovedYet) {
                onSoccerWordDetected(audioData, textResult)
            } else {
                Log.d(tag, "LM analysis needed for: $textResult")
                onLMAnalysisNeeded(textResult, sentenceAnalysisStatus.audioData)
            }
        } finally {
            sentenceAnalysisStatus.reset()
        }
    }

    private class SentenceAnalysisStatus() {
        var text: String
        var audioData: ByteArray
        var isApprovedYet: Boolean

        init {
            text = ""
            audioData = ByteArray(0)
            isApprovedYet = false
        }

        fun reset() {
            isApprovedYet = false
            text = ""
            audioData = ByteArray(0)
        }
    }
}