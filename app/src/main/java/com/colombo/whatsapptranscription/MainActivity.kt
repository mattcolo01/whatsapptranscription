package com.colombo.whatsapptranscription

import android.app.PictureInPictureParams
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.util.Rational
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    private var isPipMode by mutableStateOf(false)
    private var showDisplayModeDialog by mutableStateOf(false)
    private var pendingTranscription: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize Speech2Text
        speech2Text = Speech2Text(this)
        
        setContent {
            WhatsappTranscriptionTheme(isPipMode = isPipMode) {
                TranscriptionScreen()
                
                // Display mode selection dialog
                if (showDisplayModeDialog && pendingTranscription != null) {
                    DisplayModeDialog(
                        onPipMode = {
                            showDisplayModeDialog = false
                            enterPictureInPictureMode(pendingTranscription!!)
                            pendingTranscription = null
                        },
                        onOverlayMode = {
                            showDisplayModeDialog = false
                            enterOverlayMode(pendingTranscription!!)
                            pendingTranscription = null
                        },
                        onDismiss = {
                            showDisplayModeDialog = false
                            pendingTranscription = null
                        }
                    )
                }
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

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isPipMode = isInPictureInPictureMode
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
                    // Show user choice between PiP and overlay
                    pendingTranscription = transcription
                    showDisplayModeDialog = true
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
            WhatsappTranscriptionTheme(isPipMode = isPipMode) {
                TranscriptionResultScreen(
                    transcription = transcription,
                    onCopyText = { copyToClipboard(transcription) },
                    onClose = { finishAndRemoveTask() }
                )
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Use a better aspect ratio for text display (taller for readability)
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(3, 4))  // Changed from 16:9 to 3:4 for better text readability
                .build()
            
            try {
                enterPictureInPictureMode(params)
                isPipMode = true
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to enter PiP mode", e)
                // PiP may not be supported or allowed, just show in normal mode
            }
        }
    }

    private fun enterOverlayMode(transcription: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                // Request permission
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, 
                    Uri.parse("package:$packageName"))
                startActivity(intent)
                Toast.makeText(this, "Please grant overlay permission and try again", Toast.LENGTH_LONG).show()
                return
            }
        }
        
        // Start overlay service
        val intent = Intent(this, OverlayService::class.java)
        intent.putExtra("transcription", transcription)
        startService(intent)
        
        // Minimize the app
        moveTaskToBack(true)
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Transcription", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Text copied to clipboard", Toast.LENGTH_SHORT).show()
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
            Icon(
                imageVector = Icons.Default.VolumeUp,
                contentDescription = "Audio transcription",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "WhatsApp Transcription",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Share an audio message from WhatsApp or any other app to transcribe it to text.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExpandLess,
                            contentDescription = "How to use",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "How to use:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    val steps = listOf(
                        "Share the audio message from WhatsApp or another app",
                        "Select 'WhatsApp Transcription' in the share menu",
                        "View the transcription in picture-in-picture mode"
                    )
                    
                    steps.forEachIndexed { index, step ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Surface(
                                modifier = Modifier.size(20.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${index + 1}",
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = step,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TranscriptionResultScreen(
    transcription: String,
    onCopyText: () -> Unit = {},
    onClose: () -> Unit = {}
) {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val isInPipMode = configuration.screenWidthDp < 200 || configuration.screenHeightDp < 200
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background gradient for visual appeal
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.background,
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
            )
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (isInPipMode) 8.dp else 16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header section
                if (!isInPipMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "Transcription",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Transcription",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        
                        IconButton(onClick = onClose) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                } else {
                    // Compact header for PiP mode
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Audio Transcription",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                // Main transcription content
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = if (isInPipMode) 2.dp else 8.dp
                    ),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(if (isInPipMode) 8.dp else 16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(if (isInPipMode) 12.dp else 20.dp)
                    ) {
                        Text(
                            text = transcription,
                            style = if (isInPipMode) {
                                MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                )
                            } else {
                                MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 16.sp,
                                    lineHeight = 24.sp
                                )
                            },
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = if (isInPipMode) TextAlign.Start else TextAlign.Start,
                            modifier = Modifier.weight(1f)
                        )
                        
                        Spacer(modifier = Modifier.height(if (isInPipMode) 8.dp else 16.dp))
                        
                        // Action buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isInPipMode) 
                                Arrangement.Center else Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isInPipMode) {
                                // Compact copy button for PiP
                                FilledTonalButton(
                                    onClick = onCopyText,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        contentColor = MaterialTheme.colorScheme.primary
                                    ),
                                    contentPadding = PaddingValues(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy text",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Copy",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            } else {
                                // Full-sized button for normal mode
                                Button(
                                    onClick = onCopyText,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy text",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Copy Text")
                                }
                            }
                        }
                    }
                }
                
                if (!isInPipMode) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Footer info
                    Text(
                        text = "Tap the copy button to save the transcription to your clipboard",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun DisplayModeDialog(
    onPipMode: () -> Unit,
    onOverlayMode: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Choose Display Mode",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "How would you like to view the transcription?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Option 1: Picture-in-Picture
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureInPicture,
                            contentDescription = "Picture in Picture",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Picture-in-Picture",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Small overlay window",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
                
                // Option 2: Floating Overlay
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = "Floating Overlay",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Floating Overlay",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Draggable popup window",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onOverlayMode) {
                    Text("Floating")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onPipMode) {
                    Text("Picture-in-Picture")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}