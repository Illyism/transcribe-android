package com.illyism.transcribe.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.io.File

/** Loads a local JPEG thumbnail path, or shows [fallbackIcon]. */
@Composable
fun LocalThumbnail(
    path: String,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    fallbackIcon: ImageVector = Icons.Outlined.Videocam,
    contentScale: ContentScale = ContentScale.Crop
) {
    val scheme = MaterialTheme.colorScheme
    val bitmap = remember(path) {
        if (path.isBlank()) return@remember null
        val file = File(path)
        if (!file.exists()) return@remember null
        runCatching { BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() }.getOrNull()
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(scheme.primary.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale
            )
        } else {
            Icon(
                fallbackIcon,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
