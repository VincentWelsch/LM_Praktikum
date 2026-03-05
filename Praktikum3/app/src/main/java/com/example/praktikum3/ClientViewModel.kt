package com.example.praktikum3

import android.content.Context
import android.location.Location
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FilenameFilter
import kotlin.io.path.Path

class ClientViewModel(
    private val appContext: Context): ViewModel() {
    // ==========================================================================================
    // toast display
    // ==========================================================================================
    private val _uiEvent = MutableSharedFlow<String>() // UI event message channel
    val uiEvent = _uiEvent.asSharedFlow()

    // ==========================================================================================
    // local fixes
    // ==========================================================================================
    private var localFixes = mutableListOf<PositionFix>() // local fixes

    private val mockLocalFixes: Array<PositionFix> = arrayOf<PositionFix>(
        PositionFix(51.482f, 7.217f, 0f, true),
        PositionFix(51.483f, 7.218f, 0f, false),
        PositionFix(51.484f, 7.219f, 0f, true),
        PositionFix(51.485f, 7.220f, 0f, true),
        PositionFix(51.486f, 7.221f, 0f, false)
    )

    fun getLocalFixes(): Array<PositionFix> {
        return localFixes.toTypedArray()
        // return mockLocalFixes
    }

    // ==========================================================================================
    // manage runs
    // ==========================================================================================
    fun listRuns(): Array<String> {
        // Return all run IDs as String array
        val files: Array<String> = appContext.fileList()
        return files.map { it.removeSuffix(".json") }.toTypedArray()
    }

    fun clearRun() {
        localFixes.clear()
        resetCounts()
    }

    fun loadRun(runId: String) {
        viewModelScope.launch {
            try {
                val publicDir = appContext.getExternalFilesDir(null)
                // See View > Tool Windows > Device Explorer
                // Files located at /storage/emulated/0/Android/data/com.example.praktikum3/files

                // Check runId
                if (runId.isEmpty()) { // empty?
                    Log.e("LoadFromFile", "Run ID is empty")
                    _uiEvent.emit("Error: Run ID is empty")
                } else if (runId.length > 20) { // too long?
                    Log.e("LoadFromFile", "Run ID is too long")
                    _uiEvent.emit("Error: Run ID is too long")
                } else if (runId.contains(" ") || // contains illegal characters?
                    runId.contains("\n") ||
                    runId.contains("\t") ||
                    runId.contains("/") ||
                    runId.contains("\\") ||
                    runId.contains(":")
                ) {
                    Log.e("SaveToFile", "Run ID contains illegal character")
                    _uiEvent.emit("Error: Run ID contains illegal character")
                } else if (publicDir == null || // does not exist?
                    !Path(publicDir.absolutePath + "/$runId.json").toFile().exists()) {
                    Log.e("LoadFromFile", "Run ID does not exist")
                    _uiEvent.emit("Error: Run ID does not exist")
                } else { // Valid runId
                    // Read from file
                    val file = File(publicDir, "$runId.json")
                    val runJson = file.readText()
                    val run: Run = Json.decodeFromString<Run>(runJson)

                    // Set local variables
                    localFixes = run.getFixes().toMutableList()
                    fixCount = run.getFixes().filter { !it.wasReported }.size
                    reportCount = localFixes.size - fixCount

                    val runFixesCount = localFixes.size
                    Log.d("LoadFromFile", "Loaded $runFixesCount fixes from run $runId")
                    _uiEvent.emit("Loaded $runFixesCount fixes from run $runId")
                }
            } catch (e: Exception) {
                Log.e("LoadFromFile", "Failed to load run: ${e.message}")
                _uiEvent.emit("Error: Failed to load run")
            }
        }
    }

    fun storeRun(runId: String) {
        viewModelScope.launch {
            try {
                // Check runId
                if (runId.isEmpty()) { // is empty?
                    Log.e("SaveToFile", "Run ID is empty")
                    _uiEvent.emit("Error: Run ID is empty")
                } else if (runId.length > 20) { // too long?
                    Log.e("SaveToFile", "Run ID is too long")
                    _uiEvent.emit("Error: Run ID is too long")
                } else if (runId.contains(" ") || // contains illegal characters?
                    runId.contains("\n") ||
                    runId.contains("\t") ||
                    runId.contains("/") ||
                    runId.contains("\\") ||
                    runId.contains(":")
                ) {
                    Log.e("SaveToFile", "Run ID contains illegal character")
                    _uiEvent.emit("Error: Run ID contains illegal character")
                } else { // Valid runId
                    // Create temporary Run object for serialization
                    val run = Run(runId, localFixes.toTypedArray())
                    val runJson = Json.encodeToString(run)
                    val runFixesCount = run.getFixes().size

                    // Write to file
                    // val file = File(appContext.filesDir, "$runId.json")
                    val publicDir = appContext.getExternalFilesDir(null)
                    val file = File(publicDir, "$runId.json")
                    file.writeText(runJson)
                    // Now using external directory for easy run data exchange between students

                    Log.d("SaveToFile", "Saved $runFixesCount fixes to run $runId ($publicDir)")
                    _uiEvent.emit("Saved $runFixesCount fixes to run $runId")
                }
            } catch (e: Exception) {
                Log.e("SaveToFile", "Failed to save run: ${e.message}")
                _uiEvent.emit("Error: Failed to save run")
            }
        }
    }

    // ==========================================================================================
    // counting
    // ==========================================================================================
    private var fixCount: Int = 0 // fixCount
    /* fun incFixCount() {
        fixCount += 1
    } */
    fun getFixCount(): Int {
        return fixCount
    }
    private var reportCount: Int = 0 // reportCount
    fun getReportCount(): Int {
        return reportCount
    }

    fun showFixCounts() { // display as toast
        viewModelScope.launch {
            try {
                Log.d("ShowFixCounts", "Fixes: $fixCount, Reports: $reportCount")
                _uiEvent.emit("Fixes: $fixCount, Reports: $reportCount")
            } catch (e: Exception) {
                Log.e("ShowFixCounts", "Failed to show fix counts: ${e.message}")
                _uiEvent.emit("Error: Failed to show fix counts")
            }
        }
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
        var wasSent = false // remember if fix was sent

        when (strategy) {
            ReportingStrategies.NONE -> {
                Log.w("ReportToServerError", "Reporting strategy NONE used")
                wasSent = false
            }

            ReportingStrategies.PERIODIC, // periodic (fixed time)
            ReportingStrategies.MANAGED_PERIODIC, // managed (min time to cross threshold)
            ReportingStrategies.MANAGED_MOVEMENT, // managed (movement detected)
                -> {
                try {
                    send(fix, time, strategy)
                    reportCount += 1
                    wasSent = true
                } catch (e: Exception) {
                    Log.e("ReportToServerError", "Failed to report to server: ${e.message}")
                    wasSent = false
                }
            }

            ReportingStrategies.DISTANCE_BASED -> { // distance-based (threshold crossed)
                val last = lastSentLocation
                if (last == null) {
                    // Always send first fix
                    send(fix, time, strategy)
                    lastSentLocation = fix
                    reportCount += 1
                    wasSent = true
                } else {
                    // Calculate distance
                    val distance = calculateDistance(last, fix)
                    if (distance >= distanceThreshold) {
                        // Send if beyond threshold
                        send(fix, time, strategy)
                        lastSentLocation = fix // remember as last
                        reportCount += 1
                        wasSent = true
                    } else { // distance too small
                        wasSent = false
                    }
                }
            }

            ReportingStrategies.MANAGED_PLUS_MOVEMENT, // managed+ (movement detected and threshold crossed)
            ReportingStrategies.MANAGED_PLUS_PERIODIC,  // managed+ (estimated time to cross threshold)
                -> {
                Log.w("ReportToServerError", "Reporting strategy $strategy not yet implemented")
            }
        }

        // Track fixes locally for later visualization
        if (wasSent) {
            fix.wasReported = true // update fix to reflect that it was reported
            // TODO: use color during visualization to differentiate between reported and not reported fixes
        }
        localFixes.add(fix)
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


@Serializable
data class Run(private val runId: String, private val fixes: Array<PositionFix>) {
    fun getRunId(): String {
        return runId
    }
    fun getFixes(): Array<PositionFix> {
        return fixes
    }

    // equals/hashCode must be overridden
    // https://www.baeldung.com/kotlin/data-class-equals-method
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Run) return false

        if (runId != other.runId) return false
        if (!fixes.contentEquals(other.fixes)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = runId.hashCode()
        result = 31 * result + fixes.hashCode()
        return result
    }
}

