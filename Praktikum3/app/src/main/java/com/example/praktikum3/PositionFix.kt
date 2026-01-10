package com.example.praktikum3
import kotlinx.serialization.Serializable

@Serializable
data class PositionFix(
    val latitude: Float,
    val longitude: Float,
    val altitude: Float,
) {
    // equals/hashCode must be overridden
    // https://www.baeldung.com/kotlin/data-class-equals-method
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PositionFix) return false

        if (latitude != other.latitude) return false
        if (longitude != other.longitude) return false
        if (altitude != other.altitude) return false
        return true
    }

    override fun hashCode(): Int {
        var result = latitude.hashCode()
        result = 31 * result + longitude.hashCode()
        result = 31 * result + altitude.hashCode()
        return result
    }
}