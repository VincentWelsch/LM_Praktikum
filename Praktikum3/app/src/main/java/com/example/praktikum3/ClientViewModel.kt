package com.example.praktikum3

import android.location.Location
import android.util.Log

class ClientViewModel {
    private var fixCount: Int = 0
    private var reportCount: Int = 0
    private var distanceThreshold: Float = 10f
    private var lastSentLocation: PositionFix? = null
    private var isMoving: Boolean = false

    fun incFixCount() {
        fixCount += 1
    }
    fun getFixCount(): Int {
        return fixCount
    }
    fun getReportCount(): Int {
        return reportCount
    }

    fun getIsMoving(): Boolean {
        return isMoving
    }
    fun setIsMoving(moving: Boolean) {
        isMoving = moving
    }

    fun getDistanceThreshold(): Float {
        return distanceThreshold
    }
    fun setDistanceThreshold(threshold: Float) {
        if (threshold < 1f) {
            distanceThreshold = 1f
        } else {
            distanceThreshold = threshold
        }
    }

    fun setLastSentLocation(lastLocation: PositionFix?) {
        // used to reset lastSentLocation when DISTANCE_BASED is used (for immediate report next fix)
        lastSentLocation = lastLocation
    }

    fun reportToServer(fix: PositionFix, time: Long, strategy: ReportingStrategies) {
        fixCount += 1
        when (strategy) {
            ReportingStrategies.NONE -> {
                Log.w("ReportToServerError", "Reporting strategy NONE used")
            }

            ReportingStrategies.PERIODIC, // periodic (fixed time)
            ReportingStrategies.MANAGED_PERIODIC, // managed (min time to cross threshold)
            ReportingStrategies.MANAGED_MOVEMENT, // managed (movement detected)
                    -> {
                try {
                    send(fix, time, strategy)
                    reportCount += 1
                } catch(e: Exception) {
                    Log.e("ReportToServerError", "Failed to report to server: ${e.message}")
                }
            }

            ReportingStrategies.DISTANCE_BASED -> { // distance-based (threshold crossed)
                val last = lastSentLocation
                if (last == null) {
                    // Always send first fix
                    send(fix, time, strategy)
                    lastSentLocation = fix
                    reportCount += 1
                    return
                }
                // Calculate distance
                val distance = calculateDistance(last, fix)
                if (distance >= distanceThreshold) {
                    send(fix, time, strategy)
                    lastSentLocation = fix
                    reportCount += 1
                }
            }

            ReportingStrategies.MANAGED_PLUS_MOVEMENT, // managed+ (movement detected and threshold crossed)
            ReportingStrategies.MANAGED_PLUS_PERIODIC,  // managed+ (estimated time to cross threshold)
                    -> {
                Log.w("ReportToServerError", "Reporting strategy $strategy not yet implemented")
            }
        }
    }

    private fun calculateDistance(a: PositionFix, b: PositionFix): Float {
        val locA = Location("").apply {
            latitude = a.latitude.toDouble()
            longitude = a.longitude.toDouble()
        }
        val locB = Location("").apply {
            latitude = b.latitude.toDouble()
            longitude = b.longitude.toDouble()
        }
        return locA.distanceTo(locB)
    }

    private fun send(fix: PositionFix, time: Long, strategy: ReportingStrategies) {
        Log.d("ReportToServer", """DEBUG: Report $reportCount
            |Strategy: $strategy
            |Fix: $fix
            |Time: $time
        """.trimMargin())
        // TODO: Communicate to server
    }
}