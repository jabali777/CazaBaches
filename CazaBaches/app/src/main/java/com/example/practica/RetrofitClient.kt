package com.example.practica

import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

data class AuthRequest(val email: String, val password: String)
data class AuthResponse(val token: String?, val email: String?, val error: String?)

interface ApiService {
    @POST("api/login")
    suspend fun login(@Body request: AuthRequest): Response<AuthResponse>

    @POST("api/register")
    suspend fun register(@Body request: AuthRequest): Response<AuthResponse>
}

object RetrofitClient {
    const val BASE_URL = "http://192.168.0.23:5000/"

    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}