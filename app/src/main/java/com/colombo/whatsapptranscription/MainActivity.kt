package com.colombo.whatsapptranscription

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.colombo.whatsapptranscription.ui.theme.WhatsappTranscriptionTheme
import com.colombo.whatsapptranscription.utils.Speech2Text

class MainActivity : ComponentActivity() {
    private var speech2Text: Speech2Text? = null
    private lateinit var transcriptionResult: MutableState<String>
    private lateinit var isProcessing: MutableState<Boolean>
    private lateinit var isInPictureInPictureMode: MutableState<Boolean>
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            transcriptionResult = remember { mutableStateOf("") }
            isProcessing = remember { mutableStateOf(false) }
            isInPictureInPictureMode = remember { mutableStateOf(false) }
            
            // Initialize Speech2Text after compose states are ready
            LaunchedEffect(Unit) {
                initializeSpeech2Text()
                // Handle incoming intent (audio sharing)
                handleIncomingIntent(intent)
            }
            
            WhatsappTranscriptionTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    TranscriptionScreen(
                        transcriptionResult = transcriptionResult.value,
                        isProcessing = isProcessing.value,
                        isInPictureInPictureMode = isInPictureInPictureMode.value,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
    
    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        this.isInPictureInPictureMode.value = isInPictureInPictureMode
    }
    
    private fun enterPictureInPictureMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }
    
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIncomingIntent(it) }
    }
    
    private fun initializeSpeech2Text() {
        speech2Text = Speech2Text(
            context = this,
            onLMAnalysisNeeded = { text, audioData ->
                // For now, just use the recognized text
                transcriptionResult.value = text
                isProcessing.value = false
                Log.d("MainActivity", "Transcription completed: $text")
                
                // Enter picture-in-picture mode when transcription is ready
                if (text.isNotBlank()) {
                    enterPictureInPictureMode()
                }
            },
            onSoccerWordDetected = { audioData, text ->
                // Handle detected keywords or final result
                if (text.isNotEmpty()) {
                    transcriptionResult.value = text
                    isProcessing.value = false
                    Log.d("MainActivity", "Transcription with keywords: $text")
                    
                    // Enter picture-in-picture mode when transcription is ready
                    enterPictureInPictureMode()
                }
            }
        )
    }
    
    private fun handleIncomingIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SEND -> {
                if (intent.type?.startsWith("audio/") == true) {
                    intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { audioUri ->
                        processAudioFile(audioUri)
                    }
                }
            }
        }
    }
    
    private fun processAudioFile(audioUri: Uri) {
        try {
            isProcessing.value = true
            transcriptionResult.value = ""
            
            contentResolver.openInputStream(audioUri)?.use { inputStream ->
                val audioData = inputStream.readBytes()
                
                // Process complete audio file with Speech2Text
                speech2Text?.processCompleteAudio(audioData)
                
                Toast.makeText(this, "Processing audio...", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error processing audio file", e)
            Toast.makeText(this, "Error processing audio: ${e.message}", Toast.LENGTH_LONG).show()
            isProcessing.value = false
        }
    }
}

@Composable
fun TranscriptionScreen(
    transcriptionResult: String,
    isProcessing: Boolean,
    isInPictureInPictureMode: Boolean,
    modifier: Modifier = Modifier
) {
    if (isInPictureInPictureMode) {
        // Compact layout for picture-in-picture mode
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isProcessing) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    Text(
                        text = "Processing...",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else if (transcriptionResult.isNotEmpty()) {
                Text(
                    text = transcriptionResult,
                    fontSize = 12.sp,
                    maxLines = 3,
                    lineHeight = 16.sp
                )
            } else {
                Text(
                    text = "Ready for transcription",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        // Full layout for normal mode
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "WhatsApp Transcription",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 16.dp)
            )
            
            if (isProcessing) {
                CircularProgressIndicator()
                Text(
                    text = "Processing audio...",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            if (transcriptionResult.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Transcription:",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            text = transcriptionResult,
                            fontSize = 16.sp,
                            lineHeight = 24.sp
                        )
                    }
                }
            } else if (!isProcessing) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "How to use:",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            text = "1. Share an audio message from WhatsApp\n" +
                                  "2. Select 'Whatsapp Transcription' from the share menu\n" +
                                  "3. Wait for the transcription to appear in picture-in-picture mode",
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }
    }
}