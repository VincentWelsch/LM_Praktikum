package com.example.praktikum1

data class SensorSample (
    val timestamp: Long,
    val accelX: Float, val accelY: Float, val accelZ: Float,
    val gyroX: Float, val gyroY: Float, val gyroZ: Float,
    val magnetX: Float, val magnetY: Float, val magnetZ: Float,
    val lat: Double?, val lon: Double?
)