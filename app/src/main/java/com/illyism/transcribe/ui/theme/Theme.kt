package com.illyism.transcribe.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Amber = Color(0xFFE8A838)
val AmberDim = Color(0xFFB87A1A)
val Bg = Color(0xFF121212)
val Surface = Color(0xFF1A1A1A)
val SurfaceAlt = Color(0xFF222222)
val TextPrimary = Color(0xFFF2F2F2)
val TextSecondary = Color(0xFFB0B0B0)
val Danger = Color(0xFFFF8A80)
val Success = Color(0xFF81C784)

private val colors = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF1A1200),
    secondary = AmberDim,
    background = Bg,
    surface = Surface,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    error = Danger,
    outline = Color(0xFF3A3A3A)
)

@Composable
fun TranscribeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = colors,
        typography = MaterialTheme.typography.copy(
            displaySmall = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 34.sp,
                letterSpacing = (-0.5).sp,
                color = TextPrimary
            ),
            headlineMedium = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                color = TextPrimary
            ),
            titleMedium = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                color = TextPrimary
            ),
            bodyLarge = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                color = TextPrimary,
                lineHeight = 22.sp
            ),
            bodyMedium = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                color = TextSecondary,
                lineHeight = 20.sp
            ),
            labelLarge = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
        ),
        content = content
    )
}
