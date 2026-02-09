package com.example.praktikum3

enum class ReportingStrategies(val id: Int, val desc: String) {
    NONE(0, "none"),
    PERIODIC(1, "periodic (fixed time)"),
    DISTANCE_BASED(2, "distance-based (threshold crossed)"),
    MANAGED_PERIODIC(3, "managed (min time to cross threshold)"),
    MANAGED_MOVEMENT(4, "managed (movement detected)"),

    // optional
    MANAGED_PLUS_MOVEMENT(5, "managed+ (movement detected and threshold crossed)"),
    MANAGED_PLUS_PERIODIC(6, "managed+ (estimated time to cross threshold)"),
}