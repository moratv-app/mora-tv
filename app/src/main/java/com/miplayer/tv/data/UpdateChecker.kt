package com.miplayer.tv.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.miplayer.tv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

data class UpdateInfo(val versionName: String, val versionCode: Int, val apkUrl: String, val notes: String)

object UpdateChecker {
    const val OWNER = "moratv-app"
    const val REPO = "mora-tv"

    private val client = OkHttpClient()

    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("https://api.github.com/repos/$OWNER/$REPO/releases/latest")
                .header("Accept", "application/vnd.github+json").build()
            val body = client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@withContext null
                r.body?.string() ?: return@withContext null
            }
            val json = JSONObject(body)
            val code = json.optString("tag_name").removePrefix("v").toIntOrNull() ?: return@withContext null
            if (code <= BuildConfig.VERSION_CODE) return@withContext null
            val assets = json.getJSONArray("assets")
            var apk: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk")) { apk = a.getString("browser_download_url"); break }
            }
            apk ?: return@withContext null
            UpdateInfo(json.optString("name").ifBlank { "v$code" }, code, apk, json.optString("body").take(300))
        } catch (e: Exception) { null }
    }

    /** ¿Puede la app instalar APKs? (permiso de "orígenes desconocidos") */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Abre los ajustes para que el usuario autorice instalar desde esta app. */
    fun openInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
        }
    }

    /** Descarga el APK a la caché. Devuelve el fichero o null si falla. */
    suspend fun download(context: Context, url: String): File? = withContext(Dispatchers.IO) {
        try {
            val file = File(context.cacheDir, "update.apk")
            client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) return@withContext null
                r.body?.byteStream()?.use { input -> file.outputStream().use { input.copyTo(it) } }
                    ?: return@withContext null
            }
            file
        } catch (e: Exception) { null }
    }

    /** Lanza el instalador del sistema con el APK descargado. */
    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
