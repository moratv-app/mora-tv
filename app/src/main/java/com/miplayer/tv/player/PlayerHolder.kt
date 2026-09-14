package com.miplayer.tv.player

import androidx.media3.exoplayer.ExoPlayer

/** Referencia al reproductor activo, para poder pararlo al cerrar la ventana flotante. */
object PlayerHolder {
    var current: ExoPlayer? = null
    fun stop() {
        current?.let { it.pause(); it.stop() }
    }
}
