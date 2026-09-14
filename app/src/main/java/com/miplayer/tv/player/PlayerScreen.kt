package com.miplayer.tv.player

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView

/**
 * Reproductor a pantalla completa con ExoPlayer (Media3).
 *
 * Corrige dos fallos de la app analizada:
 *  1. Ella usaba ProgressiveMediaSource también para HLS. Aquí la fábrica detecta
 *     el tipo y usa HlsMediaSource cuando toca.
 *  2. Su reconexión reconstruía el reproductor entero cada segundo. Aquí se escucha
 *     el error real y se reintenta de forma controlada.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    url: String,
    startPositionMs: Long = 0L,
    onPosition: (Long) -> Unit = {},
    inPip: Boolean = false
) {
    val context = LocalContext.current
    var retries by remember(url) { mutableIntStateOf(0) }

    val player = remember(url) {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("Mora")
            .setAllowCrossProtocolRedirects(true)   // los servidores IPTV redirigen mucho
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(5_000, 30_000, 2_500, 5_000)
            .build()

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .setLoadControl(loadControl)
            .build().apply {
                setMediaItem(MediaItem.fromUri(url))
                if (startPositionMs > 0) seekTo(startPositionMs)
                prepare()
                playWhenReady = true
            }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (retries < MAX_RETRIES) { retries++; player.prepare() }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) retries = 0
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    DisposableEffect(Unit) {
        PlayerHolder.current = player
        val window = (context as? ComponentActivity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            onPosition(player.currentPosition)
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (PlayerHolder.current === player) PlayerHolder.current = null
            player.release()
        }
    }

    AndroidView(
        factory = { PlayerView(it).apply { this.player = player } },
        update = { it.useController = !inPip },
        modifier = Modifier.fillMaxSize()
    )
}

private const val MAX_RETRIES = 5
