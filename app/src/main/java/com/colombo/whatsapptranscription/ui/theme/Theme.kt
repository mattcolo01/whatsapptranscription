package com.colombo.whatsapptranscription.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = WhatsAppGreen,
    secondary = WhatsAppTeal,
    tertiary = Pink80,
    background = DarkGray,
    surface = PipSurface,
    onPrimary = TextLight,
    onSecondary = TextLight,
    onBackground = TextLight,
    onSurface = TextLight
)

private val LightColorScheme = lightColorScheme(
    primary = WhatsAppGreenDark,
    secondary = WhatsAppTeal,
    tertiary = Pink40,
    background = SoftWhite,
    surface = Color.White,
    onPrimary = TextLight,
    onSecondary = TextLight,
    onBackground = TextDark,
    onSurface = TextDark
)

// Special color scheme optimized for PiP mode with high contrast
private val PipColorScheme = darkColorScheme(
    primary = PipPrimary,
    secondary = WhatsAppTeal,
    tertiary = Pink80,
    background = PipBackground,
    surface = PipSurface,
    onPrimary = TextLight,
    onSecondary = TextLight,
    onBackground = PipTextPrimary,
    onSurface = PipTextPrimary,
    outline = Color(0xFF3C3C3E)
)

@Composable
fun WhatsappTranscriptionTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    isPipMode: Boolean = false,
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        isPipMode -> PipColorScheme
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}