package com.miplayer.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Ajustes: formato de emisión, refresco y borrado de datos locales. */
@Composable
fun SettingsScreen(state: UiState, vm: MainViewModel) {
    var format by remember { mutableStateOf(vm.settings.streamFormat) }
    val ctx = androidx.compose.ui.platform.LocalContext.current

    Box(Modifier.fillMaxSize().background(NebulaGradientSoft)) {
        Column(Modifier.fillMaxSize().padding(horizontal = edgePadding().dp, vertical = 24.dp)) {
            ScreenHeader("Ajustes", state.active?.name)
            Spacer(Modifier.height(28.dp))

            Text("Formato de emisión", color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Si un canal no arranca, prueba a cambiarlo.", color = TextSub, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(".ts" to "TS (recomendado)", ".m3u8" to "HLS", "" to "Sin extensión").forEach { (v, label) ->
                    FocusCard(Modifier.weight(1f).height(64.dp), onClick = {
                        format = v; vm.settings.streamFormat = v
                    }) { focused ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                if (format == v) "● $label" else label,
                                color = when {
                                    format == v -> Accent
                                    focused -> Accent
                                    else -> TextMain
                                },
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
            Text("Mantenimiento", color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FocusCard(Modifier.weight(1f).height(64.dp), onClick = { vm.refresh() }) { f ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Actualizar catálogo", color = if (f) Accent else TextMain, fontSize = 15.sp)
                    }
                }
                FocusCard(Modifier.weight(1f).height(64.dp), onClick = { vm.clearLocalData() }) { f ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Borrar datos locales", color = if (f) Accent else TextMain, fontSize = 15.sp)
                    }
                }
                FocusCard(Modifier.weight(1f).height(64.dp), onClick = { vm.go(Screen.Profiles) }) { f ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Cambiar de perfil", color = if (f) Accent else TextMain, fontSize = 15.sp)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            FocusCard(Modifier.fillMaxWidth().height(56.dp), onClick = {
                // Cierra la app por completo (útil tras actualizar, para que arranque la versión nueva)
                (ctx as? android.app.Activity)?.finishAndRemoveTask()
                kotlin.system.exitProcess(0)
            }) { f ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Salir de Mora TV", color = if (f) Accent else TextMain,
                        fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(32.dp))
            Text(
                "Esta app no muestra publicidad, no envía datos a terceros y no se " +
                "actualiza sola desde servidores externos. Tus credenciales se guardan cifradas.",
                color = TextSub, fontSize = 13.sp
            )
            Spacer(Modifier.height(12.dp))
            Text("Mora TV versión " + com.miplayer.tv.BuildConfig.VERSION_NAME, color = TextSub, fontSize = 12.sp)
        }
        ToastBar(state.toast) { vm.clearToast() }
    }
}
