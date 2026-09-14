package com.miplayer.tv.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Paleta "nebula": morado -> azul
val Accent = Color(0xFF8B5CF6)   // morado
val Accent2 = Color(0xFF3B82F6)  // azul
val Bg = Color(0xFF0C0E1A)       // azul muy oscuro
val Card = Color(0xFF181B2E)
val TextMain = Color(0xFFECEEFB)
val TextSub = Color(0xFF9AA0C0)

/** Degradado de marca para fondos y acentos. */
val NebulaGradient = Brush.linearGradient(listOf(Color(0xFF7C3AED), Color(0xFF2563EB)))
val NebulaGradientSoft = Brush.verticalGradient(listOf(Color(0xFF141024), Color(0xFF0C0E1A)))

private val scheme = darkColorScheme(
    primary = Accent,
    secondary = Accent2,
    background = Bg,
    surface = Card,
    onPrimary = Color(0xFFFFFFFF),
    onBackground = TextMain,
    onSurface = TextMain
)

@Composable
fun MoraTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
