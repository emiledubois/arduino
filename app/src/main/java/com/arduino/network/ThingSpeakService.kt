package com.arduino.network

import retrofit2.Response
import retrofit2.http.POST
import retrofit2.http.Query

interface ThingSpeakService {

    @POST("update")
    suspend fun enviarDatos(
        @Query("api_key") apiKey: String,
        @Query("field1") field1: String,
        @Query("field2") field2: String
    ): Response<String>
}