package com.miplayer.tv.data

import android.content.Context
import com.miplayer.tv.BuildConfig

class ProSessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("pro_session", Context.MODE_PRIVATE)

    fun panelUrl(): String {
        val saved = prefs.getString("panel", "").orEmpty()
        return saved.ifBlank { BuildConfig.PANEL_URL }
    }

    fun token(): String = prefs.getString("token", "").orEmpty()
    fun appUser(): String = prefs.getString("user", "").orEmpty()

    fun save(panel: String, token: String, user: String) {
        prefs.edit()
            .putString("panel", PanelClient.normalize(panel))
            .putString("token", token)
            .putString("user", user)
            .apply()
    }

    fun clear() {
        prefs.edit().remove("token").remove("user").apply()
    }
}
