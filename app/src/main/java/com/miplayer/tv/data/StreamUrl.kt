package com.miplayer.tv.data

/**
 * URLs del protocolo Xtream Codes.
 *   Live:      {host}/live/{user}/{pass}/{id}.{ext}
 *   Película:  {host}/movie/{user}/{pass}/{id}.{ext}
 *   Episodio:  {host}/series/{user}/{pass}/{id}.{ext}
 *   Timeshift: {host}/timeshift/{user}/{pass}/{min}/{yyyy-MM-dd:HH-mm}/{id}.ts
 */
object StreamUrl {
    private fun base(p: Profile) = "${p.host.trimEnd('/')}"

    fun live(p: Profile, streamId: Int, format: String = ".ts"): String =
        "${base(p)}/live/${p.username}/${p.password}/$streamId$format"

    fun movie(p: Profile, streamId: Int, ext: String?): String =
        "${base(p)}/movie/${p.username}/${p.password}/$streamId.${ext ?: "mp4"}"

    fun series(p: Profile, episodeId: String, ext: String?): String =
        "${base(p)}/series/${p.username}/${p.password}/$episodeId.${ext ?: "mp4"}"

    /** minutos se manda como entero: la app analizada mandaba decimales y rompía la URL. */
    fun timeshift(p: Profile, minutes: Int, start: String, streamId: Int): String =
        "${base(p)}/timeshift/${p.username}/${p.password}/$minutes/$start/$streamId.ts"
}
