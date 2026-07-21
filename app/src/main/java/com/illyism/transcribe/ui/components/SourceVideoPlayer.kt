package com.illyism.transcribe.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
 * Falls back to [LocalThumbnail] when the Uri is missing or playback fails.
 */
@Composable
fun SourceVideoPlayer(
    sourceUri: String,
    thumbnailPath: String,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(16f / 9f),
    autoPlay: Boolean = false
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

    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
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
            player.release()
        }
    }

    LaunchedEffect(uri, autoPlay) {
        playbackFailed = false
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        player.playWhenReady = autoPlay
    }

    if (playbackFailed) {
        LocalThumbnail(
            path = thumbnailPath,
            modifier = modifier,
            cornerRadius = 0.dp,
            fallbackIcon = Icons.Outlined.Videocam
        )
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
