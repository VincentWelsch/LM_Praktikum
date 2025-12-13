package com.example.praktikum2

import android.content.ClipData
import android.content.Context
import android.util.Log
import android.content.ClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
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

class CollectionViewModel(
    private val appContext: Context,
    private val clipboard: ClipboardManager): ViewModel() {
    private val ioScope = viewModelScope + Dispatchers.IO
    private var groundTruth: MutableList<FloatArray> = mutableListOf()
    fun getGroundTruth(): List<FloatArray> { return groundTruth.toList() }
    fun setGroundTruth(gnd: MutableList<FloatArray>) { groundTruth = gnd }
    // Note: toList creates a copy. We use this to prevent concurrency errors which were causing errors.

    // Allow or disallow adding new data
    private var takesNew = false;
    fun getTakesNew(): Boolean { return takesNew }
    fun setTakesNew(b: Boolean) { takesNew = b }

    // Store and get measurements and waypoints
    private var measurements: MutableList<Measurement> = mutableListOf()
    fun getMeasurements(): List<Measurement> { return measurements.toList() }
    fun getMeasurementsCount(): Int { return measurements.size}
    private var waypoints: MutableList<Measurement> = mutableListOf()
    fun getWaypoints(): List<Measurement> { return waypoints.toList() }
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
    Intended json scheme:
    {
        title: String,
        groundTruth: [ [Float, Float], ...],
        measurements: [ {longitude: Float, latitude: Float, time: Long}, ... ],
        waypoints: [ {longitude: Float, latitude: Float, time: Long}, ... ],
    }
     */
    // Store entire collection with title
    fun storeCollection(title: String) {
        if (title.isNotEmpty()) {
            // Remember takesNew
            val tookNew: Boolean = takesNew
            takesNew = false
            // Save success
            var success = false
            ioScope.launch() {
                try {
                    // Create temporary Run object for serialization
                    val run = Run(
                        title,
                        groundTruth as List<FloatArray>,
                        measurements as List<Measurement>,
                        waypoints as List<Measurement>
                    )
                    val runJson = Json.encodeToString(run)
                    Log.d("SaveToFile", "Content: $runJson")
                    // Write to file
                    val file = File(appContext.filesDir, "$title.json")
                    file.writeText(runJson)

                    val absPath = file.absolutePath
                    Log.d("SaveToFile", "Path: $absPath")
                    success = true
                    clipboard.setPrimaryClip(ClipData.newPlainText("Run",
                        AnnotatedString(runJson))) // Copy json to clipboard
                    takesNew = tookNew
                } catch (e: Exception) {
                    Log.e("SaveToFile", "Failed to save run: ${e.message}")
                    takesNew = tookNew
                }
            }
        } else {
            Log.w("SaveToFile", "No title was provided")
        }
    }
    // Load entire collection with title and return title
    fun loadCollection(title: String) {
        if (title.isNotEmpty()) {
            // Remember takesNew
            val tookNew: Boolean = takesNew
            takesNew = false
            // Clear to avoid errors when clearing empty lists
            groundTruth.clear()
            measurements.clear()
            waypoints.clear()
            // Save success
            var success = false
            ioScope.launch {
                try {
                    // Read from file
                    val file = File(appContext.filesDir, "$title.json")
                    val absPath = file.absolutePath
                    val runJson = file.readText()
                    Log.d("LoadFromFile", "Content: $runJson")
                    val run: Run = Json.decodeFromString<Run>(runJson)
                    // Set local variables
                    groundTruth.addAll(run.groundTruth)
                    measurements.addAll(run.measurements)
                    waypoints.addAll(run.waypoints)
                    Log.d("LoadFromFile", "Path: $absPath")
                    success = true
                    takesNew = tookNew
                } catch (e: Exception) {
                    Log.e("LoadFromFile", "Failed to load run: ${e.message}")

                    takesNew = tookNew
                }
            }
        } else {
            Log.w("LoadFromFile", "No title was provided")
        }
    }

    fun clearCollection(): Array<Int> {
        // Remember takesNew
        val tookNew: Boolean = takesNew
        takesNew = false
        // Save count for visual feedback, then clear
        val arr: Array<Int> = arrayOf(getMeasurementsCount(), getWaypointsCount())
        try {
            groundTruth.clear()
            measurements.clear()
            waypoints.clear()
        } catch(e: Exception) {
            Log.e("ClearCollection", "Failed to clear collection: ${e.message}")
        }
        // Restore takesNew, then return
        takesNew = tookNew
        return arr
    }
}