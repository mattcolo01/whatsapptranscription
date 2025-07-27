package com.colombo.whatsapptranscription

import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.colombo.whatsapptranscription.ui.theme.WhatsappTranscriptionTheme

class OverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var transcription: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        android.util.Log.d("OverlayService", "onStartCommand called")
        
        transcription = intent?.getStringExtra("transcription") ?: "No transcription available"
        
        android.util.Log.d("OverlayService", "Received transcription (${transcription.length} chars): ${transcription.take(50)}...")
        
        if (overlayView == null) {
            android.util.Log.d("OverlayService", "Creating new overlay")
            createOverlay()
        } else {
            android.util.Log.d("OverlayService", "Overlay view already exists, updating transcription")
            // Update existing overlay with new transcription
            updateOverlayContent()
        }
        
        return START_NOT_STICKY
    }
    
    private fun updateOverlayContent() {
        (overlayView as? ComposeView)?.setContent {
            WhatsappTranscriptionTheme(isPipMode = true) {
                // Wrap in a Surface to ensure proper Material theme background
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background.copy(alpha = 0f)
                ) {
                    FloatingTranscriptionCard(
                        transcription = transcription,
                        onCopy = { copyToClipboard(transcription) },
                        onClose = { stopOverlay() }
                    )
                }
            }
        }
    }

    private fun createOverlay() {
        android.util.Log.d("OverlayService", "createOverlay called")
        
        // Double-check permission before creating overlay
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!android.provider.Settings.canDrawOverlays(this)) {
                android.util.Log.e("OverlayService", "Overlay permission not granted")
                stopSelf()
                return
            } else {
                android.util.Log.d("OverlayService", "Overlay permission confirmed")
            }
        }
        
        android.util.Log.d("OverlayService", "Creating overlay window")
        
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        
        android.util.Log.d("OverlayService", "Using layout flag: $layoutFlag")

        // Convert dp to pixels for proper sizing
        val displayMetrics = resources.displayMetrics
        val widthDp = 320
        val heightDp = 200
        val widthPx = (widthDp * displayMetrics.density).toInt()
        val heightPx = (heightDp * displayMetrics.density).toInt()
        
        android.util.Log.d("OverlayService", "Overlay size: ${widthPx}x${heightPx} px (${widthDp}x${heightDp} dp)")
        android.util.Log.d("OverlayService", "Screen size: ${displayMetrics.widthPixels}x${displayMetrics.heightPixels} px")

        val params = WindowManager.LayoutParams(
            widthPx,
            heightPx,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (displayMetrics.widthPixels * 0.1).toInt()
            y = (displayMetrics.heightPixels * 0.2).toInt()
        }

        // Create Compose view for the overlay
        overlayView = ComposeView(this).apply {
            // Set explicit background to ensure proper rendering
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            
            setContent {
                WhatsappTranscriptionTheme(isPipMode = true) {
                    // Wrap in a Surface to ensure proper Material theme background
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background.copy(alpha = 0f)
                    ) {
                        FloatingTranscriptionCard(
                            transcription = transcription,
                            onCopy = { copyToClipboard(transcription) },
                            onClose = { stopOverlay() }
                        )
                    }
                }
            }
        }

        // Add touch handling for drag functionality
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        overlayView?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    
                    // Only start dragging if movement is significant (to allow button clicks)
                    if (!isDragging && (kotlin.math.abs(deltaX) > 10 || kotlin.math.abs(deltaY) > 10)) {
                        isDragging = true
                    }
                    
                    if (isDragging) {
                        params.x = initialX + deltaX
                        params.y = initialY + deltaY
                        
                        // Keep within screen bounds
                        params.x = params.x.coerceAtLeast(0)
                        params.y = params.y.coerceAtLeast(0)
                        params.x = (params.x).coerceAtMost(displayMetrics.widthPixels - widthPx)
                        params.y = (params.y).coerceAtMost(displayMetrics.heightPixels - heightPx)
                        
                        windowManager?.updateViewLayout(overlayView, params)
                    }
                    isDragging
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        isDragging = false
                        true
                    } else {
                        // Allow click events to propagate to child views
                        false
                    }
                }
                else -> false
            }
        }

        try {
            android.util.Log.d("OverlayService", "Adding overlay view to window manager")
            windowManager?.addView(overlayView, params)
            android.util.Log.d("OverlayService", "Overlay view added successfully")
        } catch (e: SecurityException) {
            android.util.Log.e("OverlayService", "SecurityException - overlay permission may have been revoked", e)
            stopSelf()
        } catch (e: IllegalStateException) {
            android.util.Log.e("OverlayService", "IllegalStateException - invalid window state", e)
            stopSelf()
        } catch (e: Exception) {
            // Handle permission not granted or other errors
            android.util.Log.e("OverlayService", "Failed to add overlay view", e)
            stopSelf()
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Transcription", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Text copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun stopOverlay() {
        if (overlayView != null) {
            windowManager?.removeView(overlayView)
            overlayView = null
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopOverlay()
    }
}

@Composable
fun FloatingTranscriptionCard(
    transcription: String,
    onCopy: () -> Unit,
    onClose: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 140.dp, max = 300.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header with drag handle visual cue
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Audio Transcription",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Drag to move • Tap to interact",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Transcription text in scrollable container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(min = 60.dp, max = 180.dp)
            ) {
                Text(
                    text = transcription,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                )
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            
            // Action button
            Button(
                onClick = onCopy,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                contentPadding = PaddingValues(vertical = 8.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy text",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Copy Text",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}