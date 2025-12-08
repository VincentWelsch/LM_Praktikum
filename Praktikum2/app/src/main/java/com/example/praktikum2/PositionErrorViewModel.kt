package com.example.praktikum2

import androidx.lifecycle.ViewModel
import kotlin.math.pow
import kotlin.math.sqrt

class PositionErrorViewModel(private val collectionModel: CollectionViewModel): ViewModel() {
    private var positionErrors: MutableList<Float> = mutableListOf()
    fun getPositionErrors(): List<Float> { return positionErrors }

    fun calculatePositionErrors() {
        if (collectionModel.getGroundTruth().size != collectionModel.getWaypoints().size)
            throw Throwable("Error: Ground truth and waypoints must be the same size")
        positionErrors.clear()

        val groundTruth = collectionModel.getGroundTruth() // Use for actual position of waypoints
        val measurements = collectionModel.getMeasurements() // Use for position and time
        val waypoints = collectionModel.getWaypoints() // Use for time of arrival at waypoints

        for (measurement in collectionModel.getMeasurements()) {
            for (i in 0 until collectionModel.getGroundTruth().size) {
                // i is the index of the waypoint
                // Loop through waypoints to find out which "Stütz-" and "Richtungsvektor" to use
                if (measurement.time > collectionModel.getWaypoints()[i].time) {
                    /* If tine of measurement is greater than time of waypoint for the first time,
                    *  measurement must be between that waypoint and the next.
                    *
                    *  Interpolate ground truth position between waypoint and next waypoint using
                    *  vectors. Let latitude be x and longitude be y
                    *
                    *  Let g be groundTruth and t_d be the time diff between waypoint and measurement.
                    *  To get gtPosition:
                    *  g[i] + t_d * (g[i + 1] - g[i])       both for x and for y
                    *
                    *  Error as difference between measurement and gtPosition:
                    *  m - gtPosition                       both for x and for y
                    */
                    val timeDiff = measurement.time - waypoints[i].time
                    val gtPosition = floatArrayOf(
                        // Waypoint i as Stützvektor and "Verbindungsvektor" to next as "Richtungsvektor"
                        groundTruth[i][0] + timeDiff * (groundTruth[i + 1][0] - groundTruth[i][0]),
                        groundTruth[i][1] + timeDiff * (groundTruth[i + 1][1] - groundTruth[i][1])
                    )
                    val positionDiff = floatArrayOf(
                        measurement.latitude - gtPosition[0],
                        measurement.longitude - gtPosition[1]
                    )
                    // Calculate and add length of vector
                    positionErrors.add(sqrt(positionDiff[0].pow( 2) + positionDiff[1].pow(2)))
                }
            }
        }
        positionErrors = mutableListOf()
    }

    // Get a CDF from a list of errors
    fun positionErrorCDF(): List<FloatArray> {
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
    fun getPositionErrorFromConfidence(cdf: List<FloatArray>, confidence: Float): Float {
        if (confidence !in 0f..1f)
            throw Throwable("Error: Confidence must be between 0 and 1")
        if (cdf.isEmpty())
            throw Throwable("Error: CDF is empty")
        if (cdf.size == 1)
            return cdf[0][0] // All data points are the same
        for (i in 0 until cdf.size) {
            if (cdf[i][1] !in 0f..1f) // Check if chance is possible
                throw Throwable("Error: CDF contained chance not between 0 and 1")
            if (cdf[i][1] >= confidence)
                return cdf[i][0] // Meets confidence level
        }
        return cdf.last()[0] // Impossible, but IDE complains without it
    }
}