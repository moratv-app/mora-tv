package com.miplayer.tv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Tarjeta enfocable para televisión. Crece y se resalta al recibir el foco del mando,
 * que es lo que guía la vista a distancia.
 */
@Composable
fun FocusCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable (focused: Boolean) -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.07f else 1f, label = "cardScale")

    Box(
        modifier
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .background(Card)
            .border(
                BorderStroke(if (focused) 3.dp else 1.dp, if (focused) Accent else Color(0x22FFFFFF)),
                RoundedCornerShape(14.dp)
            )
            .focusable(interactionSource = interaction)
            .clickable(interactionSource = interaction, indication = null) { onClick() }
    ) { content(focused) }
}

/** Cabecera común de las pantallas internas. */
@Composable
fun ScreenHeader(title: String, subtitle: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("MiPlayer", color = Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            if (subtitle != null)
                Text(subtitle, color = TextSub, fontSize = 13.sp, maxLines = 2)
        }
    }
}

/** Aviso breve en la parte inferior. */
@Composable
fun BoxScope.ToastBar(message: String?, onDone: () -> Unit) {
    if (message == null) return
    LaunchedEffect(message) {
        kotlinx.coroutines.delay(2000)
        onDone()
    }
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .padding(32.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Accent)
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Text(message, color = Color(0xFF0A1414), fontWeight = FontWeight.Bold)
    }
}

/** Texto de una o dos líneas, recortado. */
@Composable
fun CardLabel(text: String, focused: Boolean, modifier: Modifier = Modifier) {
    Text(
        text,
        color = if (focused) Accent else TextMain,
        fontSize = 14.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = modifier
    )
}

/** Pantalla de carga a pantalla completa. */
@Composable
fun LoadingScreen(message: String) {
    Box(Modifier.fillMaxSize().background(Bg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.material3.CircularProgressIndicator(color = Accent)
            Spacer(Modifier.height(20.dp))
            Text(message, color = TextSub, fontSize = 16.sp)
        }
    }
}
