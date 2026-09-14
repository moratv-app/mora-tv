package com.miplayer.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miplayer.tv.data.MediaCard
import com.miplayer.tv.data.Section
import kotlinx.coroutines.delay

/**
 * Búsqueda global en canales, películas y series.
 * Con retardo de 300 ms para no recalcular en cada tecla, que es el fallo
 * que tenía la app analizada.
 */
@Composable
fun SearchScreen(state: UiState, vm: MainViewModel) {
    var text by remember { mutableStateOf(state.query) }
    var applied by remember { mutableStateOf(state.query) }

    LaunchedEffect(text) {
        delay(300)
        applied = text
    }

    val results = remember(applied, state.catalog) {
        if (applied.length < 2) emptyList() else {
            val q = applied.lowercase()
            val c = state.catalog
            c.live.filter { it.name?.lowercase()?.contains(q) == true }.take(60).map {
                MediaCard("live:${it.streamId}", it.name ?: "", image = it.icon, section = Section.LIVE, num = it.num, streamId = it.streamId)
            } + c.movies.filter { it.name?.lowercase()?.contains(q) == true }.take(60).map {
                MediaCard("movie:${it.streamId}", it.name ?: "", image = it.icon, section = Section.MOVIES,
                    streamId = it.streamId, ext = it.ext)
            } + c.series.filter { it.name?.lowercase()?.contains(q) == true }.take(60).map {
                MediaCard("series:${it.seriesId}", it.name ?: "", image = it.cover, section = Section.SERIES,
                    seriesId = it.seriesId)
            }
        }
    }

    Box(Modifier.fillMaxSize().background(NebulaGradientSoft)) {
        Column(Modifier.fillMaxSize().padding(horizontal = edgePadding().dp, vertical = 20.dp)) {
            ScreenHeader("Buscar", if (applied.length >= 2) "${results.size} resultados" else null)
            Spacer(Modifier.height(18.dp))

            OutlinedTextField(
                value = text,
                onValueChange = { text = it; vm.setQuery(it) },
                singleLine = true,
                label = { Text("Escribe al menos 2 letras") },
                modifier = Modifier.fillMaxWidth(if (isCompact()) 1f else 0.6f)
            )
            Spacer(Modifier.height(20.dp))

            if (results.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (applied.length < 2) "Busca canales, películas o series"
                        else "Sin resultados",
                        color = TextSub, fontSize = 16.sp
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridColumns(wide = 6, compact = 3)),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(results) { card ->
                        MediaTile(
                            card = card,
                            isFav = card.id in state.favorites,
                            onClick = {
                                when (card.section) {
                                    Section.LIVE -> state.catalog.live
                                        .firstOrNull { it.streamId == card.streamId }?.let { vm.playLive(it) }
                                    Section.MOVIES -> state.catalog.movies
                                        .firstOrNull { it.streamId == card.streamId }?.let { vm.playMovie(it) }
                                    Section.SERIES -> state.catalog.series
                                        .firstOrNull { it.seriesId == card.seriesId }?.let { vm.openSeries(it) }
                                    else -> Unit
                                }
                            },
                            onLong = { vm.toggleFavorite(card.id) }
                        )
                    }
                }
            }
        }
        ToastBar(state.toast) { vm.clearToast() }
    }
}
