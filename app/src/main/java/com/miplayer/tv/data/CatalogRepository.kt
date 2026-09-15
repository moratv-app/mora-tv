package com.miplayer.tv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/** Catálogo completo de un perfil. */
data class Catalog(
    val userInfo: UserInfo? = null,
    val liveCategories: List<Category> = emptyList(),
    val live: List<Stream> = emptyList(),
    val movieCategories: List<Category> = emptyList(),
    val movies: List<Stream> = emptyList(),
    val seriesCategories: List<Category> = emptyList(),
    val series: List<SeriesItem> = emptyList()
)

object CatalogRepository {

    /**
     * Descarga el catálogo. A diferencia de la app analizada, que encadenaba
     * 6 peticiones anidadas una detrás de otra, aquí van EN PARALELO.
     */
    suspend fun load(p: Profile): Catalog = withContext(Dispatchers.IO) {
        val base = Api.endpoint(p.host)
        val u = p.username; val pw = p.password

        coroutineScope {
            val login = async { runCatching { Api.service.login(base, u, pw) }.getOrNull() }
            val liveCat = async { runCatching { Api.service.liveCategories(base, u, pw) }.getOrDefault(emptyList()) }
            val live = async { runCatching { Api.service.liveStreams(base, u, pw) }.getOrDefault(emptyList()) }
            val vodCat = async { runCatching { Api.service.vodCategories(base, u, pw) }.getOrDefault(emptyList()) }
            val vod = async { runCatching { Api.service.vodStreams(base, u, pw) }.getOrDefault(emptyList()) }
            val serCat = async { runCatching { Api.service.seriesCategories(base, u, pw) }.getOrDefault(emptyList()) }
            val ser = async { runCatching { Api.service.series(base, u, pw) }.getOrDefault(emptyList()) }

            Catalog(
                userInfo = login.await()?.userInfo,
                liveCategories = liveCat.await(), live = live.await(),
                movieCategories = vodCat.await(), movies = vod.await(),
                seriesCategories = serCat.await(), series = ser.await()
            )
        }
    }

    /** Valida credenciales y devuelve un mensaje de error, o null si todo va bien. */
    suspend fun validate(p: Profile): String? = withContext(Dispatchers.IO) {
        try {
            val r = Api.service.login(Api.endpoint(p.host), p.username, p.password)
            val info = r.userInfo ?: return@withContext "Respuesta inválida del servidor"
            when {
                info.auth == 0 -> "Usuario o contraseña incorrectos"
                info.status == "Expired" -> "La cuenta ha caducado"
                info.status == "Disabled" -> "La cuenta está deshabilitada"
                info.status == "Banned" -> "La cuenta está bloqueada"
                info.status != "Active" -> info.message ?: "La cuenta no está activa"
                else -> null
            }
        } catch (e: Exception) {
            "No se pudo conectar: ${e.message ?: "error de red"}"
        }
    }

    private val http = OkHttpClient.Builder()
        .followSslRedirects(false).retryOnConnectionFailure(true).build()

    /** Descarga los episodios de una serie y los agrupa por temporada. */
    suspend fun seriesEpisodes(p: Profile, seriesId: Int): List<SeasonGroup> = withContext(Dispatchers.IO) {
        try {
            val url = "${Api.endpoint(p.host)}?username=${p.username}&password=${p.password}" +
                      "&action=get_series_info&series_id=$seriesId"
            val body = http.newCall(Request.Builder().url(url).build()).execute().use {
                if (!it.isSuccessful) return@withContext emptyList<SeasonGroup>()
                it.body?.string() ?: return@withContext emptyList<SeasonGroup>()
            }
            val root = JSONObject(body)
            val eps = root.optJSONObject("episodes") ?: return@withContext emptyList()
            val groups = mutableListOf<SeasonGroup>()
            val keys = eps.keys().asSequence().toList().sortedBy { it.toIntOrNull() ?: 0 }
            for (k in keys) {
                val arr = eps.optJSONArray(k) ?: continue
                val list = mutableListOf<EpisodeItem>()
                for (i in 0 until arr.length()) {
                    val e = arr.getJSONObject(i)
                    list.add(EpisodeItem(
                        id = e.optString("id"),
                        episodeNum = e.optInt("episode_num"),
                        title = e.optString("title").ifBlank { "Episodio ${e.optInt("episode_num")}" },
                        ext = e.optString("container_extension").ifBlank { "mp4" },
                        season = k.toIntOrNull() ?: 0
                    ))
                }
                if (list.isNotEmpty()) groups.add(SeasonGroup(k.toIntOrNull() ?: 0, list))
            }
            groups
        } catch (e: Exception) { emptyList() }
    }

    private fun swapScheme(host: String): String =
        if (host.startsWith("https://")) "http://" + host.removePrefix("https://")
        else "https://" + host.removePrefix("http://")

    /**
     * Valida el perfil probando el host tal cual y, si el fallo parece de cifrado
     * (TLS/SSL) o de tráfico, reintenta con el esquema contrario (http<->https).
     * Devuelve el perfil que funciona y un error (null si conectó).
     */
    suspend fun resolveHost(p: Profile): Pair<Profile, String?> {
        val e1 = validate(p)
        if (e1 == null) return p to null
        val looksTls = listOf("TLS", "SSL", "handshake", "CLEARTEXT", "trust anchor", "cert")
            .any { e1.contains(it, ignoreCase = true) }
        if (looksTls) {
            val alt = p.copy(host = swapScheme(p.host))
            val e2 = validate(alt)
            if (e2 == null) return alt to null
        }
        return p to e1
    }
}
