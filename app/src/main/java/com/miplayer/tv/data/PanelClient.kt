package com.miplayer.tv.data

import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class PanelUser(
    val id: Int = 0,
    val name: String = "",
    val username: String = "",
    val role: String = "user"
)

data class PanelProfile(
    val name: String = "",
    val host: String = "",
    val username: String = "",
    val password: String = "",
    val isM3u: Boolean = false,
    val m3uUrl: String = ""
)

data class PanelSession(
    val token: String,
    val user: PanelUser,
    val profile: PanelProfile?
)

object PanelClient {
    private val gson = Gson()
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    private val json = "application/json".toMediaType()

    fun normalize(url: String): String {
        var s = url.trim().trimEnd('/')
        if (s.isNotBlank() && !s.startsWith("http://") && !s.startsWith("https://")) s = "http://$s"
        return s
    }

    fun login(panel: String, username: String, password: String): PanelSession {
        val body = gson.toJson(mapOf("username" to username, "password" to password))
        return post(panel, "/api/login", body, null)
    }

    fun me(panel: String, token: String): PanelSession {
        val raw = call(panel, "/api/me", token, "GET", null)
        val me = gson.fromJson(raw, MeBody::class.java)
        return PanelSession(token, me.user, me.profile)
    }

    private data class MeBody(val user: PanelUser, val profile: PanelProfile?)

    private inline fun <reified T> post(panel: String, path: String, body: String, token: String?): T {
        return gson.fromJson(call(panel, path, token, "POST", body), T::class.java)
    }

    private fun call(panel: String, path: String, token: String?, method: String, body: String?): String {
        val reqBody = if (method == "GET") null else body?.toRequestBody(json)
        val req = Request.Builder()
            .url(normalize(panel) + path)
            .method(method, reqBody)
            .apply { if (!token.isNullOrBlank()) header("Authorization", "Bearer $token") }
            .build()
        http.newCall(req).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                val msg = Regex("\"detail\"\\s*:\\s*\"([^\"]+)\"").find(text)?.groupValues?.get(1)
                    ?: "Error ${res.code}"
                error(msg)
            }
            return text
        }
    }
}
