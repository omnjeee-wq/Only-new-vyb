package com.example.data.ai

import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.Part
import retrofit2.http.POST

interface VybeAiApi {
    @POST("v1/assistant") suspend fun assistant(@Body request: AiAssistantRequest): AiResponse
    @POST("v1/mood-search") suspend fun moodSearch(@Body request: AiMoodSearchRequest): AiResponse
    @POST("v1/lyrics/insights") suspend fun lyricsInsights(@Body request: AiLyricsRequest): AiResponse
    @POST("v1/recommendations") suspend fun recommendations(@Body request: AiRecommendationRequest): AiResponse
    @POST("v1/dj/sequence") suspend fun djSequence(@Body request: AiDjRequest): AiResponse
    @Multipart @POST("v1/analyze") suspend fun analyzeAudio(@Part file: MultipartBody.Part): AiResponse

    companion object {
        fun create(baseUrl: String): VybeAiApi {
            val client = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(150, TimeUnit.SECONDS)
                .writeTimeout(150, TimeUnit.SECONDS)
                .callTimeout(180, TimeUnit.SECONDS)
                .build()
            return Retrofit.Builder()
            .baseUrl(if (baseUrl.endsWith('/')) baseUrl else "$baseUrl/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(VybeAiApi::class.java)
        }
    }
}
