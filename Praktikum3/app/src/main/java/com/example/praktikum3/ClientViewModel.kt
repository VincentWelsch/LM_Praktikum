package com.example.praktikum3

import android.location.Location
import android.util.Log

class ClientViewModel {
    // ==========================================================================================
    // current strategy
    // ==========================================================================================
    /*private var strategy: ReportingStrategies = ReportingStrategies.NONE // strategy
    fun getCurrentStrategy(): ReportingStrategies {
        return strategy
    }
    fun setStrategy(strategy: ReportingStrategies) {
        this.strategy = strategy
    }*/

    // ==========================================================================================
    // counting
    // ==========================================================================================
    private var fixCount: Int = 0 // fixCount
    fun incFixCount() {
        fixCount += 1
    }
    fun getFixCount(): Int {
        return fixCount
    }
    private var reportCount: Int = 0 // reportCount
    fun getReportCount(): Int {
        return reportCount
    }

    // ==========================================================================================
    // strategy-specific variables
    // ==========================================================================================
    private var jobDelay: Long = 5000 // jobDelay
    fun getJobDelay(): Long {
        return jobDelay
    }
    fun setJobDelay(delay: Long) {
        if (delay < 500) {
            jobDelay = 500
        } else {
            jobDelay = delay
        }
    }
    private var distanceThreshold: Float = 10f // distanceThreshold
    fun getDistanceThreshold(): Float {
        return distanceThreshold
    }
    fun setDistanceThreshold(threshold: Float) {
        if (threshold > 1f) {
            distanceThreshold = threshold
        }
    }
    private var lastSentLocation: PositionFix? = null // lastSentLocation
    fun setLastSentLocation(lastLocation: PositionFix?) {
        // used to reset lastSentLocation when DISTANCE_BASED is used (for immediate report next fix)
        lastSentLocation = lastLocation
    }
    private var maxVelocity: Float = 2f // maxVelocity
    fun getMaxVelocity(): Float {
        return maxVelocity
    }
    fun setMaxVelocity(velocity: Float) {
        if (velocity > 0f) {
            maxVelocity = velocity
        }
    }
    private var accelThreshold: Double = 10.0 // accelThreshold
    fun getAccelThreshold(): Double {
        return accelThreshold
    }
    fun setAccelThreshold(threshold: Double) {
        if (threshold > 0) {
            accelThreshold = threshold
        }
    }
    private var isMoving: Boolean = false // isMoving
    fun getIsMoving(): Boolean {
        return isMoving
    }
    fun setIsMoving(moving: Boolean) {
        isMoving = moving
    }

    // ==========================================================================================
    // reset methods
    // ==========================================================================================
    fun resetCounts() {
        fixCount = 0
        reportCount = 0
    }

    fun resetStrategyVars() {
        jobDelay = 5000
        distanceThreshold = 10f
        lastSentLocation = null
        maxVelocity = 2f
        accelThreshold = 10.0
        isMoving = false
    }

    // ==========================================================================================
    // client-specific methods
    // ==========================================================================================
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