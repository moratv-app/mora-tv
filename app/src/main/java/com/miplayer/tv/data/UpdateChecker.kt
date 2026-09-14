package com.miplayer.tv.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.miplayer.tv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

data class UpdateInfo(val versionName: String, val versionCode: Int, val apkUrl: String, val notes: String)

/**
 * Comprueba si hay una versión nueva publicada en GitHub Releases y la instala.
 * Apunta a TU repositorio por HTTPS: es la fuente de confianza, no un host ajeno.
 * Convención: cada release se etiqueta "v<versionCode>" y adjunta el APK.
 */
object UpdateChecker {
    // Se rellena al crear el repo:
    const val OWNER = "9ness"
    const val REPO = "mora-tv"

    private val client = OkHttpClient()

    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("https://api.github.com/repos/$OWNER/$REPO/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .build()
            val body = client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@withContext null
                r.body?.string() ?: return@withContext null
            }
            val json = JSONObject(body)
            val tag = json.optString("tag_name").removePrefix("v")   // "v5" -> 5
            val code = tag.toIntOrNull() ?: return@withContext null
            if (code <= BuildConfig.VERSION_CODE) return@withContext null

            val assets = json.getJSONArray("assets")
            var apk: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk")) {
                    apk = a.getString("browser_download_url"); break
                }
            }
            apk ?: return@withContext null
            UpdateInfo(
                versionName = json.optString("name").ifBlank { "v$code" },
                versionCode = code,
                apkUrl = apk,
                notes = json.optString("body").take(300)
            )
        } catch (e: Exception) { null }
    }

    /** Descarga el APK a la caché y lanza el instalador del sistema. */
    suspend fun downloadAndInstall(context: Context, url: String) = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "update.apk")
        client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            r.body?.byteStream()?.use { input ->
                file.outputStream().use { input.copyTo(it) }
            }
        }
        val uri: Uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
