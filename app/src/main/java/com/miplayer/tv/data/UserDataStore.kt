package com.miplayer.tv.data

import android.content.Context

/**
 * Favoritos y posición de reproducción, por perfil.
 * Se guardan como CLAVES en un conjunto, no duplicando filas del catálogo
 * (que es el antipatrón que usaban las apps analizadas).
 */
class UserDataStore(context: Context) {
    private val prefs = context.getSharedPreferences("userdata", Context.MODE_PRIVATE)

    private fun favKey(profileId: String) = "fav_$profileId"

    fun favorites(profileId: String): Set<String> =
        prefs.getStringSet(favKey(profileId), emptySet()) ?: emptySet()

    fun isFavorite(profileId: String, item: String) = item in favorites(profileId)

    /** Devuelve el nuevo estado. */
    fun toggleFavorite(profileId: String, item: String): Boolean {
        val set = favorites(profileId).toMutableSet()
        val added = if (item in set) { set.remove(item); false } else { set.add(item); true }
        prefs.edit().putStringSet(favKey(profileId), set).apply()
        return added
    }

    /** Posición en milisegundos para reanudar. */
    fun saveResume(profileId: String, item: String, positionMs: Long) =
        prefs.edit().putLong("res_${profileId}_$item", positionMs).apply()

    fun resume(profileId: String, item: String): Long =
        prefs.getLong("res_${profileId}_$item", 0L)

    /** Último canal visto, para reanudar al abrir. */
    fun saveLastChannel(profileId: String, streamId: Int) =
        prefs.edit().putInt("last_$profileId", streamId).apply()

    fun lastChannel(profileId: String): Int = prefs.getInt("last_$profileId", 0)

    fun clearAll() = prefs.edit().clear().apply()
}

/** Ajustes generales de la app. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var streamFormat: String
        get() = prefs.getString("format", ".ts") ?: ".ts"
        set(v) = prefs.edit().putString("format", v).apply()

    var gridColumns: Int
        get() = prefs.getInt("cols", 5)
        set(v) = prefs.edit().putInt("cols", v).apply()
}
