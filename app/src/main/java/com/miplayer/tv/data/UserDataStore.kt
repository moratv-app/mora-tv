package com.miplayer.tv.data

import android.content.Context

/**
 * Favoritos y posición de reproducción, por perfil.
 * Se guardan como CLAVES en un conjunto, no duplicando filas del catálogo
 * (que es el antipatrón que usaban las apps analizadas).
 */
class UserDataStore(context: Context) {
    private val prefs = context.getSharedPreferences("userdata", Context.MODE_PRIVATE)
    private val gson = com.google.gson.Gson()

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

    // ---------- Listas personalizadas (ej. "Futbol") ----------
    private fun listsKey(profileId: String) = "lists_$profileId"

    fun customLists(profileId: String): Map<String, Set<String>> {
        val json = prefs.getString(listsKey(profileId), null) ?: return linkedMapOf()
        return try {
            val type = object : com.google.gson.reflect.TypeToken<LinkedHashMap<String, MutableSet<String>>>() {}.type
            gson.fromJson(json, type) ?: linkedMapOf()
        } catch (e: Exception) { linkedMapOf() }
    }

    private fun saveLists(profileId: String, map: Map<String, Set<String>>) =
        prefs.edit().putString(listsKey(profileId), gson.toJson(map)).apply()

    fun createList(profileId: String, name: String) {
        val m = LinkedHashMap(customLists(profileId))
        if (!m.containsKey(name)) { m[name] = emptySet(); saveLists(profileId, m) }
    }

    fun deleteList(profileId: String, name: String) {
        val m = LinkedHashMap(customLists(profileId)); m.remove(name); saveLists(profileId, m)
    }

    /** Añade/quita un canal de una lista; crea la lista si no existe. Devuelve si quedó dentro. */
    fun toggleInList(profileId: String, name: String, channelKey: String): Boolean {
        val m = LinkedHashMap<String, MutableSet<String>>()
        customLists(profileId).forEach { (k, v) -> m[k] = v.toMutableSet() }
        val set = m.getOrPut(name) { mutableSetOf() }
        val inside = if (channelKey in set) { set.remove(channelKey); false } else { set.add(channelKey); true }
        saveLists(profileId, m); return inside
    }

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
