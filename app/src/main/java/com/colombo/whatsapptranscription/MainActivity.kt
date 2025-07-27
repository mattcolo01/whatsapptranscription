package com.colombo.whatsapptranscription

import android.app.PictureInPictureParams
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Rational
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.colombo.whatsapptranscription.ui.theme.WhatsappTranscriptionTheme
import com.colombo.whatsapptranscription.utils.AudioProcessor
import com.colombo.whatsapptranscription.utils.Speech2Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var speech2Text: Speech2Text? = null
    private val audioProcessor = AudioProcessor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize Speech2Text
        speech2Text = Speech2Text(this)
        
        setContent {
            WhatsappTranscriptionTheme {
                TranscriptionScreen()
            }
        }
        
        // Handle intent when app is opened
        processIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        processIntent(intent)
    }

    private fun processIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SEND -> {
                if (intent.type?.startsWith("audio/") == true) {
                    val audioUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
                    }
                    
                    audioUri?.let { uri ->
                        processSharedAudio(uri)
                    }
                }
            }
        }
    }

    private fun processSharedAudio(uri: Uri) {
        lifecycleScope.launch {
            try {
                val transcription = withContext(Dispatchers.IO) {
                    transcribeAudio(uri)
                }
                
                if (transcription.isNotEmpty()) {
                    enterPictureInPictureMode(transcription)
                } else {
                    Toast.makeText(this@MainActivity, "Could not transcribe audio", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error processing audio", e)
                Toast.makeText(this@MainActivity, "Error processing audio: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun transcribeAudio(uri: Uri): String {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("MainActivity", "Processing audio from URI: $uri")

                // Process the audio file
                val audioData = audioProcessor.processAudioFromUri(this@MainActivity, uri)
                
                if (audioData != null && audioData.isNotEmpty()) {
                    Log.d("MainActivity", "Audio data size: ${audioData.size} bytes")
                    
                    // Feed audio data to Speech2Text
                    // Note: This is a simplified approach. In production, you'd need proper
                    // audio format conversion to 16kHz PCM before feeding to Speech2Text
                    val result = speech2Text?.filterAudioData(audioData)
                    
                    result?.takeIf { it.isNotBlank() } 
                        ?: "Audio processed but no speech detected. The Vosk model expects 16kHz PCM audio."
                } else {
                    "Error: Could not read audio file"
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Transcription error", e)
                "Error during transcription: ${e.message}"
            }
        }
    }

    private fun enterPictureInPictureMode(transcription: String) {
        // Update UI to show transcription
        setContent {
            WhatsappTranscriptionTheme {
                TranscriptionResultScreen(transcription = transcription)
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            
            try {
                enterPictureInPictureMode(params)
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to enter PiP mode", e)
                // PiP may not be supported or allowed, just show in normal mode
            }
        }
    }
}

@Composable
fun TranscriptionScreen() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "WhatsApp Transcription",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Share an audio message from WhatsApp or any other app to transcribe it to text.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "How to use:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("1. Share the audio message from WhatsApp or another app")
                    Text("2. Select 'WhatsApp Transcription' in the share menu")
                    Text("3. View the transcription in picture-in-picture mode")
                }
            }
        }
    }
}

@Composable
fun TranscriptionResultScreen(transcription: String) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Transcription",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Text(
                    text = transcription,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}