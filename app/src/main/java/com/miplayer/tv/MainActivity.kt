package com.miplayer.tv

import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.miplayer.tv.player.PlayerHolder
import com.miplayer.tv.player.PlayerScreen
import com.miplayer.tv.ui.*

class MainActivity : ComponentActivity() {

    // Si hay algo reproduciéndose, para saber cuándo activar la ventana flotante
    private var playbackActive = false
    private val inPip = mutableStateOf(false)
    private var enteredPip = false

    private fun pipSupported(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    /** Entra en ventana flotante si se está reproduciendo. Devuelve true si entró. */
    private fun enterPip(): Boolean {
        if (!playbackActive || !pipSupported()) return false
        return try {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
            )
            true
        } catch (e: Exception) { false }
    }

    // Al pulsar Inicio o cambiar de app, si se está viendo algo, flota
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        enterPip()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean, newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip.value = isInPictureInPictureMode
        if (isInPictureInPictureMode) enteredPip = true
    }

    override fun onStop() {
        super.onStop()
        // Si estábamos en ventana flotante y ya no lo estamos al parar,
        // es que el usuario la cerró con la X: cortamos el sonido.
        // (Con la pantalla bloqueada en reproducción normal, enteredPip es false y sigue sonando.)
        if (enteredPip && !isInPipCompat()) {
            PlayerHolder.stop()
            enteredPip = false
        }
    }

    override fun onResume() {
        super.onResume()
        enteredPip = false
    }

    private fun isInPipCompat(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Mantener el logo un instante para que se vea al arrancar
        var keep = true
        splash.setKeepOnScreenCondition { keep }
        window.decorView.postDelayed({ keep = false }, 900)
        setContent {
            MoraTheme {
                val vm: MainViewModel = viewModel()
                val state by vm.state.collectAsState()
                val isTv = isTelevision(LocalContext.current)
                val pip by inPip

                val playing = state.screen is Screen.Play || state.screen is Screen.Live
                playbackActive = playing

                LaunchedEffect(state.screen, isTv) {
                    requestedOrientation = when {
                        isTv -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        state.screen is Screen.Play -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        state.screen is Screen.Live && state.playerFullscreen ->
                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    }
                }

                // Atrás SIEMPRE navega entre pantallas (gesto o mando).
                // La ventana flotante salta solo al salir con Inicio (onUserLeaveHint).
                BackHandler(enabled = state.screen != Screen.Profiles) { vm.back() }

                when (val s = state.screen) {
                    Screen.Profiles -> if (state.loading) LoadingScreen(state.loadingMsg)
                                       else ProfilesScreen(state, vm)
                    Screen.Dashboard -> if (state.loading) LoadingScreen(state.loadingMsg)
                                        else DashboardScreen(state, vm)
                    is Screen.Browse -> BrowseScreen(state, vm, s.section)
                    Screen.Live -> LiveScreen(state, vm, inPip = pip)
                    is Screen.SeriesDetail -> SeriesDetailScreen(state, vm, s.name)
                    Screen.Search -> SearchScreen(state, vm)
                    Screen.Settings -> SettingsScreen(state, vm)
                    is Screen.Play -> PlayerScreen(
                        url = s.url,
                        startPositionMs = vm.resumeOf(s.itemKey),
                        onPosition = { vm.savePosition(s.itemKey, it) },
                        inPip = pip
                    )
                }
            }
        }
    }
}
