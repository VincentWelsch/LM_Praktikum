package com.example.praktikum1

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.widget.RadioButton
import android.widget.Switch
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.praktikum1.ui.theme.Praktikum1Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        enableEdgeToEdge()
        setContent {
            Praktikum1Theme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SensorConfig(sensorManager)
                    innerPadding
                }
            }
        }
    }
}

@Composable
fun SensorConfig(sensorManager: SensorManager) {
    // https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview?hl=de#sensors-identify
    // https://www.geeksforgeeks.org/kotlin/radiogroup-in-kotlin/

    // Logic
    // Variables
    var allSensorSwitchesEnabled: Boolean by remember { mutableStateOf(true) }

    var accelChecked: Boolean by remember { mutableStateOf(false) }
    var gyroChecked: Boolean by remember { mutableStateOf(false) }
    var magnetChecked: Boolean by remember { mutableStateOf(false) }
    var positionChecked: Boolean by remember { mutableStateOf(false)}
    var gpsChecked: Boolean by remember { mutableStateOf(true) }

    var accelDelay: Int by remember { mutableIntStateOf(SensorManager.SENSOR_DELAY_NORMAL) }
    var gyroDelay: Int by remember { mutableIntStateOf(SensorManager.SENSOR_DELAY_NORMAL) }
    var magnetDelay: Int by remember { mutableIntStateOf(SensorManager.SENSOR_DELAY_NORMAL) }

    var accelData: FloatArray by remember { mutableStateOf(FloatArray(3)) }
    var gyroData: FloatArray by remember { mutableStateOf(FloatArray(3)) }
    var magnetData: FloatArray by remember { mutableStateOf(FloatArray(3)) }

    fun generalAccuracyChanged(sensor: String, accuracy: Int) {
        if (accuracy < SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM) {
            val warning = when (accuracy) {
                SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "Low"
                SensorManager.SENSOR_STATUS_UNRELIABLE -> "Unreliable"
                else -> ""
            }
            Log.d("SensorAccuracyWarning", "Accuracy of $sensor is $warning")
        }
    }

    // Init sensor event listeners
    val accelListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            accelData = event?.values!!.copyOf(3)
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            generalAccuracyChanged("Accelerometer", accuracy)
        }
    }

    val gyroListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            gyroData = event?.values!!.copyOf(3)
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            generalAccuracyChanged("Gyroscope", accuracy)
        }
    }

    val magnetListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            magnetData = event?.values!!.copyOf(3)
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            generalAccuracyChanged("Magnetometer", accuracy)
        }
    }

    // Register/unregister
    fun regListener(listener: SensorEventListener, type: Int, delay: Int) {
        try {
            sensorManager.registerListener(listener, sensorManager.getDefaultSensor(
                type), delay)
        } catch (e: Exception) {
            Log.e("SensorListenerRegistrationError", "Failed to register sensor listener: ${e.message}")
        }
    }

    fun unregListener(listener: SensorEventListener) {
        try {
            sensorManager.unregisterListener(listener)
        } catch (e: Exception) {
            Log.e("SensorListenerRegistrationError", "Failed to unregister sensor listener: ${e.message}")
        }

    }


    // GUI elements
    // Toggle all
    val allSensorsSwitch = Switch(checked = allSensorSwitchesEnabled, onCheckedChange = {
        // for each switch, if checked, register/unregister listener

    })
    Text(text = "Enable data collection")

    // Toggle accelerometer
    val accelSwitch = Checkbox(
        checked = accelChecked,
        enabled = allSensorSwitchesEnabled,
        onCheckedChange = { checked ->
            if (checked) {
                regListener(
                    accelListener,
                    Sensor.TYPE_ACCELEROMETER,
                    accelDelay
                )
            } else {
                unregListener(
                    accelListener
                )
            }
        }
    )
    Text(text = "Accelerometer")

    // Toggle gyroscope
    val gyroSwitch = Checkbox(
        checked = gyroChecked,
        onCheckedChange = { checked ->
            if (checked) {
                regListener(
                    gyroListener,
                    Sensor.TYPE_GYROSCOPE,
                    gyroDelay
                )
            } else {
                unregListener(
                    gyroListener
                )
            }
        }
    )
    Text(text = "Gyroscope")

    // Toggle magnetometer
    val magnetSwitch = Checkbox(
        checked = magnetChecked,
        enabled = allSensorSwitchesEnabled,
        onCheckedChange = { checked ->
            if (checked) {
                regListener(
                    magnetListener,
                    Sensor.TYPE_MAGNETIC_FIELD,
                    magnetDelay
                )
            } else {
                unregListener(
                    magnetListener
                )
            }
        }
    )
    Text(text = "Magnetometer")

    // Toggle position
    val positionSwitch = Checkbox(
        checked = positionChecked,
        enabled = allSensorSwitchesEnabled,
        onCheckedChange = { checked ->
            if (checked) {
                TODO()
            } else {
                TODO()
            }
        }
    )

    val gpsRadio = RadioButton(
        context = ,
        checked = gpsChecked,
        enabled = allSensorSwitchesEnabled,
        onCheckedChange = {
            TODO()
        }

    )

    val arr: Array<Unit> = arrayOf(accelSwitch, gyroSwitch, magnetSwitch, positionSwitch)
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    Praktikum1Theme {
        Greeting("Android")
    }
}