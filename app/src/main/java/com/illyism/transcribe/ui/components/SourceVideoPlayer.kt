package com.illyism.transcribe.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toDrawable
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * Plays a local SAF / content [sourceUri] with Media3 controls.
 * Falls back to [LocalThumbnail] with a re-link prompt when playback fails.
 */
@Composable
fun SourceVideoPlayer(
    sourceUri: String,
    thumbnailPath: String,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(16f / 9f),
    autoPlay: Boolean = false,
    externalPlayer: ExoPlayer? = null,
    onLocateSource: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val uri = remember(sourceUri) {
        sourceUri.takeIf { it.isNotBlank() }?.let { runCatching { Uri.parse(it) }.getOrNull() }
    }

    if (uri == null) {
        LocalThumbnail(
            path = thumbnailPath,
            modifier = modifier,
            cornerRadius = 0.dp,
            fallbackIcon = Icons.Outlined.Videocam
        )
        return
    }

    var playbackFailed by remember(uri) { mutableStateOf(false) }

    val player = remember(uri, externalPlayer) {
        externalPlayer ?: ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            playWhenReady = autoPlay
            prepare()
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                playbackFailed = true
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            if (externalPlayer == null) player.release()
        }
    }

    LaunchedEffect(uri, autoPlay) {
        playbackFailed = false
        if (externalPlayer == null) {
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
            player.playWhenReady = autoPlay
        }
    }

    if (playbackFailed) {
        Box(modifier = modifier) {
            LocalThumbnail(
                path = thumbnailPath,
                modifier = Modifier.matchParentSize(),
                cornerRadius = 0.dp,
                fallbackIcon = Icons.Outlined.Videocam
            )
            val errorColor = MaterialTheme.colorScheme.error
            val primaryColor = MaterialTheme.colorScheme.primary
            val labelStyle = MaterialTheme.typography.labelLarge
            Surface(
                color = Color.Black.copy(alpha = 0.72f),
                modifier = Modifier.matchParentSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = errorColor,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Source video unavailable",
                        style = labelStyle,
                        color = Color.White
                    )
                    if (onLocateSource != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = onLocateSource,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = primaryColor
                            )
                        ) {
                            Icon(
                                Icons.Outlined.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Locate video file")
                        }
                    }
                }
            }
        }
        return
    }

    val artwork = remember(thumbnailPath) {
        if (thumbnailPath.isBlank()) return@remember null
        runCatching {
            BitmapFactory.decodeFile(thumbnailPath)?.toDrawable(context.resources)
        }.getOrNull()
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
                controllerAutoShow = true
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                artwork?.let { defaultArtwork = it }
            }
        },
        update = { view ->
            if (view.player !== player) view.player = player
            artwork?.let { view.defaultArtwork = it }
        },
        modifier = modifier
    )
}
