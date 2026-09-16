package com.miplayer.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miplayer.tv.data.Profile

/**
 * Pantalla inicial: lista de perfiles guardados y formulario para añadir uno.
 * Sustituye al login de un solo uso: aquí se guardan varios servidores.
 */
@Composable
fun ProfilesScreen(state: UiState, vm: MainViewModel) {
    var adding by remember { mutableStateOf(state.profiles.isEmpty()) }

    Box(Modifier.fillMaxSize().background(NebulaGradientSoft)) {
        if (adding) AddProfileForm(state, vm) { adding = false }
        else ProfileList(state, vm) { adding = true }

        Text(
            "v" + com.miplayer.tv.data.AppVersion.name(androidx.compose.ui.platform.LocalContext.current),
            color = TextSub, fontSize = 11.sp,
            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)
        )
        ToastBar(state.toast) { vm.clearToast() }
    }
}

@Composable
private fun ProfileList(state: UiState, vm: MainViewModel, onAdd: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = edgePadding().dp, vertical = 24.dp)) {
        ScreenHeader("Elige un perfil")
        Spacer(Modifier.height(16.dp))
        if (state.update != null) {
            UpdateBanner(state, vm)
            Spacer(Modifier.height(16.dp))
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(state.profiles) { p ->
                FocusCard(Modifier.fillMaxWidth(), onClick = { vm.openProfile(p) }) { focused ->
                    Row(
                        Modifier.fillMaxWidth().padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Person, null, tint = if (focused) Accent else TextSub)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, color = if (focused) Accent else TextMain,
                                fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("${p.username} · ${p.host}", color = TextSub, fontSize = 14.sp)
                        }
                        TextButton(onClick = { vm.deleteProfile(p) }) {
                            Icon(Icons.Filled.Delete, null, tint = TextSub)
                        }
                    }
                }
            }
            item {
                FocusCard(Modifier.fillMaxWidth(), onClick = onAdd) { focused ->
                    Row(
                        Modifier.fillMaxWidth().padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Add, null, tint = if (focused) Accent else TextSub)
                        Spacer(Modifier.width(16.dp))
                        Text("Añadir otro servidor",
                            color = if (focused) Accent else TextMain, fontSize = 18.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun AddProfileForm(state: UiState, vm: MainViewModel, onCancel: () -> Unit) {
    var pasteMode by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var m3uUrl by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(color = Card, shape = RoundedCornerShape(22.dp),
            modifier = Modifier.widthIn(max = 520.dp).padding(horizontal = 24.dp)) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Conectar servidor", color = TextMain, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(if (pasteMode) "Pega tu enlace M3U y detecto todo solo"
                     else "Introduce los datos que te dio tu proveedor",
                    color = TextSub, fontSize = 14.sp)
                if (state.update != null) {
                    Spacer(Modifier.height(14.dp))
                    UpdateBanner(state, vm)
                }
                Spacer(Modifier.height(18.dp))

                // Selector de modo
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ModeChip("Pegar enlace M3U", pasteMode, Modifier.weight(1f)) { pasteMode = true }
                    ModeChip("Escribir a mano", !pasteMode, Modifier.weight(1f)) { pasteMode = false }
                }
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(name, { name = it }, singleLine = true,
                    label = { Text("Nombre del perfil (opcional)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))

                if (pasteMode) {
                    OutlinedTextField(m3uUrl, { m3uUrl = it }, singleLine = false, maxLines = 3,
                        label = { Text("Enlace M3U (con get.php)") },
                        placeholder = { Text("http://host:puerto/get.php?username=...&password=...") },
                        modifier = Modifier.fillMaxWidth())
                } else {
                    OutlinedTextField(host, { host = it }, singleLine = true,
                        label = { Text("Servidor (host:puerto)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(user, { user = it }, singleLine = true,
                        label = { Text("Usuario") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(pass, { pass = it }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        label = { Text("Contraseña") }, modifier = Modifier.fillMaxWidth())
                }

                if (state.error != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(state.error, color = androidx.compose.ui.graphics.Color(0xFFFF6B6B), fontSize = 14.sp)
                }

                Spacer(Modifier.height(22.dp))
                val canConnect = !state.loading &&
                    (if (pasteMode) m3uUrl.isNotBlank() else host.isNotBlank() && user.isNotBlank())
                Box(
                    Modifier.fillMaxWidth().height(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (canConnect) NebulaGradient else androidx.compose.ui.graphics.SolidColor(Card))
                        .clickable(enabled = canConnect) {
                            if (pasteMode) vm.addFromUrl(name, m3uUrl)
                            else vm.addProfile(name, host, user, pass)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (state.loading) state.loadingMsg else "CONECTAR",
                        color = TextMain, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
                if (state.profiles.isNotEmpty()) {
                    TextButton(onClick = onCancel) { Text("Volver a la lista", color = TextSub) }
                }
            }
        }
    }
}

@Composable
private fun ModeChip(text: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FocusCard(modifier.height(46.dp), onClick = onClick) { focused ->
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Text(
                text,
                color = if (active || focused) Accent else TextMain,
                fontSize = 14.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}
