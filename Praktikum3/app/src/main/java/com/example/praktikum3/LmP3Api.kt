package com.example.praktikum3

import com.example.praktikum3.FixReport
import com.example.praktikum3.ServerResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface LmP3Api {
    @POST("run/report")
    fun reportFix(@Body fix: FixReport): Call<ServerResponse>
}
