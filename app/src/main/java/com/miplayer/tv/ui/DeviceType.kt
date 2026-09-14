package com.miplayer.tv.ui

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/** Detecta si la app corre en un televisor. */
fun isTelevision(context: Context): Boolean {
    val ui = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    return ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
}

/** Pantalla estrecha (móvil en vertical). Sirve para adaptar la interfaz de TV. */
@Composable
fun isCompact(): Boolean = LocalConfiguration.current.screenWidthDp < 600

/** Columnas de la rejilla según el ancho disponible. */
@Composable
fun gridColumns(wide: Int, compact: Int): Int = if (isCompact()) compact else wide

/** Márgenes: en tele hacen falta por el recorte de bordes, en móvil sobran. */
@Composable
fun edgePadding(): Int = if (isCompact()) 16 else 40
