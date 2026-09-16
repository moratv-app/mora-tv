package com.miplayer.tv.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.miplayer.tv.R

/** Pantalla de arranque: logo, nombre y versión. */
@Composable
fun SplashContent() {
    Box(Modifier.fillMaxSize().background(NebulaGradientSoft), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.logo_header),
                contentDescription = "Mora TV",
                modifier = Modifier.size(110.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text("Mora TV", color = TextMain, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("v" + com.miplayer.tv.data.AppVersion.name(androidx.compose.ui.platform.LocalContext.current), color = TextSub, fontSize = 14.sp)
        }
    }
}
