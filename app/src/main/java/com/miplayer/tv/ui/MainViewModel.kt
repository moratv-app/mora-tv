package com.miplayer.tv.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.miplayer.tv.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Pantalla en la que está el usuario. */
sealed class Screen {
    data object Profiles : Screen()
    data object Dashboard : Screen()
    data class Browse(val section: Section) : Screen()
    data object Search : Screen()
    data object Settings : Screen()
    data class Play(val url: String, val title: String, val itemKey: String) : Screen()
    /** Directo con vista previa + lista de canales (móvil vertical). */
    data object Live : Screen()
    data class SeriesDetail(val seriesId: Int, val name: String) : Screen()
}

data class UiState(
    val screen: Screen = Screen.Profiles,
    val profiles: List<Profile> = emptyList(),
    val active: Profile? = null,
    val catalog: Catalog = Catalog(),
    val loading: Boolean = false,
    val loadingMsg: String = "",
    val error: String? = null,
    val toast: String? = null,
    val selectedCategory: String? = null,
    val query: String = "",
    val favorites: Set<String> = emptySet(),
    val livePlaylist: List<Stream> = emptyList(),
    val liveIndex: Int = 0,
    val playerFullscreen: Boolean = false,
    val update: UpdateInfo? = null,
    val seriesSeasons: List<SeasonGroup> = emptyList(),
    val seriesLoadingInfo: Boolean = false,
    val epg: Map<String, List<Programme>> = emptyMap()
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val profileStore = ProfileStore(app)
    private val userData = UserDataStore(app)
    private val catalogCache = CatalogCache(app)
    val settings = SettingsStore(app)

    private var playOrigin: Screen = Screen.Dashboard
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    init {
        val profiles = profileStore.all()
        val active = profileStore.active()
        _state.value = _state.value.copy(
            profiles = profiles,
            active = active,
            favorites = active?.let { userData.favorites(it.id) } ?: emptySet(),
            screen = Screen.Profiles
        )
        if (active != null) openProfile(active)
        checkUpdate()
    }

    fun checkUpdate() {
        viewModelScope.launch {
            val u = UpdateChecker.check()
            if (u != null) _state.value = _state.value.copy(update = u)
        }
    }

    fun applyUpdate(context: android.content.Context) {
        val u = _state.value.update ?: return
        // Si falta el permiso de instalar apps, llevamos al usuario a activarlo
        if (!UpdateChecker.canInstall(context)) {
            _state.value = _state.value.copy(
                toast = "Activa \"permitir instalar apps\" y vuelve a pulsar")
            UpdateChecker.openInstallPermission(context)
            return
        }
        _state.value = _state.value.copy(toast = "Descargando actualización...")
        viewModelScope.launch {
            val file = UpdateChecker.download(context, u.apkUrl)
            if (file == null) {
                _state.value = _state.value.copy(toast = "No se pudo descargar la actualización")
            } else {
                UpdateChecker.install(context, file)
            }
        }
    }

    fun dismissUpdate() { _state.value = _state.value.copy(update = null) }

    // ---------- Perfiles ----------

    fun addProfile(name: String, host: String, user: String, pass: String) {
        val cleanHost = host.trim().trimEnd('/').let {
            if (it.startsWith("http")) it else "http://$it"
        }
        val p = Profile(
            id = user.trim() + "@" + cleanHost,
            name = name.ifBlank { user }.trim(),
            host = cleanHost, username = user.trim(), password = pass.trim()
        )
        _state.value = _state.value.copy(loading = true, loadingMsg = "Comprobando cuenta...", error = null)
        viewModelScope.launch {
            val err = CatalogRepository.validate(p)
            if (err != null) {
                _state.value = _state.value.copy(loading = false, error = err)
            } else {
                profileStore.add(p)
                _state.value = _state.value.copy(profiles = profileStore.all())
                openProfile(p)
            }
        }
    }

    /**
     * Crea un perfil a partir de una URL pegada. Los enlaces M3U de Xtream
     * (get.php) llevan dentro usuario, contraseña y host, así que se extraen
     * y se crea un perfil Xtream completo (con categorías, películas y series).
     */
    fun addFromUrl(name: String, rawUrl: String) {
        val url = rawUrl.trim()
        val uri = runCatching { android.net.Uri.parse(url) }.getOrNull()
        val user = uri?.getQueryParameter("username")
        val pass = uri?.getQueryParameter("password")
        val host = if (uri?.scheme != null && uri.authority != null)
            "${uri.scheme}://${uri.authority}" else null

        if (user.isNullOrBlank() || pass.isNullOrBlank() || host == null) {
            _state.value = _state.value.copy(
                error = "No pude leer los datos del enlace. Pega la URL M3U completa " +
                        "(la que contiene get.php con username y password)."
            )
            return
        }
        addProfile(name.ifBlank { "Mi lista" }, host, user, pass)
    }

    fun openProfile(p: Profile) {
        profileStore.setActive(p.id)
        val cached = catalogCache.load(p.id)
        if (cached != null && (cached.live.isNotEmpty() || cached.movies.isNotEmpty() || cached.series.isNotEmpty())) {
            // Arranque instantáneo con la copia guardada; refresco en segundo plano
            _state.value = _state.value.copy(
                active = p, catalog = cached, loading = false,
                screen = Screen.Dashboard, error = null, favorites = userData.favorites(p.id)
            )
            loadEpg()
            viewModelScope.launch {
                val fresh = CatalogRepository.load(p)
                if (fresh.live.isNotEmpty() || fresh.movies.isNotEmpty() || fresh.series.isNotEmpty()) {
                    catalogCache.save(p.id, fresh)
                    _state.value = _state.value.copy(catalog = fresh)
                }
            }
        } else {
            _state.value = _state.value.copy(
                active = p, loading = true, loadingMsg = "Cargando catálogo...",
                error = null, favorites = userData.favorites(p.id)
            )
            viewModelScope.launch {
                val cat = CatalogRepository.load(p)
                if (cat.live.isEmpty() && cat.movies.isEmpty() && cat.series.isEmpty()) {
                    _state.value = _state.value.copy(loading = false,
                        error = "No se recibió contenido. Revisa el servidor o las credenciales.")
                } else {
                    catalogCache.save(p.id, cat)
                    _state.value = _state.value.copy(catalog = cat, loading = false, screen = Screen.Dashboard)
                    loadEpg()
                }
            }
        }
    }

    fun deleteProfile(p: Profile) {
        profileStore.remove(p.id)
        _state.value = _state.value.copy(
            profiles = profileStore.all(),
            active = if (_state.value.active?.id == p.id) null else _state.value.active,
            screen = Screen.Profiles
        )
    }

    fun refresh() = _state.value.active?.let { openProfile(it) }

    fun loadEpg() {
        val p = _state.value.active ?: return
        viewModelScope.launch {
            val e = EpgRepository.load(p)
            if (e.isNotEmpty()) _state.value = _state.value.copy(epg = e)
        }
    }

    fun nowPlaying(channelId: String?): Programme? = EpgRepository.now(_state.value.epg, channelId)
    fun nextProgramme(channelId: String?): Programme? = EpgRepository.next(_state.value.epg, channelId)

    // ---------- Navegación ----------

    fun go(screen: Screen) {
        _state.value = _state.value.copy(screen = screen, selectedCategory = null, error = null)
    }

    fun back() {
        val s = _state.value
        _state.value = when {
            s.screen is Screen.Live && s.playerFullscreen -> s.copy(playerFullscreen = false)
            s.screen is Screen.Live -> s.copy(screen = Screen.Browse(Section.LIVE))
            s.screen is Screen.SeriesDetail -> s.copy(screen = Screen.Browse(Section.SERIES))
            s.screen is Screen.Play -> s.copy(screen = playOrigin)
            s.screen is Screen.Browse || s.screen == Screen.Search || s.screen == Screen.Settings ->
                s.copy(screen = Screen.Dashboard)
            s.screen == Screen.Dashboard -> s.copy(screen = Screen.Profiles)
            else -> s
        }
    }

    fun selectCategory(id: String?) {
        _state.value = _state.value.copy(selectedCategory = id)
    }

    fun setQuery(q: String) {
        _state.value = _state.value.copy(query = q)
    }

    // ---------- Favoritos ----------

    fun toggleFavorite(key: String) {
        val p = _state.value.active ?: return
        val added = userData.toggleFavorite(p.id, key)
        _state.value = _state.value.copy(
            favorites = userData.favorites(p.id),
            toast = if (added) "Añadido a favoritos" else "Quitado de favoritos"
        )
    }

    fun clearToast() { _state.value = _state.value.copy(toast = null) }

    // ---------- Reproducción ----------

    fun playLive(ch: Stream) {
        val p = _state.value.active ?: return
        userData.saveLastChannel(p.id, ch.streamId)
        // Lista de la misma categoría del canal, para poder cambiar desde el reproductor
        val list = _state.value.catalog.live
            .filter { ch.categoryId == null || it.categoryId == ch.categoryId }
            .ifEmpty { _state.value.catalog.live }
        val idx = list.indexOfFirst { it.streamId == ch.streamId }.coerceAtLeast(0)
        _state.value = _state.value.copy(
            screen = Screen.Live, livePlaylist = list, liveIndex = idx,
            playerFullscreen = false, error = null
        )
    }

    fun selectLiveIndex(i: Int) {
        val list = _state.value.livePlaylist
        if (i in list.indices) {
            _state.value = _state.value.copy(liveIndex = i)
            _state.value.active?.let { p ->
                userData.saveLastChannel(p.id, list[i].streamId)
            }
        }
    }

    fun setFullscreen(full: Boolean) {
        _state.value = _state.value.copy(playerFullscreen = full)
    }

    /** URL del canal que se está viendo en directo. */
    fun currentLiveUrl(): String? {
        val p = _state.value.active ?: return null
        val list = _state.value.livePlaylist
        val ch = list.getOrNull(_state.value.liveIndex) ?: return null
        return StreamUrl.live(p, ch.streamId, settings.streamFormat)
    }

    fun openSeries(item: SeriesItem) {
        _state.value = _state.value.copy(
            screen = Screen.SeriesDetail(item.seriesId, item.name ?: "Serie"),
            seriesSeasons = emptyList(), seriesLoadingInfo = true
        )
        val p = _state.value.active ?: return
        viewModelScope.launch {
            val groups = CatalogRepository.seriesEpisodes(p, item.seriesId)
            _state.value = _state.value.copy(seriesSeasons = groups, seriesLoadingInfo = false)
        }
    }

    fun playEpisode(ep: EpisodeItem) {
        val p = _state.value.active ?: return
        playOrigin = _state.value.screen as? Screen.SeriesDetail ?: Screen.Dashboard
        go(Screen.Play(
            StreamUrl.series(p, ep.id, ep.ext),
            ep.title ?: "Episodio", "episode:${ep.id}"
        ))
    }

    fun playMovie(m: Stream) {
        val p = _state.value.active ?: return
        playOrigin = Screen.Browse(Section.MOVIES)
        go(Screen.Play(
            StreamUrl.movie(p, m.streamId, m.ext),
            m.name ?: "Película", "movie:${m.streamId}"
        ))
    }

    fun savePosition(itemKey: String, ms: Long) {
        val p = _state.value.active ?: return
        if (ms > 10_000) userData.saveResume(p.id, itemKey, ms)
    }

    fun resumeOf(itemKey: String): Long =
        _state.value.active?.let { userData.resume(it.id, itemKey) } ?: 0L

    fun clearLocalData() {
        userData.clearAll()
        catalogCache.clear()
        _state.value = _state.value.copy(favorites = emptySet(), toast = "Datos locales borrados")
    }
}
