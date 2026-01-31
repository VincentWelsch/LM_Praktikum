package com.example.praktikum3

import android.location.Location
import android.util.Log

class ClientViewModel {
    private var fixCount: Int = 0
    private var reportCount: Int = 0
    private var distanceThreshold: Float = 10f
    private var lastSentLocation: PositionFix? = null
    private var isMoving: Boolean= false

    private var maxVelocity: Float = 0f
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
    fun getMaxVelocity(): Float {
        return maxVelocity
    }
    fun setMaxVelocity(velocity: Float) {
       maxVelocity = velocity
    }
    fun setlastSentLocation(lastLocation: PositionFix?) {
        lastSentLocation = lastLocation
    }

    fun setIsMoving(move: Boolean) {
        isMoving = move
    }
    fun getIsMoving(): Boolean {
       return  isMoving
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
                    reportCount += 1
                    return
                }

                // Distanz berechnen
                val distance = calculateDistance(last, fix)

                if (distance >= distanceThreshold) {
                    send(fix, time, strategy)
                    lastSentLocation = fix
                    reportCount += 1
                }
                else
                    System.out.println("distance zwischen 2 GPS-FIX ist noch klein als Distanzschwelle")
            }
            ReportingStrategies.MANAGED_PERIODIC -> {

                // TODO: Check conditions and (eventually) call send(fix, time, strategy)
                try {
                    send(fix, time, strategy)
                    reportCount += 1
                } catch (e: Exception) {
                    Log.e("ReportToServerError", "Failed to report to server: ${e.message}")
                }

            }
            ReportingStrategies.MANAGED_MOVEMENT -> {
                // TODO: Check conditions and (eventually) call send(fix, time, strategy)
                // 1. Wenn keine Bewegung → nichts senden
                if (!getIsMoving()) {
                    Log.d("ManagedMovement", "No movement → no send")
                    return
                }

                // 2. Hole letzte gesendete Position
                val last = lastSentLocation

                // 3. Wenn noch nie gesendet wurde → ersten Fix immer senden
                if (last == null) {
                    send(fix, time, strategy)
                    lastSentLocation = fix
                    reportCount += 1
                    return
                }

                // 4. Distanz berechnen
                val distance = calculateDistance(last, fix)

                // 5. Wenn Distanzschwelle überschritten → senden
                if (distance >= distanceThreshold) {
                    send(fix, time, strategy)
                    lastSentLocation = fix
                    reportCount += 1
                } else {
                    Log.d("ManagedMovement", "Distance < threshold → no send")
                }

            }
            ReportingStrategies.MOVEMENT_BASED -> {
                // TODO: Check conditions and (eventually) call send(fix, time, strategy)
                try {
                    send(fix, time, strategy)
                    reportCount += 1
                } catch (e: Exception) {
                    Log.e("ReportToServerError", "Failed to report to server: ${e.message}")
                }
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