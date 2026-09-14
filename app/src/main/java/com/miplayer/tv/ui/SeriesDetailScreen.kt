package com.miplayer.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miplayer.tv.data.SeasonGroup

@Composable
fun SeriesDetailScreen(state: UiState, vm: MainViewModel, seriesName: String) {
    var selectedSeason by remember(state.seriesSeasons) {
        mutableStateOf(state.seriesSeasons.firstOrNull()?.season ?: 0)
    }
    val season: SeasonGroup? = state.seriesSeasons.firstOrNull { it.season == selectedSeason }

    Box(Modifier.fillMaxSize().background(NebulaGradientSoft)) {
        Column(Modifier.fillMaxSize().padding(horizontal = edgePadding().dp, vertical = 20.dp)) {
            ScreenHeader(seriesName, if (state.seriesSeasons.isNotEmpty())
                "${state.seriesSeasons.size} temporadas" else null)
            Spacer(Modifier.height(16.dp))

            when {
                state.seriesLoadingInfo -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Accent)
                }
                state.seriesSeasons.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No se encontraron episodios", color = TextSub)
                }
                else -> {
                    // Temporadas en fila
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.seriesSeasons) { g ->
                            val active = g.season == selectedSeason
                            FocusCard(onClick = { selectedSeason = g.season }) { f ->
                                Text("Temporada ${g.season}",
                                    color = if (active || f) Accent else TextMain,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    // Episodios de la temporada elegida
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        itemsIndexed(season?.episodes ?: emptyList()) { _, ep ->
                            FocusCard(Modifier.fillMaxWidth(), onClick = { vm.playEpisode(ep) }) { f ->
                                Row(Modifier.fillMaxWidth().padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.PlayArrow, null,
                                        tint = if (f) Accent else TextSub)
                                    Spacer(Modifier.width(14.dp))
                                    Text("${ep.episodeNum}. ${ep.title}",
                                        color = if (f) Accent else TextMain, fontSize = 15.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
