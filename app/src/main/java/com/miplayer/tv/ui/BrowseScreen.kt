package com.miplayer.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.miplayer.tv.data.*

/**
 * Navegador de contenido: categorías a la izquierda, rejilla a la derecha.
 * Sirve para canales, películas, series y favoritos.
 */
@Composable
fun BrowseScreen(state: UiState, vm: MainViewModel, section: Section) {
    val cats = remember(state.catalog, section) { categoriesFor(state, section) }
    val selected = state.selectedCategory ?: cats.firstOrNull()?.first
    val items = remember(state.catalog, section, selected, state.favorites) {
        itemsFor(state, section, selected)
    }

    val compact = isCompact()
    val pad = edgePadding().dp

    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.fillMaxSize().padding(horizontal = pad, vertical = if (compact) 16.dp else 28.dp)) {
            ScreenHeader(section.title, "${items.size} elementos")
            Spacer(Modifier.height(14.dp))

            // En móvil las categorías van arriba en horizontal; en tele, en barra lateral
            if (compact && cats.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listItems(cats) { (id, name) ->
                        val active = id == selected
                        FocusCard(onClick = { vm.selectCategory(id) }) { f ->
                            Text(
                                name,
                                color = when { active -> Accent; f -> Accent; else -> TextMain },
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 14.sp,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            Row(Modifier.fillMaxSize()) {
                // Columna de categorías (solo pantalla ancha)
                if (!compact && cats.isNotEmpty()) {
                    LazyColumn(
                        Modifier.width(260.dp).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listItems(cats) { (id, name) ->
                            val active = id == selected
                            FocusCard(Modifier.fillMaxWidth(), onClick = { vm.selectCategory(id) }) { f ->
                                Text(
                                    name,
                                    color = when { active -> Accent; f -> Accent; else -> TextMain },
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 15.sp,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(24.dp))
                }

                // Rejilla de contenido
                if (items.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No hay contenido en esta categoría", color = TextSub)
                    }
                } else if (section == Section.LIVE) {
                    // Directo en LISTA: se ven más canales de un vistazo
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listItems(items) { card ->
                            ChannelRow(
                                card = card,
                                isFav = card.favKey() in state.favorites,
                                onClick = { openCard(vm, state, card) },
                                onFav = { vm.toggleFavorite(card.favKey()) }
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(gridColumns(wide = 6, compact = 3)),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(items) { card ->
                            MediaTile(
                                card = card,
                                isFav = card.favKey() in state.favorites,
                                onClick = { openCard(vm, state, card) },
                                onLong = { vm.toggleFavorite(card.favKey()) }
                            )
                        }
                    }
                }
            }
        }
        ToastBar(state.toast) { vm.clearToast() }
    }
}

@Composable
fun MediaTile(card: MediaCard, isFav: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    val tall = card.section != Section.LIVE
    val h = if (isCompact()) (if (tall) 190.dp else 120.dp) else (if (tall) 230.dp else 150.dp)
    FocusCard(Modifier.height(h), onClick = onClick) { focused ->
        Column(
            Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = card.image,
                    contentDescription = null,
                    contentScale = if (tall) ContentScale.Crop else ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                if (isFav) {
                    Icon(
                        Icons.Filled.Star, null, tint = Accent,
                        modifier = Modifier.align(Alignment.TopEnd).size(20.dp)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            CardLabel(card.title, focused)
            if (focused) {
                Text("Mantén OK para favorito", color = TextSub, fontSize = 10.sp)
            }
        }
    }
}

private fun MediaCard.favKey() = when (section) {
    Section.MOVIES -> "movie:$streamId"
    Section.SERIES -> "series:$seriesId"
    else -> "live:$streamId"
}

private fun openCard(vm: MainViewModel, state: UiState, card: MediaCard) {
    when (card.section) {
        Section.LIVE -> state.catalog.live.firstOrNull { it.streamId == card.streamId }
            ?.let { vm.playLive(it) }
        Section.MOVIES -> state.catalog.movies.firstOrNull { it.streamId == card.streamId }
            ?.let { vm.playMovie(it) }
        else -> Unit   // series: pendiente la pantalla de episodios
    }
}

// ---------- Construcción de listas ----------

private fun categoriesFor(state: UiState, section: Section): List<Pair<String?, String>> {
    val c = state.catalog
    val raw = when (section) {
        Section.LIVE -> c.liveCategories
        Section.MOVIES -> c.movieCategories
        Section.SERIES -> c.seriesCategories
        else -> emptyList()
    }
    if (section == Section.FAVORITES) return emptyList()
    return listOf<Pair<String?, String>>(null to "Todo") +
        raw.mapNotNull { it.categoryId?.let { id -> id to (it.categoryName ?: "Sin nombre") } }
}

private fun itemsFor(state: UiState, section: Section, categoryId: String?): List<MediaCard> {
    val c = state.catalog
    fun liveCards(list: List<Stream>) = list.map {
        MediaCard("live:${it.streamId}", it.name ?: "Canal ${it.num}", image = it.icon,
            section = Section.LIVE, num = it.num, streamId = it.streamId, categoryId = it.categoryId)
    }
    fun movieCards(list: List<Stream>) = list.map {
        MediaCard("movie:${it.streamId}", it.name ?: "Película", image = it.icon,
            section = Section.MOVIES, streamId = it.streamId, ext = it.ext, categoryId = it.categoryId)
    }
    fun seriesCards(list: List<SeriesItem>) = list.map {
        MediaCard("series:${it.seriesId}", it.name ?: "Serie", image = it.cover,
            section = Section.SERIES, seriesId = it.seriesId, categoryId = it.categoryId)
    }

    return when (section) {
        Section.LIVE -> liveCards(c.live.filter { categoryId == null || it.categoryId == categoryId })
        Section.MOVIES -> movieCards(c.movies.filter { categoryId == null || it.categoryId == categoryId })
        Section.SERIES -> seriesCards(c.series.filter { categoryId == null || it.categoryId == categoryId })
        Section.FAVORITES -> {
            val f = state.favorites
            liveCards(c.live.filter { "live:${it.streamId}" in f }) +
            movieCards(c.movies.filter { "movie:${it.streamId}" in f }) +
            seriesCards(c.series.filter { "series:${it.seriesId}" in f })
        }
        else -> emptyList()
    }
}

@Composable
fun ChannelRow(card: MediaCard, isFav: Boolean, onClick: () -> Unit, onFav: () -> Unit) {
    FocusCard(Modifier.fillMaxWidth(), onClick = onClick) { focused ->
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (card.num > 0) "${card.num}" else "",
                color = TextSub, fontSize = 13.sp,
                modifier = Modifier.width(38.dp)
            )
            AsyncImage(
                model = card.image, contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                card.title,
                color = if (focused) Accent else TextMain,
                fontSize = 16.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            if (isFav) {
                Icon(Icons.Filled.Star, null, tint = Accent, modifier = Modifier.size(20.dp))
            }
        }
    }
}
