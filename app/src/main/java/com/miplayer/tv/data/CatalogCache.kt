package com.miplayer.tv.data

import android.content.Context
import com.google.gson.Gson
import java.io.File

/**
 * Guarda el catálogo descargado en el dispositivo para que la app abra al instante.
 * Se guarda un fichero JSON por perfil. Al abrir se muestra la copia guardada y,
 * en paralelo, se refresca desde el servidor.
 */
class CatalogCache(context: Context) {
    private val gson = Gson()
    private val dir = File(context.filesDir, "catalog").apply { mkdirs() }

    private fun fileFor(profileId: String): File {
        val safe = profileId.hashCode().toString()
        return File(dir, "cat_$safe.json")
    }

    fun load(profileId: String): Catalog? = try {
        val f = fileFor(profileId)
        if (f.exists()) gson.fromJson(f.readText(), Catalog::class.java) else null
    } catch (e: Exception) { null }

    fun save(profileId: String, catalog: Catalog) {
        try { fileFor(profileId).writeText(gson.toJson(catalog)) } catch (e: Exception) {}
    }

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }
}
