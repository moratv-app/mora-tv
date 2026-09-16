package com.miplayer.tv.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
                // Arranque más rápido al cambiar de canal y menos cortes
                .setBufferDurationsMs(2_500, 20_000, 1_200, 2_500)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build())
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

    // Recarga bajo demanda desde el botón "Recargar"
    LaunchedEffect(state.reloadTick) { if (state.reloadTick > 0) reload() }

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
    var listDialogCh by remember { mutableStateOf<com.miplayer.tv.data.Stream?>(null) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            inPip ->
                VideoSurface(player, Modifier.fillMaxSize(), showFullscreenButton = false)

            fullscreen -> {
                val fr = remember { FocusRequester() }
                LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
                Box(
                    Modifier.fillMaxSize()
                        .focusRequester(fr).focusable()
                        .onPreviewKeyEvent { e ->
                            if (e.type == KeyEventType.KeyDown) when (e.key) {
                                Key.DirectionDown -> { vm.liveNext(); true }
                                Key.DirectionUp -> { vm.livePrev(); true }
                                Key.DirectionLeft -> { vm.setFullscreen(false); true }
                                else -> false
                            } else false
                        }
                ) {
                    VideoWithReload(player, Modifier.fillMaxSize(), isFullscreen = true,
                        onFullscreen = { vm.setFullscreen(false) }, onReload = { reload() })
                }
            }

            compact -> Column(Modifier.fillMaxSize().background(NebulaGradientSoft)) {
                PreviewTile(
                    player, Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                    onClick = { vm.setFullscreen(true) }
                )
                ChannelInfo(state, vm, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp))
                LiveCategories(state, vm, Modifier.fillMaxWidth(), horizontal = true)
                ChannelList(state, vm, Modifier.weight(1f).fillMaxWidth(), onLongPress = { listDialogCh = it })
            }

            else -> Row(Modifier.fillMaxSize().background(NebulaGradientSoft)) {
                LiveCategories(state, vm, Modifier.weight(0.9f).fillMaxHeight(), horizontal = false)
                ChannelList(state, vm, Modifier.weight(1f).fillMaxHeight(), onLongPress = { listDialogCh = it })
                Column(Modifier.weight(1.8f).fillMaxHeight().padding(10.dp)) {
                    PreviewTile(
                        player, Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                        onClick = { vm.setFullscreen(true) }
                    )
                    ChannelInfo(state, vm, Modifier.fillMaxWidth().weight(1f).padding(top = 12.dp))
                }
            }
        }

        listDialogCh?.let { ch ->
            AddToListDialog(state, vm, ch) { listDialogCh = null }
        }
    }
}

/** Vista previa del vídeo como una sola casilla: al pulsar OK va a pantalla completa. */
@Composable
private fun PreviewTile(player: ExoPlayer, modifier: Modifier, onClick: () -> Unit) {
    FocusCard(modifier, onClick = onClick) { _ ->
        VideoSurface(player, Modifier.fillMaxSize(), showControls = false, showFullscreenButton = false)
    }
}

/** Información del canal en directo bajo la vista previa (aprovecha el hueco). */
@Composable
private fun ChannelInfo(state: UiState, vm: MainViewModel, modifier: Modifier) {
    val ch = state.livePlaylist.getOrNull(state.liveIndex) ?: return
    // Guía por canal (get_short_epg); si no hay, se prueba la del XMLTV
    val now = state.currentEpg.getOrNull(0) ?: vm.nowPlaying(ch.epgId)
    val next = state.currentEpg.getOrNull(1) ?: vm.nextProgramme(ch.epgId)
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("● EN DIRECTO", color = Color(0xFFFF5252), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(10.dp))
            Text(ch.name ?: "Canal ${ch.num}", color = TextMain, fontSize = 18.sp,
                fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Spacer(Modifier.height(10.dp))
        if (now != null) {
            Text(now.title, color = TextMain, fontSize = 15.sp, maxLines = 2)
            val frac = progressOf(now.startMs, now.stopMs)
            if (frac != null) androidx.compose.material3.LinearProgressIndicator(
                progress = { frac }, color = Accent, trackColor = Card,
                modifier = Modifier.fillMaxWidth().height(4.dp).padding(top = 6.dp)
            )
            if (next != null) {
                Spacer(Modifier.height(10.dp))
                Text("Después: ${next.title}", color = TextSub, fontSize = 13.sp, maxLines = 1)
            }
        } else {
            Text("Sin información de guía (EPG)", color = TextSub, fontSize = 13.sp)
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RewindChip("↻ Recargar") { vm.requestReload() }
        }

        // Rebobinar (catch-up) solo si el canal guarda archivo
        if (vm.currentSupportsArchive()) {
            Spacer(Modifier.height(14.dp))
            Text(
                if (state.timeshiftBack > 0) "Rebobinado: -${state.timeshiftBack} min" else "Volver atrás",
                color = if (state.timeshiftBack > 0) Accent else TextSub, fontSize = 12.sp
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RewindChip("-30 min") { vm.rewind(30) }
                RewindChip("-1 h") { vm.rewind(60) }
                RewindChip("-2 h") { vm.rewind(120) }
                RewindChip("EN VIVO") { vm.goLive() }
            }
        }
    }
}

