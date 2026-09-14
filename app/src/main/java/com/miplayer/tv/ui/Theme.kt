package com.miplayer.tv.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFF00C8B4)
val Accent2 = Color(0xFF785AFF)
val Bg = Color(0xFF0F121C)
val Card = Color(0xFF1C2130)
val TextMain = Color(0xFFEBF0F8)
val TextSub = Color(0xFF96A0B4)

private val scheme = darkColorScheme(
    primary = Accent,
    secondary = Accent2,
    background = Bg,
    surface = Card,
    onPrimary = Color(0xFF0A1414),
    onBackground = TextMain,
    onSurface = TextMain
)

@Composable
fun MiPlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
