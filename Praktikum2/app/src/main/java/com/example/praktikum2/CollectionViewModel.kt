package com.example.praktikum2

import androidx.lifecycle.ViewModel
import kotlin.time.Instant

data class Measurement(
    val longitude: Float,
    val latitude: Float,
    val time: Long,
) {
    // equals/hashCode must be overridden
    // https://www.baeldung.com/kotlin/data-class-equals-method
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is com.example.praktikum2.Measurement) return false

        if (longitude != other.longitude) return false
        if (latitude != other.latitude) return false
        if (time != other.time) return false
        return true
    }

    override fun hashCode(): Int {
        var result = longitude.hashCode()
        result = 31 * result + latitude.hashCode()
        result = 31 * result + time.hashCode()
        return result
    }
}

data class Waypoint(
    val index: Int,
    val time: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is com.example.praktikum2.Waypoint) return false

        if (index != other.index) return false
        if (time != other.time) return false
        return true
    }

    override fun hashCode(): Int {
        var result = index.hashCode()
        result = 31 * result + time.hashCode()
        return result
    }
}

/*
Usage:
- Set groundTruth
- Let measurements be added periodically
- Add waypoints when pressing a button
- Save all in JSON format with storeCollection
 */

class CollectionViewModel: ViewModel() {
    private var groundTruth = mutableListOf<FloatArray>()
    fun setGroundTruth(gnd: MutableList<FloatArray>) {
        groundTruth = gnd
    }

    private var index = 0

    // Allow or disallow adding new data
    private var takesNew = true;
    fun getTakesNew(): Boolean {
        return takesNew
    }
    fun setTakesNew(b: Boolean) {
        takesNew = b
    }

    // Store and add measurements and waypoints
    private val measurements = mutableListOf<Measurement>()
    private val waypoints = mutableListOf<Waypoint>()
    fun addMeasurement(measurement: Measurement) { // existing instance of Measurement
        if (takesNew) measurements.add(measurement)
    }
    fun addMeasurement(longitude: Float, latitude: Float) { // new instance of Measurement
        if (takesNew) measurements.add(Measurement(longitude, latitude, Instant().epochSecond))
    }
    fun addWaypoint(waypoint: Waypoint) {
        if (takesNew) waypoints.add(waypoint)
    }
    fun addWaypoint() { // new instance of Measurement
        if (takesNew) {
            waypoints.add(Waypoint(index, Instant().epochSecond))
            index++
        }
    }

    // Store entire collection with title
    fun storeCollection(title: String) {
        if (title.isNotEmpty()) {
            // TODO: Store groundTruth, measurements, and waypoints in a file called '<title>.json'
            index = 0
        }
    }
    // Load entire collection with title
    fun loadCollectionU(title: String) {
        if (title.isNotEmpty() && isExistingFile(title)) {
            // TODO: Load groundTruth, measurements, and waypoints
            // TODO: Infer index from length of waypoints
        }
    }
}