package com.miplayer.tv.player

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * Superficie de vídeo reutilizable. La misma instancia sirve para la vista previa
 * (pequeña, en vertical) y para la pantalla completa (horizontal): solo cambia el
 * tamaño y el botón de pantalla completa.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoSurface(
    player: ExoPlayer,
    modifier: Modifier = Modifier,
    showControls: Boolean = true,
    showFullscreenButton: Boolean = true,
    isFullscreen: Boolean = false,
    onFullscreenToggle: () -> Unit = {}
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = showControls
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                if (showControls && showFullscreenButton)
                    setFullscreenButtonClickListener { onFullscreenToggle() }
                else
                    setFullscreenButtonClickListener(null)
            }
        },
        update = { view ->
            view.useController = showControls
            if (showControls && showFullscreenButton)
                view.setFullscreenButtonClickListener { onFullscreenToggle() }
            else
                view.setFullscreenButtonClickListener(null)
        }
    )
}
