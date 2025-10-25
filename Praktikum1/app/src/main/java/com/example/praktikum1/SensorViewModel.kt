// In a new file: SensorViewModel.kt
package com.example.praktikum1

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// https://developer.android.com/topic/libraries/architecture/viewmodel?hl=de
data class SensorDataSnapshot(
    val accelData: FloatArray = FloatArray(3),
    val gyroData: FloatArray = FloatArray(3),
    val magnetData: FloatArray = FloatArray(3),
    val positionData: FloatArray = FloatArray(2) // lon, lat
) {
    // equals/hashCode must be overridden
    // https://www.baeldung.com/kotlin/data-class-equals-method
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SensorDataSnapshot) return false

        if (!accelData.contentEquals(other.accelData)) return false
        if (!gyroData.contentEquals(other.gyroData)) return false
        if (!magnetData.contentEquals(other.magnetData)) return false
        if (!positionData.contentEquals(other.positionData)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = accelData.contentHashCode()
        result = 31 * result + gyroData.contentHashCode()
        result = 31 * result + magnetData.contentHashCode()
        result = 31 * result + positionData.contentHashCode()
        return result
    }
}

/* SensorViewModel is a ViewModel that receives live sensor data and calculates the average
*  of every batch (currently every half second) and stores it as a snapshot in a StateFlow.
*  To access the snapshot:
*  - create an instance of SensorViewModel
*  - call viewModel.startProcessing() -> here, done in DisposableEffect within Application()
*  - in a composable, access viewModel.processedData.collectAsState() as such:
*      val sensorData by viewModel.processedData.collectAsState()
*      Text(text = "x: ${sensorData.accelData[0]}..."}
*  - though not necessary, call viewModel.stopProcessing() when disposing
*/

class SensorViewModel: ViewModel() {
    private val _processedData = MutableStateFlow(SensorDataSnapshot())
    val processedData = _processedData.asStateFlow() // Public read-only StateFlow

    // Buffers for incoming data
    private val accelDataBuffer = mutableListOf<FloatArray>()
    private val gyroDataBuffer = mutableListOf<FloatArray>()
    private val magnetDataBuffer = mutableListOf<FloatArray>()
    private val positionDataBuffer = mutableListOf<FloatArray>()

    // Public methods for adding data -> used by sensor listeners
    fun onNewAccelData(data: FloatArray) = synchronized(accelDataBuffer) { accelDataBuffer.add(data) }
    fun onNewGyroData(data: FloatArray) = synchronized(gyroDataBuffer) { gyroDataBuffer.add(data) }
    fun onNewMagnetData(data: FloatArray) = synchronized(magnetDataBuffer) { magnetDataBuffer.add(data) }
    fun onNewPositionData(data: FloatArray) = synchronized(positionDataBuffer) { positionDataBuffer.add(data) }

    // https://developer.android.com/topic/libraries/architecture/coroutines?hl=de#viewmodelscope
    // Continuous processing using vieModelScope for coroutines
    // Only need one processing job to do all (may be changed later if too slow)
    // Called in Application after creation and when switch is toggled on
    private var processingJob: Job? = null
    fun startProcessing(delayMs: Long = 500L) {
        if (processingJob?.isActive == true) return // Check if running
        processingJob = viewModelScope.launch {
            while (true) {
                updateSnapshot()
                delay(delayMs)
            }
        }
    }

    // Called in Application to stop processing (maybe also when switch is toggled off?)
    fun stopProcessing() {
        processingJob?.cancel()
        processingJob = null
        synchronized(accelDataBuffer) { accelDataBuffer.clear() }
        synchronized(gyroDataBuffer) { gyroDataBuffer.clear() }
        synchronized(magnetDataBuffer) { magnetDataBuffer.clear() }
        synchronized(positionDataBuffer) { positionDataBuffer.clear() }
    }

    private fun updateSnapshot() {
        val newAccel = processBuffer(accelDataBuffer)
        val newGyro = processBuffer(gyroDataBuffer)
        val newMagnet = processBuffer(magnetDataBuffer)
        val newPosition = processBuffer(positionDataBuffer)

        _processedData.value = SensorDataSnapshot( // thread-safe
            accelData = newAccel ?: FloatArray(3),
            gyroData = newGyro ?: FloatArray(3),
            magnetData = newMagnet ?: FloatArray(3),
            positionData = newPosition ?: _processedData.value.positionData,
            /* Position does not always update (depending on minTimeMs and minDistanceM),
             * which causes positionData to be [0.0f, 0.0f] if no new data is sent.
             *
             * accelData, gyroData, and magnetData do not have this problem, as they use constants
             * which are below 0.5 s delay.
             */
        )
    }

    // Generic processing function for all buffers (averages the data)
    private fun processBuffer(buffer: MutableList<FloatArray>): FloatArray? {
        val readings = synchronized(buffer) {
            if (buffer.isEmpty()) {
                return null
            }
            val copy = ArrayList(buffer)
            buffer.clear()
            copy
        }
        if (readings.isEmpty()) {
            return null
        }
        Log.d("SensorViewModel", "Processing ${readings.size} readings")
        val elementCount = readings.first().size
        val average = FloatArray(elementCount)
        for (i in 0 .. elementCount-1) {
            average[i] = readings.map { it[i] }.average().toFloat()
            // average() already gets avg of all elements in a list or array (returns as Double)
            // map to get lists of all x, of all y, and of all z (or longitude and latitude)
            /* i.e.:
             *  [[x1, y1, z1], [x2, y2, z2]].map { it[0] }
             *  would return [x1, x2]
             */

        }
        return average
    }
}