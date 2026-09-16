package com.miplayer.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miplayer.tv.data.Section

/** Menú principal: una tarjeta grande por sección, navegable con el mando. */
@Composable
fun DashboardScreen(state: UiState, vm: MainViewModel) {
    val c = state.catalog
    val tiles = listOf(
        Tile(Section.LIVE, Icons.Filled.LiveTv, "${c.live.size} canales"),
        Tile(Section.MOVIES, Icons.Filled.Movie, "${c.movies.size} películas"),
        Tile(Section.SERIES, Icons.Filled.Tv, "${c.series.size} series"),
        Tile(Section.FAVORITES, Icons.Filled.Star, "${state.favorites.size} guardados"),
        Tile(Section.SEARCH, Icons.Filled.Search, "En todo el catálogo"),
        Tile(Section.SETTINGS, Icons.Filled.Settings, "Perfil y opciones")
    )

    val info = state.catalog.userInfo
    val subtitle = buildString {
        append(state.active?.name ?: "")
        info?.expDate?.let { exp ->
            val txt = runCatching {
                java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                    .format(java.util.Date(exp.toLong() * 1000))
            }.getOrNull()
            if (txt != null) append("  ·  Caduca: $txt")
        }
        info?.maxConnections?.let { append("  ·  $it conexiones") }
    }

    val ctx = androidx.compose.ui.platform.LocalContext.current
    Box(Modifier.fillMaxSize().background(NebulaGradientSoft)) {
        val perRow = if (isCompact()) 2 else 3
        Column(Modifier.fillMaxSize().padding(horizontal = edgePadding().dp, vertical = 24.dp)) {
            ScreenHeader("Inicio", subtitle)
            Spacer(Modifier.height(14.dp))

            // Aviso de actualización disponible (solo si es realmente más nueva)
            state.update?.takeIf { it.versionCode > state.installedVersionCode }?.let { up ->
                FocusCard(Modifier.fillMaxWidth(), onClick = { vm.applyUpdate(ctx) }) { f ->
                    androidx.compose.foundation.layout.Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Icon(
                            androidx.compose.material.icons.Icons.Filled.Download, null,
                            tint = Accent)
                        Spacer(Modifier.width(12.dp))
                        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                            Text("Actualización disponible (${up.versionName})",
                                color = if (f) Accent else TextMain, fontWeight = FontWeight.Bold)
                            Text("Pulsa para descargar e instalar", color = TextSub, fontSize = 13.sp)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            Spacer(Modifier.height(if (isCompact()) 6.dp else 20.dp))

            tiles.chunked(perRow).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { t -> SectionTile(t, Modifier.weight(1f)) { open(vm, t.section) } }
                    repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        Text(
            "v" + com.miplayer.tv.data.AppVersion.name(ctx),
            color = TextSub, fontSize = 11.sp,
            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)
        )
        ToastBar(state.toast) { vm.clearToast() }
    }
}

private fun open(vm: MainViewModel, s: Section) = when (s) {
    Section.SEARCH -> vm.go(Screen.Search)
    Section.SETTINGS -> vm.go(Screen.Settings)
    Section.LIVE -> vm.openLive()          // entra y reproduce ya
    else -> vm.go(Screen.Browse(s))
}

private data class Tile(val section: Section, val icon: ImageVector, val hint: String)

@Composable
private fun SectionTile(tile: Tile, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FocusCard(modifier.height(if (isCompact()) 130.dp else 170.dp), onClick = onClick) { focused ->
        Column(
            Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(tile.icon, null, tint = if (focused) Accent else TextMain,
                modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(14.dp))
            Text(tile.section.title, color = if (focused) Accent else TextMain,
                fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(tile.hint, color = TextSub, fontSize = 13.sp)
        }
    }
}