@Composable
private fun RewindChip(label: String, onClick: () -> Unit) {
    FocusCard(onClick = onClick) { f ->
        Text(label, color = if (f) Accent else TextMain, fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
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
        // Botón de recargar: solo en vista previa, no a pantalla completa
        if (!isFullscreen) {
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
}

@kotlin.OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelList(state: UiState, vm: MainViewModel, modifier: Modifier, onLongPress: (com.miplayer.tv.data.Stream) -> Unit = {}) {
    val listState = rememberLazyListState()
    val playingFocus = remember { FocusRequester() }
    LaunchedEffect(state.playerFullscreen) {
        if (!state.playerFullscreen && state.livePlaylist.isNotEmpty()) {
            runCatching { listState.scrollToItem(state.liveIndex) }
            kotlinx.coroutines.delay(80)
            runCatching { playingFocus.requestFocus() }
        }
    }
    LazyColumn(modifier.padding(horizontal = 12.dp), state = listState) {
        itemsIndexed(state.livePlaylist) { i, ch ->
            val focusMod = if (i == state.liveIndex) Modifier.focusRequester(playingFocus) else Modifier
            val playing = i == state.liveIndex
            val now = vm.nowPlaying(ch.epgId)
            val interaction = remember { MutableInteractionSource() }
            val focused by interaction.collectIsFocusedAsState()
            Row(
                focusMod.fillMaxWidth().padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (playing) Accent.copy(alpha = 0.20f) else Card)
                    .border(
                        BorderStroke(if (focused) 3.dp else 0.dp, if (focused) Accent else Color.Transparent),
                        RoundedCornerShape(10.dp)
                    )
                    .combinedClickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = { if (i == state.liveIndex) vm.setFullscreen(true) else vm.selectLiveIndex(i) },
                        onLongClick = { onLongPress(ch) }
                    )
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
    val real: List<Pair<String?, String>> = state.catalog.liveCategories
        .filter { c -> state.catalog.live.any { it.categoryId == c.categoryId } }
        .map { it.categoryId to (it.categoryName ?: "Sin nombre") }
    val custom: List<Pair<String?, String>> = state.customLists.keys.map { "mylist:$it" to "★ $it" }
    val all = custom + real
    if (all.isEmpty()) return

    @Composable
    fun chip(id: String?, label: String, fill: Boolean) {
        val active = id == state.liveCatId
        FocusCard(if (fill) Modifier.fillMaxWidth() else Modifier,
            onClick = { vm.selectLiveCategory(id) }) { f ->
            Text(
                label,
                color = if (active || f) Accent else TextMain,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                fontSize = 14.sp, maxLines = 1,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
            )
        }
    }

    if (horizontal) {
        androidx.compose.foundation.lazy.LazyRow(
            modifier.padding(vertical = 6.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) { items(all) { chip(it.first, it.second, fill = false) } }
    } else {
        LazyColumn(
            modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) { items(all) { chip(it.first, it.second, fill = true) } }
    }
}

/** Diálogo para añadir/quitar un canal de tus listas, o crear una nueva. */
@Composable
private fun AddToListDialog(state: UiState, vm: MainViewModel, ch: com.miplayer.tv.data.Stream, onClose: () -> Unit) {
    val key = "live:${ch.streamId}"
    var newName by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { androidx.compose.material3.TextButton(onClick = onClose) { Text("Cerrar", color = Accent) } },
        title = { Text("Añadir a lista: ${ch.name ?: "Canal"}", color = TextMain, fontSize = 18.sp) },
        containerColor = Card,
        text = {
            Column {
                state.customLists.forEach { (name, keys) ->
                    val inside = key in keys
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .clickable { vm.toggleChannelInList(name, key) }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (inside) "☑" else "☐", color = Accent, fontSize = 18.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(name, color = TextMain, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = newName, onValueChange = { newName = it }, singleLine = true,
                    label = { Text("Nueva lista (ej. Futbol)") }, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.TextButton(
                    enabled = newName.isNotBlank(),
                    onClick = {
                        vm.createList(newName)
                        vm.toggleChannelInList(newName, key)   // mete el canal en la nueva lista
                        newName = ""
                    }
                ) { Text("Crear lista y añadir", color = if (newName.isNotBlank()) Accent else TextSub) }
            }
        }
    )
}
