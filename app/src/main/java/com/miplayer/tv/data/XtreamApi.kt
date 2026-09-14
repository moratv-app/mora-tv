package com.miplayer.tv.data

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

interface XtreamService {
    @GET suspend fun login(@Url base: String,
        @Query("username") u: String, @Query("password") p: String): LoginResponse

    @GET suspend fun liveCategories(@Url base: String,
        @Query("username") u: String, @Query("password") p: String,
        @Query("action") a: String = "get_live_categories"): List<Category>

    @GET suspend fun vodCategories(@Url base: String,
        @Query("username") u: String, @Query("password") p: String,
        @Query("action") a: String = "get_vod_categories"): List<Category>

    @GET suspend fun seriesCategories(@Url base: String,
        @Query("username") u: String, @Query("password") p: String,
        @Query("action") a: String = "get_series_categories"): List<Category>

    @GET suspend fun liveStreams(@Url base: String,
        @Query("username") u: String, @Query("password") p: String,
        @Query("action") a: String = "get_live_streams"): List<Stream>

    @GET suspend fun vodStreams(@Url base: String,
        @Query("username") u: String, @Query("password") p: String,
        @Query("action") a: String = "get_vod_streams"): List<Stream>

    @GET suspend fun series(@Url base: String,
        @Query("username") u: String, @Query("password") p: String,
        @Query("action") a: String = "get_series"): List<SeriesItem>
}

object Api {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("http://localhost/")   // ignorado: usamos @Url absolutas
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val service: XtreamService = retrofit.create(XtreamService::class.java)

    fun endpoint(host: String) = "${host.trimEnd('/')}/player_api.php"
}
