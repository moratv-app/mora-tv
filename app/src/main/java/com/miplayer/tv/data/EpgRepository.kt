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

    /** Guía "ahora/después" de un canal concreto vía player_api get_short_epg (fiable y ligero). */
    suspend fun shortEpg(p: Profile, streamId: Int): List<Programme> = withContext(Dispatchers.IO) {
        try {
            val url = "${p.host.trimEnd('/')}/player_api.php?username=${p.username}&password=${p.password}" +
                      "&action=get_short_epg&stream_id=$streamId&limit=6"
            val body = client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) return@withContext emptyList<Programme>()
                r.body?.string() ?: return@withContext emptyList<Programme>()
            }
            val arr = org.json.JSONObject(body).optJSONArray("epg_listings") ?: return@withContext emptyList()
            val out = mutableListOf<Programme>()
            for (i in 0 until arr.length()) {
                val e = arr.getJSONObject(i)
                val title = decodeMaybeBase64(e.optString("title"))
                val start = e.optString("start_timestamp").toLongOrNull()?.times(1000) ?: 0L
                val stop = e.optString("stop_timestamp").toLongOrNull()?.times(1000) ?: 0L
                if (title.isNotBlank()) out.add(Programme("", start, stop, title))
            }
            out
        } catch (e: Exception) { emptyList() }
    }

    /** Los títulos de get_short_epg vienen en Base64; si no lo son, se devuelve tal cual. */
    private fun decodeMaybeBase64(s: String): String {
        if (s.isBlank()) return ""
        return try {
            val bytes = android.util.Base64.decode(s, android.util.Base64.DEFAULT)
            val txt = String(bytes, Charsets.UTF_8)
            if (txt.all { it.code in 9..126 || it.code > 160 }) txt else s
        } catch (e: Exception) { s }
    }
}
