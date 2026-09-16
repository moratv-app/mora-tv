package com.miplayer.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miplayer.tv.BuildConfig

@Composable
fun ProLoginScreen(state: UiState, vm: MainViewModel) {
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var panel by remember { mutableStateOf(vm.proPanelUrl()) }
    var showPanel by remember { mutableStateOf(panel.isBlank() || BuildConfig.PANEL_URL.isBlank()) }

    Box(Modifier.fillMaxSize().background(NebulaGradientSoft)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                color = Card, shape = RoundedCornerShape(22.dp),
                modifier = Modifier.widthIn(max = 480.dp).padding(horizontal = 24.dp)
            ) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Mora TV Pro", color = TextMain, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("Usuario y contraseña. Nada más.", color = TextSub, fontSize = 14.sp)
                    Spacer(Modifier.height(18.dp))
                    OutlinedTextField(user, { user = it }, singleLine = true,
                        label = { Text("Usuario") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(pass, { pass = it }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        label = { Text("Contraseña") }, modifier = Modifier.fillMaxWidth())
                    if (showPanel) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(panel, { panel = it }, singleLine = true,
                            label = { Text("Panel (IP:8787)") }, modifier = Modifier.fillMaxWidth())
                    }
                    if (state.error != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(state.error!!, color = androidx.compose.ui.graphics.Color(0xFFFB7185), fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(16.dp))
                    FocusCard(Modifier.fillMaxWidth().height(52.dp), onClick = {
                        vm.proLogin(panel, user, pass)
                    }) { f ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                if (state.loading) state.loadingMsg else "Entrar",
                                color = if (f) Accent else TextMain,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    TextButton(onClick = { showPanel = !showPanel }) {
                        Text(if (showPanel) "Ocultar panel" else "Configurar panel", color = TextSub, fontSize = 13.sp)
                    }
                }
            }
        }
        ToastBar(state.toast) { vm.clearToast() }
    }
}
