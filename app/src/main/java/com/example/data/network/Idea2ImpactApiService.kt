package com.example.data.network

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface Idea2ImpactApiService {

    @POST("auth/login")
    suspend fun login(@Body body: RequestBody): Response<ResponseBody>

    @POST("auth/register")
    suspend fun register(@Body body: RequestBody): Response<ResponseBody>

    @POST("auth/firebase-exchange")
    suspend fun exchangeFirebaseToken(@Body body: RequestBody): Response<ResponseBody>

    @POST("auth/firebase-custom-token")
    suspend fun requestFirebaseCustomToken(): Response<ResponseBody>

    @GET("auth/me")
    suspend fun getMe(): Response<ResponseBody>

    @GET("innovations")
    suspend fun getInnovations(
        @Query("search") search: String = "",
        @Query("category") category: String = "",
        @Query("stage") stage: String = ""
    ): Response<ResponseBody>

    @GET("innovations/{id}")
    suspend fun getInnovationDetail(
        @Path("id") id: String
    ): Response<ResponseBody>

    @POST("innovations/{id}/like")
    suspend fun toggleLike(
        @Path("id") id: String
    ): Response<ResponseBody>

    @POST("innovations/{id}/favourite")
    suspend fun toggleFavourite(
        @Path("id") id: String
    ): Response<ResponseBody>

    @POST("innovations/{id}/comments")
    suspend fun postComment(
        @Path("id") id: String,
        @Body body: RequestBody
    ): Response<ResponseBody>

    @POST("innovations/{id}/proposals")
    suspend fun submitProposal(
        @Path("id") id: String,
        @Body body: RequestBody
    ): Response<ResponseBody>

    @POST("innovations/{id}/pitch-deck")
    suspend fun generatePitchDeck(
        @Path("id") id: String
    ): Response<ResponseBody>

    @GET("categories")
    suspend fun getCategories(): Response<ResponseBody>

    @GET("talents")
    suspend fun getTalents(): Response<ResponseBody>

    @GET("dashboard/stats")
    suspend fun getDashboardStats(): Response<ResponseBody>

    @POST("ai/chat")
    suspend fun aiChat(@Body body: RequestBody): Response<ResponseBody>
}
