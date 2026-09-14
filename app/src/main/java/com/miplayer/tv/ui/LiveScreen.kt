package com.miplayer.tv.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
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
import kotlinx.coroutines.delay

/**
 * Directo: vídeo arriba + lista de canales debajo (móvil), o lado a lado (tele).
 * Incluye recarga manual y AUTOMÁTICA del canal: si se queda atascado unos
 * segundos (lo que suele provocar la desincronización de audio en IPTV),
 * la app vuelve a cargar el flujo sola y el audio se resincroniza.
 */
@OptIn(UnstableApi::class)
@Composable
fun LiveScreen(state: UiState, vm: MainViewModel, inPip: Boolean = false) {
    val context = LocalContext.current
    val url = vm.currentLiveUrl()
    val fullscreen = state.playerFullscreen
    var retries by remember { mutableIntStateOf(0) }
    // Momento en que empezó a bufferear (0 = va fluido)
    var bufferingSince by remember { mutableLongStateOf(0L) }

    val player = remember {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("Mora")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000).setReadTimeoutMs(15_000)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .setLoadControl(DefaultLoadControl.Builder()
                .setBufferDurationsMs(4_000, 30_000, 2_000, 4_000).build())
            .build().apply { playWhenReady = true }
    }

    // Recarga el canal actual: vuelve al directo en vivo y resincroniza el audio
    fun reload() {
        val u = vm.currentLiveUrl() ?: return
        bufferingSince = 0L
        retries = 0
        player.setMediaItem(MediaItem.fromUri(u))
        player.prepare()
        player.seekToDefaultPosition()   // salta al borde en vivo
        player.play()
    }

    LaunchedEffect(url) {
        if (url != null) {
            retries = 0; bufferingSince = 0L
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()
        }
    }

    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING ->
                        if (bufferingSince == 0L) bufferingSince = System.currentTimeMillis()
                    Player.STATE_READY -> bufferingSince = 0L
                }
            }
            override fun onPlayerError(e: PlaybackException) {
                if (retries < 5) { retries++; player.prepare() }
            }
            override fun onIsPlayingChanged(p: Boolean) { if (p) { retries = 0; bufferingSince = 0L } }
        }
        player.addListener(l)
        onDispose { player.removeListener(l); player.release() }
    }

    // Vigilante: si lleva más de 8 s atascado, recarga solo
    LaunchedEffect(player) {
        while (true) {
            delay(1000)
            val since = bufferingSince
            if (since != 0L && System.currentTimeMillis() - since > 8_000) {
                reload()
            }
        }
    }

    val compact = isCompact()

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            inPip ->
                VideoSurface(player, Modifier.fillMaxSize(), showFullscreenButton = false)

            fullscreen ->
                VideoWithReload(player, Modifier.fillMaxSize(), isFullscreen = true,
                    onFullscreen = { vm.setFullscreen(false) }, onReload = { reload() })

            compact -> Column(Modifier.fillMaxSize().background(Bg)) {
                VideoWithReload(
                    player,
                    Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black),
                    onFullscreen = { vm.setFullscreen(true) }, onReload = { reload() }
                )
                ChannelList(state, vm, Modifier.weight(1f).fillMaxWidth())
            }

            else -> Row(Modifier.fillMaxSize().background(Bg)) {
                VideoWithReload(
                    player, Modifier.weight(1.6f).fillMaxHeight().background(Color.Black),
                    onFullscreen = { vm.setFullscreen(true) }, onReload = { reload() }
                )
                ChannelList(state, vm, Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

/** Vídeo con un botón de recarga siempre accesible en la esquina. */
@Composable
private fun VideoWithReload(
    player: ExoPlayer,
    modifier: Modifier,
    isFullscreen: Boolean = false,
    onFullscreen: () -> Unit,
    onReload: () -> Unit
) {
    Box(modifier) {
        VideoSurface(player, Modifier.fillMaxSize(),
            isFullscreen = isFullscreen, onFullscreenToggle = onFullscreen)
        // Botón de recargar (resincroniza el audio de un toque)
        Box(
            Modifier.align(Alignment.TopEnd).padding(10.dp)
                .clip(CircleShape).background(Color(0x88000000))
                .clickable { onReload() }.padding(8.dp)
        ) {
            Icon(Icons.Filled.Refresh, "Recargar canal", tint = Color.White,
                modifier = Modifier.size(22.dp))
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
                    .background(if (playing) Accent.copy(alpha = 0.20f) else Card)
                    .clickable { vm.selectLiveIndex(i) }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (ch.num > 0) "${ch.num}" else "", color = TextSub,
                    fontSize = 12.sp, modifier = Modifier.width(34.dp))
                AsyncImage(ch.icon, null, modifier = Modifier.size(34.dp))
                Spacer(Modifier.width(12.dp))
                Text(ch.name ?: "Canal ${ch.num}",
                    color = if (playing) Accent else TextMain,
                    fontWeight = if (playing) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 15.sp, maxLines = 1)
            }
        }
    }
}
