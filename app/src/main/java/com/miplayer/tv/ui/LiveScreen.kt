package com.miplayer.tv.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    var autoReloads by remember { mutableIntStateOf(0) }

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
            retries = 0; bufferingSince = 0L; autoReloads = 0
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
            override fun onIsPlayingChanged(p: Boolean) { if (p) { retries = 0; bufferingSince = 0L; autoReloads = 0 } }
        }
        com.miplayer.tv.player.PlayerHolder.current = player
        player.addListener(l)
        onDispose {
            player.removeListener(l)
            if (com.miplayer.tv.player.PlayerHolder.current === player)
                com.miplayer.tv.player.PlayerHolder.current = null
            player.release()
        }
    }

    // Vigilante: si lleva más de 8 s atascado, recarga solo
    LaunchedEffect(player) {
        while (true) {
            delay(1000)
            val since = bufferingSince
            // Recarga sola solo si lleva >8 s atascado y sin superar 3 intentos,
            // para no entrar en bucle con un canal simplemente lento.
            if (since != 0L && System.currentTimeMillis() - since > 8_000 && autoReloads < 3) {
                autoReloads++
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

            compact -> Column(Modifier.fillMaxSize().background(NebulaGradientSoft)) {
                VideoWithReload(
                    player,
                    Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black),
                    onFullscreen = { vm.setFullscreen(true) }, onReload = { reload() }
                )
                LiveCategories(state, vm, Modifier.fillMaxWidth(), horizontal = true)
                ChannelList(state, vm, Modifier.weight(1f).fillMaxWidth())
            }

            else -> Row(Modifier.fillMaxSize().background(NebulaGradientSoft)) {
                LiveCategories(state, vm, Modifier.weight(0.9f).fillMaxHeight(), horizontal = false)
                ChannelList(state, vm, Modifier.weight(1f).fillMaxHeight())
                VideoWithReload(
                    player, Modifier.weight(1.8f).fillMaxHeight().background(Color.Black),
                    onFullscreen = { vm.setFullscreen(true) }, onReload = { reload() }
                )
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
            val now = vm.nowPlaying(ch.epgId)
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
                Column(Modifier.weight(1f)) {
                    Text(ch.name ?: "Canal ${ch.num}",
                        color = if (playing) Accent else TextMain,
                        fontWeight = if (playing) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 15.sp, maxLines = 1)
                    if (!now?.title.isNullOrBlank()) {
                        Text("Ahora: ${now!!.title}", color = TextSub, fontSize = 11.sp, maxLines = 1)
                        val frac = progressOf(now.startMs, now.stopMs)
                        if (frac != null) androidx.compose.material3.LinearProgressIndicator(
                            progress = { frac },
                            color = Accent, trackColor = Card,
                            modifier = Modifier.fillMaxWidth().height(3.dp).padding(top = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Fracción 0..1 de lo transcurrido del programa actual, o null si no aplica. */
fun progressOf(startMs: Long, stopMs: Long): Float? {
    if (startMs <= 0 || stopMs <= startMs) return null
    val now = System.currentTimeMillis()
    if (now < startMs || now > stopMs) return null
    return ((now - startMs).toFloat() / (stopMs - startMs)).coerceIn(0f, 1f)
}

/** Categorías de directo. Al elegir una, se reproduce automáticamente su primer canal. */
@Composable
private fun LiveCategories(state: UiState, vm: MainViewModel, modifier: Modifier, horizontal: Boolean) {
    val cats = state.catalog.liveCategories.filter { c ->
        state.catalog.live.any { it.categoryId == c.categoryId }
    }
    if (cats.isEmpty()) return

    @Composable
    fun chip(cat: com.miplayer.tv.data.Category) {
        val active = cat.categoryId == state.liveCatId
        FocusCard(onClick = { vm.selectLiveCategory(cat.categoryId) }) { f ->
            Text(
                cat.categoryName ?: "Sin nombre",
                color = if (active || f) Accent else TextMain,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                fontSize = 14.sp, maxLines = 1,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }

    if (horizontal) {
        androidx.compose.foundation.lazy.LazyRow(
            modifier.padding(vertical = 6.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) { items(cats) { chip(it) } }
    } else {
        LazyColumn(
            modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) { items(cats) { chip(it) } }
    }
}
