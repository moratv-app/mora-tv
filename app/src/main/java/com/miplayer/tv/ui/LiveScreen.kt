package com.miplayer.tv.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import coil.compose.AsyncImage
import com.miplayer.tv.player.VideoSurface

/**
 * Directo en móvil: vídeo arriba (16:9) y lista de canales debajo, en vertical.
 * El botón de pantalla completa agranda el vídeo y gira a horizontal.
 * En tele, el vídeo va a la izquierda y la lista a la derecha.
 */
@OptIn(UnstableApi::class)
@Composable
fun LiveScreen(state: UiState, vm: MainViewModel, inPip: Boolean = false) {
    val context = LocalContext.current
    val url = vm.currentLiveUrl()
    val fullscreen = state.playerFullscreen
    var retries by remember { mutableIntStateOf(0) }

    // Un único reproductor para toda la pantalla
    val player = remember {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("MiPlayer")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000).setReadTimeoutMs(15_000)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .setLoadControl(DefaultLoadControl.Builder()
                .setBufferDurationsMs(5_000, 30_000, 2_500, 5_000).build())
            .build().apply { playWhenReady = true }
    }

    // Cambiar de canal = cambiar la fuente, sin recrear el reproductor
    LaunchedEffect(url) {
        if (url != null) {
            retries = 0
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()
        }
    }

    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                if (retries < 5) { retries++; player.prepare() }
            }
            override fun onIsPlayingChanged(p: Boolean) { if (p) retries = 0 }
        }
        player.addListener(l)
        onDispose { player.removeListener(l); player.release() }
    }

    val compact = isCompact()

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (inPip) {
            // En ventana flotante: solo el vídeo, sin controles ni listas
            VideoSurface(player, Modifier.fillMaxSize(),
                showFullscreenButton = false)
        } else if (fullscreen) {
            VideoSurface(player, Modifier.fillMaxSize(),
                isFullscreen = true, onFullscreenToggle = { vm.setFullscreen(false) })
        } else if (compact) {
            // MÓVIL: vídeo arriba, lista debajo, en vertical
            Column(Modifier.fillMaxSize().background(Bg)) {
                VideoSurface(
                    player,
                    Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black),
                    onFullscreenToggle = { vm.setFullscreen(true) }
                )
                ChannelList(state, vm, Modifier.weight(1f).fillMaxWidth())
            }
        } else {
            // TELE / TABLET: vídeo a la izquierda, lista a la derecha
            Row(Modifier.fillMaxSize().background(Bg)) {
                VideoSurface(
                    player,
                    Modifier.weight(1.6f).fillMaxHeight().background(Color.Black),
                    onFullscreenToggle = { vm.setFullscreen(true) }
                )
                ChannelList(state, vm, Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun ChannelList(state: UiState, vm: MainViewModel, modifier: Modifier) {
    LazyColumn(modifier.padding(horizontal = 12.dp)) {
        itemsIndexed(state.livePlaylist) { i, ch ->
            val playing = i == state.liveIndex
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (playing) Accent.copy(alpha = 0.18f) else Card)
                    .clickable { vm.selectLiveIndex(i) }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(ch.icon, null, modifier = Modifier.size(36.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    ch.name ?: "Canal ${ch.num}",
                    color = if (playing) Accent else TextMain,
                    fontWeight = if (playing) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 15.sp, maxLines = 1
                )
            }
        }
    }
}
