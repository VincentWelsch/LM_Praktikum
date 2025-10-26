package com.example.praktikum1

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import java.io.File

class StorageViewModel(private val appContext: Context) : ViewModel() {
    private val ioScope = viewModelScope + Dispatchers.IO
    private val fileName = "sensor_data.csv"
    private val file = File(appContext.filesDir, fileName)
    private val header = "timestamp,ax,ay,az,gx,gy,gz,mx,my,mz,lat,lon\n"

    init {
        if (!file.exists()) {
            file.writeText(header)
        }
    }

    fun addSample(sample: SensorSample) {
        ioScope.launch {
            val line = buildString {
                append(sample.timestamp); append(',')
                append(sample.accelX); append(','); append(sample.accelY); append(','); append(sample.accelZ); append(',')
                append(sample.gyroX); append(','); append(sample.gyroY); append(','); append(sample.gyroZ); append(',')
                append(sample.magnetX); append(','); append(sample.magnetY); append(','); append(sample.magnetZ); append(',')
                append(sample.lat?.toString() ?: ""); append(','); append(sample.lon?.toString() ?: ""); append('\n')
            }
            file.appendText(line)
        }

    }

    fun getFilePath(): String = file.absolutePath
}

class StorageViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StorageViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return StorageViewModel(context.applicationContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
