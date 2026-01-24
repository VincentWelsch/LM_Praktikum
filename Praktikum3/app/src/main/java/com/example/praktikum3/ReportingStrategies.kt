package com.example.praktikum3

enum class ReportingStrategies(val id: Int, val desc: String) {
    NONE(0, "none"),
    PERIODIC(1, "periodic"),
    DISTANCE_BASED(2, "distance-based"),
    MANAGED_PERIODIC(3, "managed (periodic)"),
    MANAGED_MOVEMENT(4, "managed+ (detected movement)");
}