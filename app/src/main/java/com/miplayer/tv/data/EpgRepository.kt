package com.miplayer.tv.data

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.util.concurrent.TimeUnit

object EpgRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Descarga y parsea el XMLTV. Devuelve mapa: id de canal EPG -> programas ordenados. */
    suspend fun load(p: Profile): Map<String, List<Programme>> = withContext(Dispatchers.IO) {
        try {
            val url = "${p.host.trimEnd('/')}/xmltv.php?username=${p.username}&password=${p.password}"
            client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) return@withContext emptyMap()
                r.body?.byteStream()?.use { parse(it) } ?: emptyMap()
            }
        } catch (e: Exception) { emptyMap() }
    }

    private fun parse(input: InputStream): Map<String, List<Programme>> {
        val map = HashMap<String, MutableList<Programme>>()
        val parser = Xml.newPullParser()
        parser.setInput(input, null)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "programme") {
                val channel = parser.getAttributeValue(null, "channel") ?: ""
                val start = parseTime(parser.getAttributeValue(null, "start"))
                val stop = parseTime(parser.getAttributeValue(null, "stop"))
                var title = ""
                // recorrer hasta el fin de <programme> buscando <title>
                var e = parser.next()
                while (!(e == XmlPullParser.END_TAG && parser.name == "programme")) {
                    if (e == XmlPullParser.START_TAG && parser.name == "title") {
                        title = parser.nextText()
                    }
                    if (e == XmlPullParser.END_DOCUMENT) break
                    e = parser.next()
                }
                if (channel.isNotEmpty() && start > 0) {
                    map.getOrPut(channel) { mutableListOf() }.add(Programme(channel, start, stop, title))
                }
            }
            event = parser.next()
        }
        map.values.forEach { it.sortBy { p -> p.startMs } }
        return map
    }

    /** "20260914120000 +0000" -> epoch millis */
    private fun parseTime(s: String?): Long {
        if (s.isNullOrBlank()) return 0
        return try {
            val fmt = java.text.SimpleDateFormat("yyyyMMddHHmmss Z", java.util.Locale.US)
            (fmt.parse(s.trim())?.time) ?: run {
                val fmt2 = java.text.SimpleDateFormat("yyyyMMddHHmmss", java.util.Locale.US)
                fmt2.timeZone = java.util.TimeZone.getTimeZone("UTC")
                fmt2.parse(s.trim().substringBefore(" "))?.time ?: 0
            }
        } catch (e: Exception) { 0 }
    }

    /** Programa que se emite ahora en ese canal. */
    fun now(epg: Map<String, List<Programme>>, channelId: String?): Programme? {
        if (channelId.isNullOrBlank()) return null
        val now = System.currentTimeMillis()
        return epg[channelId]?.firstOrNull { now in it.startMs until it.stopMs }
    }

    fun next(epg: Map<String, List<Programme>>, channelId: String?): Programme? {
        if (channelId.isNullOrBlank()) return null
        val now = System.currentTimeMillis()
        return epg[channelId]?.firstOrNull { it.startMs > now }
    }
}
