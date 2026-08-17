package com.uddoktahisab.app.data.remote

import com.uddoktahisab.app.data.model.*
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

interface ApiService {
    @POST suspend fun login(@Url url: String, @Body body: ApiRequest): ApiResponse<LoginData>
    @POST suspend fun bootstrap(@Url url: String, @Body body: ApiRequest): ApiResponse<BootstrapData>
    @POST suspend fun action(@Url url: String, @Body body: ApiRequest): ApiResponse<Map<String, Any?>>
}
