package com.miplayer.tv.data

import com.google.gson.annotations.SerializedName

/** Perfil de conexión guardado por el usuario. */
data class Profile(
    val id: String,              // username + host, identifica el perfil
    val name: String,
    val host: String,
    val username: String,
    val password: String,
    val isM3u: Boolean = false,
    val m3uUrl: String = ""
)

data class LoginResponse(
    @SerializedName("user_info") val userInfo: UserInfo?,
    @SerializedName("server_info") val serverInfo: ServerInfo?
)

data class UserInfo(
    val username: String?, val password: String?, val message: String?,
    val auth: Int = 0, val status: String?,
    @SerializedName("exp_date") val expDate: String?,
    @SerializedName("is_trial") val isTrial: String?,
    @SerializedName("active_cons") val activeCons: String?,
    @SerializedName("max_connections") val maxConnections: String?
)

data class ServerInfo(
    val url: String?, val port: String?,
    @SerializedName("server_protocol") val protocol: String?
)

data class Category(
    @SerializedName("category_id") val categoryId: String?,
    @SerializedName("category_name") val categoryName: String?
)

/** Canal de directo o película, según el endpoint del que venga. */
data class Stream(
    val num: Int = 0,
    val name: String?,
    @SerializedName("stream_id") val streamId: Int = 0,
    @SerializedName("stream_icon") val icon: String?,
    @SerializedName("epg_channel_id") val epgId: String?,
    @SerializedName("category_id") val categoryId: String?,
    @SerializedName("container_extension") val ext: String?,
    @SerializedName("tv_archive") val tvArchive: Int = 0,
    val rating: String? = null,
    val added: String? = null
)

/** Serie. */
data class SeriesItem(
    val num: Int = 0,
    val name: String?,
    @SerializedName("series_id") val seriesId: Int = 0,
    val cover: String?,
    val plot: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    val rating: String?,
    @SerializedName("category_id") val categoryId: String?
)

/** Secciones del menú principal. */
enum class Section(val title: String) {
    LIVE("Canales"), MOVIES("Películas"), SERIES("Series"),
    FAVORITES("Favoritos"), SEARCH("Buscar"), SETTINGS("Ajustes")
}

/** Elemento genérico para pintar en la rejilla, venga de donde venga. */
data class MediaCard(
    val id: String,
    val title: String,
    val num: Int = 0,
    val image: String?,
    val section: Section,
    val streamId: Int = 0,
    val seriesId: Int = 0,
    val ext: String? = null,
    val categoryId: String? = null,
    val epgId: String? = null
)

/** Episodio de una serie (de get_series_info). */
data class EpisodeItem(
    val id: String = "",
    @SerializedName("episode_num") val episodeNum: Int = 0,
    val title: String? = null,
    @SerializedName("container_extension") val ext: String? = null,
    val season: Int = 0
)

/** Temporada con sus episodios ya agrupados. */
data class SeasonGroup(val season: Int, val episodes: List<EpisodeItem>)

/** Un programa de la guía EPG. */
data class Programme(
    val channelId: String,
    val startMs: Long,
    val stopMs: Long,
    val title: String
)
