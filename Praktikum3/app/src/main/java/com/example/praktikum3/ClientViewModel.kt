package com.example.praktikum3

import android.util.Log

class ClientViewModel {
    private var fixCount: Int = 0
    private var reportCount: Int = 0
    private var distanceThreshold: Float = 10f
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
                // TODO: Check conditions and (eventually) call send(fix, time, strategy)
            }
            ReportingStrategies.MANAGED_PERIODIC -> {
                // TODO: Check conditions and (eventually) call send(fix, time, strategy)
            }
            ReportingStrategies.MANAGED_MOVEMENT -> {
                // TODO: Check conditions and (eventually) call send(fix, time, strategy)
            }
        }
    }

    private fun send(fix: PositionFix, time: Long, strategy: ReportingStrategies) {
        // TODO: Communicate to server
    }
}