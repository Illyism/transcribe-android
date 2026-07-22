package com.illyism.transcribe.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** Fallback brand amber when dynamic color is unavailable (API &lt; 31). */
val Amber = Color(0xFFE8A838)

@Immutable
data class JobStatusColors(
    val queued: Color,
    val running: Color,
    val needsAttention: Color,
    val failed: Color
)

/*
 * Status foregrounds are authored in OKLCH, then converted to sRGB for Compose.
 * Their lightness gap is intentionally large: every pair exceeds WCAG AAA (7:1)
 * against the app's corresponding surfaceContainerLow fallback. The least pair is
 * RUNNING light at 8.5:1; dynamic Material surfaces retain an AA-safe margin.
 */
private val LightJobStatusColors = JobStatusColors(
    queued = oklch(0.36, 0.03, 255.0),
    running = oklch(0.40, 0.14, 250.0),
    needsAttention = oklch(0.38, 0.10, 75.0),
    failed = oklch(0.40, 0.16, 25.0)
)

private val DarkJobStatusColors = JobStatusColors(
    queued = oklch(0.80, 0.03, 255.0),
    running = oklch(0.82, 0.11, 250.0),
    needsAttention = oklch(0.84, 0.12, 85.0),
    failed = oklch(0.82, 0.13, 25.0)
)

val LocalJobStatusColors = staticCompositionLocalOf { LightJobStatusColors }

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
        colorScheme = colorScheme
    ) {
        CompositionLocalProvider(
            LocalJobStatusColors provides if (darkTheme) {
                DarkJobStatusColors
            } else {
                LightJobStatusColors
            },
            content = content
        )
    }
}

/** CSS Color 4 OKLCH → linear OKLab → display sRGB. */
private fun oklch(lightness: Double, chroma: Double, hueDegrees: Double): Color {
    val hueRadians = Math.toRadians(hueDegrees)
    val a = chroma * cos(hueRadians)
    val b = chroma * sin(hueRadians)

    val lPrime = lightness + 0.3963377774 * a + 0.2158037573 * b
    val mPrime = lightness - 0.1055613458 * a - 0.0638541728 * b
    val sPrime = lightness - 0.0894841775 * a - 1.2914855480 * b
    val l = lPrime * lPrime * lPrime
    val m = mPrime * mPrime * mPrime
    val s = sPrime * sPrime * sPrime

    val linearRed = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s
    val linearGreen = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s
    val linearBlue = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s

    fun gammaEncode(channel: Double): Float {
        val encoded = if (channel <= 0.0031308) {
            12.92 * channel
        } else {
            1.055 * channel.pow(1.0 / 2.4) - 0.055
        }
        return encoded.coerceIn(0.0, 1.0).toFloat()
    }

    return Color(
        red = gammaEncode(linearRed),
        green = gammaEncode(linearGreen),
        blue = gammaEncode(linearBlue)
    )
}
