package com.illyism.transcribe.ui.theme

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

/** Fallback brand amber when dynamic color is unavailable (API &lt; 31). */
val Amber = Color(0xFFE8A838)

private val DarkFallback = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF1A1200),
    secondary = Color(0xFFB87A1A),
    background = Color(0xFF121212),
    surface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFF222222),
    onBackground = Color(0xFFF2F2F2),
    onSurface = Color(0xFFF2F2F2),
    onSurfaceVariant = Color(0xFFB0B0B0),
    error = Color(0xFFFF8A80),
    outline = Color(0xFF3A3A3A)
)

private val LightFallback = lightColorScheme(
    primary = Color(0xFF8B5A00),
    onPrimary = Color.White,
    secondary = Color(0xFFB87A1A),
    background = Color(0xFFFFF8F0),
    surface = Color(0xFFFFFBF5),
    surfaceVariant = Color(0xFFF0E6D8),
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    onSurfaceVariant = Color(0xFF49454F),
    error = Color(0xFFB3261E),
    outline = Color(0xFF79747E)
)

@Composable
fun TranscribeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkFallback
        else -> LightFallback
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
