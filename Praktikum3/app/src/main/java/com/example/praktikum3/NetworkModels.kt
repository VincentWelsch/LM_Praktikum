package com.example.praktikum3

import com.google.gson.annotations.SerializedName

data class FixReport(
    @SerializedName("runId")   val runId: String,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("latitude")  val latitude: Double,
    @SerializedName("altitude")  val altitude: Double,
    @SerializedName("timestamp") val timestamp: Long? = null
)

data class ServerResponse(
    @SerializedName("success") val success: Int,
    @SerializedName("message") val message: String
)
