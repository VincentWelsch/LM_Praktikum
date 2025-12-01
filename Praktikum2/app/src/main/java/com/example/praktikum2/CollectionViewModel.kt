package com.example.praktikum2

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToSting
import java.io.File

// import kotlin.time.Instant

@Serializable
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

@Serializable
data class Run(
    val title: String,
    val groundTruth: List<FloatArray>,
    val measurements: List<Measurement>,
    val waypoints: List<Measurement>,
) {

}

/*
Usage:
- Set groundTruth
- Let measurements be added periodically
- Add waypoints (recorded upon reaching a fixed point of ground truth) when pressing a button
- Save all in JSON format with storeCollection
 */

class CollectionViewModel(private val appContext: Context): ViewModel() {
    private var groundTruth = mutableListOf<FloatArray>()
    fun getGroundTruth(): List<FloatArray> { return groundTruth as List<FloatArray> }
    fun setGroundTruth(gnd: MutableList<FloatArray>) { groundTruth = gnd }

    // Allow or disallow adding new data
    private var takesNew = true;
    fun getTakesNew(): Boolean { return takesNew }
    fun setTakesNew(b: Boolean) { takesNew = b }

    // Store and get measurements and waypoints
    private var measurements = mutableListOf<Measurement>()
    fun getMeasurements(): List<Measurement> { return measurements }
    fun getMeasurementsCount(): Int { return measurements.size}
    private var waypoints = mutableListOf<Measurement>()
    fun getWaypoints(): List<Measurement> { return waypoints }
    fun getWaypointsCount(): Int { return waypoints.size }

    // Add measurements and waypoints, return true if successful
    fun addMeasurement(measurement: Measurement): Boolean { // existing instance of Measurement
        if (takesNew) {
            measurements.add(measurement)
            return true
        }
        return false
    }
    fun addMeasurement(longitude: Float, latitude: Float): Boolean { // new instance of Measurement
        if (takesNew) {
            measurements.add(Measurement(longitude, latitude, System.currentTimeMillis()))
            return true
        }
        return false
    }
    fun addWaypoint(measurement: Measurement): Boolean {
        if (takesNew) {
            waypoints.add(measurement)
            return true
        }
        return false
    }
    fun addWaypoint(longitude: Float, latitude: Float): Boolean { // new instance of Measurement
        if (takesNew) {
            waypoints.add(Measurement(longitude, latitude, System.currentTimeMillis()))
            return true
        }
        return false
    }


    /*
    Scheme:
    {
        title: String,
        groundTruth: [ [Float, Float], ...],
        measurements: [ {longitude: Float, latitude: Float, time: Long}, ...],
        waypoints: [  ],
    }
     */
    // Store entire collection with title
    fun storeCollection(title: String) {
        if (title.isNotEmpty()) {
            try {
                // Create temporary Run object for serialization
                val run = Run(title, groundTruth, measurements, waypoints)
                val runJson = Json.encodeToString(run)
                // Write to file
                val file = File(appContext.filesDir, "$title.json")
                file.writeText(runJson)
                val absPath = file.absolutePath
                Log.d("SaveToFile", "Path: $absPath")
            } catch (e: Exception) {
                Log.e("SaveToFile", "Failed to save run: ${e.message}")
            }
        } else {
            Log.w("SaveToFile", "No title was provided")
        }
    }
    // Load entire collection with title and return title
    fun loadCollectionU(title: String): String {
        if (title.isNotEmpty()) {
            try {
                // Read from file
                val file = File(appContext.filesDir, "$title.json")
                val absPath = file.absolutePath
                val run: Run = Json.decodeFromString<Run>(file.readText())
                // Set local variables
                groundTruth = run.groundTruth as MutableList<FloatArray>
                measurements = run.measurements as MutableList<Measurement>
                waypoints = run.waypoints as MutableList<Measurement>
                Log.d("LoadFromFile", "Path: $absPath")
                return run.title
            } catch (e: Exception) {
                Log.e("LoadFromFile", "Failed to load run: ${e.message}")
            }
        } else {
            Log.w("LoadFromFile", "No title was provided")
        }
    }
}