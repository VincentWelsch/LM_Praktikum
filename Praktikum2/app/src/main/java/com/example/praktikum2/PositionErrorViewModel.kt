package com.example.praktikum2

import androidx.lifecycle.ViewModel

class PositionErrorViewModel(private val collectionModel: CollectionViewModel): ViewModel() {
    fun calculatePositionErrors() {
        // TODO
    }

    // Get a CDF from a list of errors
    fun positionErrorCDF(positionErrors: MutableList<Float>): List<FloatArray> {
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