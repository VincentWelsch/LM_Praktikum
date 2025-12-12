package com.example.praktikum2

import android.util.Log
import androidx.lifecycle.ViewModel
import kotlin.math.pow
import kotlin.math.sqrt

class PositionErrorViewModel(private val collectionModel: CollectionViewModel): ViewModel() {
    private var positionErrors: MutableList<Float> = mutableListOf()
    fun getPositionErrors(): List<Float> { return positionErrors }

    fun calculatePositionErrors() {
        if (collectionModel.getGroundTruth().size != collectionModel.getWaypoints().size)
            // throw Throwable("Error: Ground truth and waypoints must be the same size")
            Log.e("PositionError", "Ground truth and waypoints must be the same size")
        positionErrors.clear()

        val groundTruth = collectionModel.getGroundTruth() // Use for actual position of waypoints
        val measurements = collectionModel.getMeasurements() // Use for position and time
        val waypoints = collectionModel.getWaypoints() // Use for time of arrival at waypoints

        for (measurement in measurements) {
            for (i in 0 until collectionModel.getGroundTruth().size) {
                // i is the index of the waypoint
                // Loop through waypoints to find out which "Stütz-" and "Richtungsvektor" to use
                if (measurement.time > collectionModel.getWaypoints()[i].time &&
                    measurement.time < collectionModel.getWaypoints()[i + 1].time) {
                    /* If tine of measurement is greater than time of waypoint for the first time,
                    *  measurement must be between that waypoint and the next.
                    *
                    *  Interpolate ground truth position between waypoint and next waypoint using
                    *  vectors. Let latitude be x and longitude be y
                    *
                    *  Let m be measurement, g be groundTruth, t_d be the time diff between waypoint
                    *  and measurement. We need normalized "Verbindungsvektor" between both waypoints
                    *  to get gtPosition:
                    *       g[i] + t_d * (g[i + 1] - g[i]) / norm       for both x and for y
                    *
                    *  Error as distance between measurement and gtPosition. Vector between those
                    *  two is:
                    *       m - gtPosition                              for both x and for y
                    *  Distance is sqrt(x^2 + y^2)
                    */
                    // Time difference between w0 and m
                    val timeDiff = measurement.time - waypoints[i].time
                    // Vector between w0 and w1 ("Verbindungsvektor")
                    val wDiff = floatArrayOf(groundTruth[i + 1][0] - groundTruth[i][0],
                        groundTruth[i + 1][1] - groundTruth[i][1])
                    // wDiff with length of 1 ("Richtungsvektor")
                    val norm = sqrt(wDiff[0].pow(2) + wDiff[1].pow(2))
                    // Ground truth position
                    val gtPosition = floatArrayOf(
                        // Waypoint i as "Stützvektor" and wDiff/norm as normalized "Richtungsvektor"
                        groundTruth[i][0] + timeDiff * wDiff[0] / norm,
                        groundTruth[i][1] + timeDiff * wDiff[1] / norm
                    )
                    // Vector between gtPosition and m
                    val positionDiff = floatArrayOf(
                        measurement.latitude - gtPosition[0],
                        measurement.longitude - gtPosition[1]
                    )
                    // Error as length of vector between gtPosition and m
                    positionErrors.add(sqrt(positionDiff[0].pow( 2) + positionDiff[1].pow(2)))
                }
            }
        }
    }

    // Get a CDF from a list of errors
    fun positionErrorCDF(): List<FloatArray> {
        calculatePositionErrors()
        if (positionErrors.isEmpty())
            return emptyList()
        positionErrors.sort()
        val cdf: MutableList<FloatArray> = mutableListOf() // error (x) at index 0, % (y) at index 1
        var sum = 0f
        for (i in 0 until positionErrors.size) {
            sum += 1f / positionErrors.size
            cdf.add(floatArrayOf(positionErrors[i], sum))
        }
        cdf.last()[1] = 1f // Prevent rounding error
        return cdf.toList()
    }

    // Get a position error that meets a given confidence level
    fun getPositionErrorFromConfidence(confidence: Float): Float {
        if (confidence !in 0f..1f) {
            // throw Throwable("Error: Confidence must be between 0 and 1")
            Log.e("CDF", "Confidence must be between 0 and 1")
            return -1f
        }
        val cdf = positionErrorCDF()
        if (cdf.isEmpty()) {
            // throw Throwable("Error: CDF is empty")
            Log.e("CDF", "CDF is empty")
            return -1f
        }
        Log.d("CDF", "Size: " + cdf.size.toString())
        if (cdf.size == 1)
            return cdf[0][0] // All data points are the same
        for (i in 0 until cdf.size) {
            if (cdf[i][1] !in 0f..1f) // Check if chance is possible
                // throw Throwable("Error: CDF contained chance not between 0 and 1")
                return -1f
            if (cdf[i][1] >= confidence)
                return cdf[i][0] // Meets confidence level
        }
        return cdf.last()[0] // Impossible, but IDE complains without it
    }
}