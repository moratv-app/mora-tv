package com.miplayer.tv.data

/** Convierte una lista M3U/M3U8 en canales reproducibles. */
object M3uParser {
    data class M3uItem(val name: String, val logo: String?, val group: String?, val url: String)

    fun parse(content: String): List<M3uItem> {
        val items = mutableListOf<M3uItem>()
        var name = ""; var logo: String? = null; var group: String? = null
        content.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF", true) -> {
                    name = line.substringAfterLast(",").trim()
                    logo = attr(line, "tvg-logo")
                    group = attr(line, "group-title")
                }
                line.isNotEmpty() && !line.startsWith("#") -> {
                    items.add(M3uItem(name.ifEmpty { line }, logo, group, line))
                    name = ""; logo = null; group = null
                }
            }
        }
        return items
    }

    private fun attr(line: String, key: String): String? {
        val re = Regex("$key=\"([^\"]*)\"")
        return re.find(line)?.groupValues?.get(1)
    }
}
