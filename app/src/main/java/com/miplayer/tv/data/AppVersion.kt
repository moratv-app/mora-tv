package com.miplayer.tv.data

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import com.miplayer.tv.BuildConfig

/** Lee la versión realmente instalada en el aparato (no la del proceso en marcha). */
object AppVersion {
    fun name(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: BuildConfig.VERSION_NAME
    } catch (e: Exception) { BuildConfig.VERSION_NAME }

    fun code(context: Context): Long = try {
        PackageInfoCompat.getLongVersionCode(
            context.packageManager.getPackageInfo(context.packageName, 0)
        )
    } catch (e: Exception) { BuildConfig.VERSION_CODE.toLong() }
}
