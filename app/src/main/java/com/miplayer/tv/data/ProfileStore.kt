package com.miplayer.tv.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson

/**
 * Perfiles CIFRADOS con el almacén seguro de Android.
 * Las apps analizadas guardaban usuario y contraseña en texto plano.
 */
class ProfileStore(context: Context) {
    private val gson = Gson()
    private val prefs = EncryptedSharedPreferences.create(
        context, "profiles_secure",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun all(): List<Profile> =
        prefs.getString("list", null)?.let {
            gson.fromJson(it, Array<Profile>::class.java).toList()
        } ?: emptyList()

    fun save(list: List<Profile>) =
        prefs.edit().putString("list", gson.toJson(list)).apply()

    fun add(p: Profile) {
        val list = all().filterNot { it.id == p.id } + p
        save(list)
        setActive(p.id)
    }

    fun remove(id: String) {
        save(all().filterNot { it.id == id })
        if (activeId() == id) prefs.edit().remove("active").apply()
    }

    fun setActive(id: String) = prefs.edit().putString("active", id).apply()
    fun activeId(): String? = prefs.getString("active", null)
    fun active(): Profile? = activeId()?.let { id -> all().firstOrNull { it.id == id } }
}
