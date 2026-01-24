package com.example.praktikum3

import android.location.Location
import android.util.Log

class ClientViewModel {
    private var fixCount: Int = 0
    private var reportCount: Int = 0
    private var distanceThreshold: Float = 10f
    private var lastSentLocation: PositionFix? = null
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

    fun reportToServer(fix: PositionFix, time: Long, strategy: ReportingStrategies) {
        fixCount += 1
        when (strategy) {
            ReportingStrategies.NONE -> {
                Log.w("ReportToServerError", "Reporting strategy NONE used")
            }
            ReportingStrategies.PERIODIC -> {
                try {
                    // TODO: Check conditions and (eventually) call send(fix, time, strategy)
                    reportCount += 1
                } catch(e: Exception) {
                    Log.e("ReportToServerError", "Failed to report to server: ${e.message}")
                }
            }
            ReportingStrategies.DISTANCE_BASED -> {
                val last = lastSentLocation

                if (last == null) {
                    // erster Fix → immer senden
                    send(fix, time, strategy)
                    lastSentLocation = fix
                    return
                }

                // Distanz berechnen
                val distance = calculateDistance(last, fix)

                if (distance >= distanceThreshold) {
                    send(fix, time, strategy)
                    lastSentLocation = fix
                }
                else
                    System.out.println("distance zwischen 2 GPS-FIX ist noch klein als Distanzschwelle")
            }
            ReportingStrategies.MANAGED_PERIODIC -> {
                // TODO: Check conditions and (eventually) call send(fix, time, strategy)
            }
            ReportingStrategies.MANAGED_MOVEMENT -> {
                // TODO: Check conditions and (eventually) call send(fix, time, strategy)
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
        // TODO: Communicate to server
    }
}