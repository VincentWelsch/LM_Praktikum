package com.example.praktikum1

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.collection.arrayMapOf
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Checkbox
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.core.content.ContextCompat
import com.example.praktikum1.ui.theme.Praktikum1Theme
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private fun startApplication() {
        val sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        enableEdgeToEdge()
        setContent {
            Praktikum1Theme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Application(sensorManager, locationManager)
                    innerPadding
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String?>,
        grantResults: IntArray,
        deviceId: Int
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults, deviceId)

        if (requestCode == 0) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                startApplication()
            } else {
                Log.e("PermissionError", "Not all permissions were granted")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get requested permissions from AndroidManifest.xml
        val packageInfo = packageManager.getPackageInfo(packageName,
            PackageManager.GET_PERMISSIONS)
        val requiredPermissions: Array<String> = packageInfo.requestedPermissions ?: emptyArray()

        // Check if all permissions were granted
        val deniedPermissions = emptySet<String>().toMutableSet()
        for (permission in requiredPermissions) {
            if (ContextCompat.checkSelfPermission(applicationContext, permission) ==
                PackageManager.PERMISSION_DENIED) {
                deniedPermissions += permission
            }
        }

        // Request denied permissions
        if (deniedPermissions.isNotEmpty()) {
            requestPermissions(deniedPermissions.toTypedArray(),0)
        } else {
            startApplication()
        }
    }
}

@Composable
fun Application(sensorManager: SensorManager, locationManager: LocationManager) {
    var accelData: FloatArray by remember { mutableStateOf(FloatArray(3)) }
    var gyroData: FloatArray by remember { mutableStateOf(FloatArray(3)) }
    var magnetData: FloatArray by remember { mutableStateOf(FloatArray(3)) }
    var positionData: FloatArray by remember { mutableStateOf(FloatArray(2)) }
    // Not in startApplication because LocalCOntext.current must be inside a composable
    val ctx = LocalContext.current
    val fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(ctx)
    Column {
        SensorConfig(sensorManager = sensorManager,
            locationManager = locationManager,
            fusedLocationProviderClient = fusedLocationProviderClient,
            onAccelDataChange = { accelData = it },
            onGyroDataChange = { gyroData = it },
            onMagnetDataChange = { magnetData = it },
            onPositionDataChange = { positionData = it })

        TODO("Separate composable for Data Storage and Data Visualisation")
    }
}

@Composable
fun SensorConfig(modifier: Modifier = Modifier,
                 sensorManager: SensorManager,
                 locationManager: LocationManager,
                 fusedLocationProviderClient: FusedLocationProviderClient,
                 onAccelDataChange: (FloatArray) -> Unit,
                 onGyroDataChange: (FloatArray) -> Unit,
                 onMagnetDataChange: (FloatArray) -> Unit,
                 onPositionDataChange: (FloatArray) -> Unit) {
    // https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview?hl=de#sensors-identify

    // Logic
    // Variables
    var allSensorSwitchesEnabled: Boolean by remember { mutableStateOf(true) }

    var accelChecked: Boolean by remember { mutableStateOf(false) }
    var gyroChecked: Boolean by remember { mutableStateOf(false) }
    var magnetChecked: Boolean by remember { mutableStateOf(false) }
    var positionChecked: Boolean by remember { mutableStateOf(false)}
    var currentMethod: String by remember { mutableStateOf(LocationManager.GPS_PROVIDER) }

    var accelDelay: Int by remember { mutableIntStateOf(SensorManager.SENSOR_DELAY_NORMAL) }
    var gyroDelay: Int by remember { mutableIntStateOf(SensorManager.SENSOR_DELAY_NORMAL) }
    var magnetDelay: Int by remember { mutableIntStateOf(SensorManager.SENSOR_DELAY_NORMAL) }
    var positionMinTimeMs: Int by remember { mutableIntStateOf(15000) }
    var positionDistanceM: Float by remember { mutableFloatStateOf(10f) }
    var positionPriority: Int by remember { mutableIntStateOf(Priority.PRIORITY_BALANCED_POWER_ACCURACY) }
    var positionIntervalMs: Int by remember { mutableIntStateOf(10000) }

    // Moved to Application() to make accessible to other composables
    // SensorConfig() now uses "on...Change" lambdas to update values
    /* var accelData: FloatArray by remember { mutableStateOf(FloatArray(3)) }
    var gyroData: FloatArray by remember { mutableStateOf(FloatArray(3)) }
    var magnetData: FloatArray by remember { mutableStateOf(FloatArray(3)) }
    var positionData: FloatArray by remember { mutableStateOf(FloatArray(2)) } */

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
            onAccelDataChange(event?.values!!.copyOf(3))
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            generalAccuracyChanged("Accelerometer", accuracy)
        }
    }

    val gyroListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            onGyroDataChange(event?.values!!.copyOf(3))
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            generalAccuracyChanged("Gyroscope", accuracy)
        }
    }

    val magnetListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            onMagnetDataChange(event?.values!!.copyOf(3))
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            generalAccuracyChanged("Magnetometer", accuracy)
        }
    }

    // Init position event listeners
    val locationListener: LocationListener = LocationListener { location ->
        // IDE suggested using lambda instead of manually overriding onLocationChanged
        onPositionDataChange(floatArrayOf(location.longitude.toFloat(), location.latitude.toFloat()))
    }

    // locationCallback for fused location provider always the same -> outside registerLocationListener
    val locationCallback = object: LocationCallback() {
        override fun onLocationResult(p0: LocationResult) {
            super.onLocationResult(p0)
            if (p0.lastLocation != null) {
                onPositionDataChange(floatArrayOf(p0.lastLocation!!.longitude.toFloat(),
                    p0.lastLocation!!.latitude.toFloat()))
            }
        }
    }

    // Register/unregister
    fun registerSensorLister(listener: SensorEventListener, type: Int, delay: Int) {
        try {
            sensorManager.registerListener(listener, sensorManager.getDefaultSensor(
                type), delay)
        } catch (e: Exception) {
            Log.e("SensorListenerRegistrationError",
                "Failed to register sensor listener: ${e.message}")
        }
    }

    fun unregisterSensorListener(listener: SensorEventListener) {
        try {
            sensorManager.unregisterListener(listener)
        } catch (e: Exception) {
            Log.e("SensorListenerRegistrationError",
                "Failed to unregister sensor listener: ${e.message}")
        }

    }

    @SuppressLint("MissingPermission")
    fun registerLocationListener(method: String) {
        try {
            when (method) {
                LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER -> {
                    locationManager.requestLocationUpdates(
                        method,
                        positionMinTimeMs.toLong(),
                        positionDistanceM,
                        locationListener
                    )
                    Log.d("LocationListenerRegistration", "Location listener registered")
                }
                "fused" -> {
                    // locationRequest inside registerLocationListener as priority is changeable
                    val locationRequest = LocationRequest.Builder(positionPriority,
                        positionIntervalMs.toLong()).build()
                    fusedLocationProviderClient.requestLocationUpdates(locationRequest,
                        locationCallback, Looper.getMainLooper())
                    Log.d("LocationListenerRegistration", "Fused location listener registered")
                }
                else -> Log.e("LocationListenerRegistrationError",
                    "Invalid location method: $method")
            }
        } catch (e: Exception) {
            Log.e("LocationListenerRegistrationError",
                "Failed to register location listener: ${e.message}")
        }
    }

    fun unregisterLocationListener(method: String) {
        try {
            when (method) {
                LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER -> {
                    locationManager.removeUpdates(locationListener)
                    Log.d("LocationListenerRegistration", "Location listener unregistered")
                }
                "fused" -> {
                    fusedLocationProviderClient.removeLocationUpdates(locationCallback)
                    Log.d("LocationListenerRegistration", "Fused location listener unregistered")
                }
                else -> Log.e("LocationListenerRegistrationError",
                    "Invalid location method: $method")
            }
        } catch (e: Exception) {
            Log.e("LocationListenerRegistrationError",
                "Failed to unregister location listener: ${e.message}")
        }
    }

    fun changePositionMethod(method: String) {
        unregisterLocationListener(currentMethod)
        registerLocationListener(method)
        currentMethod = method
    }


    // GUI elements
    // Toggle all
    Switch(checked = allSensorSwitchesEnabled, onCheckedChange = { checked ->
        if (checked) {
            allSensorSwitchesEnabled = true
        } else {
            // uncheck all to let onCheckedChange() handle unregistering
            accelChecked = false
            gyroChecked = false
            magnetChecked = false
            positionChecked = false
            allSensorSwitchesEnabled = false
        }
    })
    Text(text = "Enable data collection")

    Column {
        // Checkboxes for individual sensors
        val sensorNames = arrayOf("Accelerometer", "Gyroscope", "Magnetometer")
        val sensorChecked = arrayOf(accelChecked, gyroChecked, magnetChecked)
        val sensorListeners= arrayOf(accelListener, gyroListener, magnetListener)
        val sensorTypes = arrayOf(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE,
            Sensor.TYPE_MAGNETIC_FIELD)
        val sensorDelays = arrayOf(accelDelay, gyroDelay, magnetDelay)
        for (i in 0..2) {
            Row {
                Checkbox(
                    checked = sensorChecked[i],
                    onCheckedChange = { checked ->
                        if (checked) {
                            registerSensorLister(
                                sensorListeners[i],
                                sensorTypes[i],
                                sensorDelays[i]
                            )
                        } else {
                            unregisterSensorListener(sensorListeners[i])
                        }
                    }
                )
                Text(text = sensorNames[i])
            }
        }

        // Sliders for individual sensor delays
        for (i in 0..2) {
            Row {
                Text(text = "${sensorNames[i]} delay: ${sensorDelays[i]}")
                Slider(
                    value = sensorDelays[i].toFloat(),
                    modifier = modifier.weight(1f),
                        // supposedly helps with less jumpy sliders when the text length changes
                    steps = 2, // 0 -> step -> step -> 3
                    onValueChange = { sensorDelays[i] = it.roundToInt() },
                        // it.toInt was recommended to prevent recomposes with each fractional change
                        /* changed to it.roundToInt because testing showed that some steps are
                         * not rounded properly
                         * used Slider with range 0..60000 and 59 steps (each +1000 increment)
                         * at a certain interval got sequences like 52000 -> 52999 -> 54000
                         */
                    onValueChangeFinished = {
                        unregisterSensorListener(sensorListeners[i])
                        registerSensorLister(sensorListeners[i],
                            sensorTypes[i], sensorDelays[i])
                    },
                    valueRange = SensorManager
                        .SENSOR_DELAY_FASTEST.toFloat()..SensorManager.SENSOR_DELAY_NORMAL.toFloat()
                )
            }
        }

        // Checkbox for position
        Row {
            Checkbox(
                checked = positionChecked,
                enabled = allSensorSwitchesEnabled,
                onCheckedChange = { checked ->
                    if (checked) {
                        registerLocationListener(currentMethod)
                    } else {
                        unregisterLocationListener(currentMethod)
                    }
                }
            )
            Text(text = "Position")
        }

        // https://developer.android.com/develop/ui/compose/components/radio-button?hl=de
        // Radio Buttons to choose method for determining position
        Column(modifier.selectableGroup()) {
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, "fused").forEach { method ->
                Row(Modifier.selectable(
                    selected = (method == currentMethod),
                    onClick = {
                        try {
                            changePositionMethod(method)
                        } catch (e: Exception) {
                            Log.e("MethodChangeError",
                                "Failed to change method for position service: ${e.message}")
                        }
                    },
                    role = Role.RadioButton
                )) {
                    RadioButton(
                        selected = (method == currentMethod),
                        onClick = null,
                        enabled = allSensorSwitchesEnabled
                    )
                    Text(text = method)
                }
            }

            Row {
                Text(text = "Minimum time in ms between updates: $positionMinTimeMs")
                Slider(
                    value = positionMinTimeMs.toFloat(),
                    modifier = modifier.weight(1f),
                    steps = 59, // 0 -> 59 steps (each +1000 increment) -> 60000
                    enabled = (allSensorSwitchesEnabled && currentMethod != "fused"),
                    onValueChange = { positionMinTimeMs = it.roundToInt() },
                    onValueChangeFinished = {
                        unregisterLocationListener(currentMethod)
                        registerLocationListener(currentMethod)
                    },
                    valueRange = 0f..60000f
                )
            }

            Row {
                Text(text = "Minimum distance in m between updates: $positionDistanceM")
                Slider(
                    value = positionDistanceM.toFloat(),
                    modifier = modifier.weight(1f),
                    steps = 99, // 0 -> 99 steps (each +1 increment) -> 100
                    enabled = (allSensorSwitchesEnabled && currentMethod != "fused"),
                    onValueChange = { positionDistanceM = it.roundToInt().toFloat() },
                    onValueChangeFinished = {
                        unregisterLocationListener(currentMethod)
                        registerLocationListener(currentMethod)
                    },
                    valueRange = 0f..100f)
            }

            Row {
                Text(text = "Position priority: $positionPriority")
                Slider(
                    value = positionPriority.toFloat(),
                    modifier = modifier.weight(1f),
                    steps = 4, // 100 -> step -> step -> step -> step -> 105
                    enabled = (allSensorSwitchesEnabled && currentMethod == "fused"),
                    onValueChange = { newValue ->
                        positionPriority = when {
                            newValue <= 100f -> Priority.PRIORITY_HIGH_ACCURACY // 100
                            newValue <= 102f -> Priority.PRIORITY_BALANCED_POWER_ACCURACY // 102
                            newValue <= 104f -> Priority.PRIORITY_LOW_POWER // 104
                            else -> Priority.PRIORITY_PASSIVE // 105
                        }
                    },
                    onValueChangeFinished = {
                        unregisterLocationListener(currentMethod)
                        registerLocationListener(currentMethod)
                    },
                    valueRange = 100f..105f) // 103 and 101 do not exist
            }

            Row {
                Text(text = "Position interval in ms: $positionIntervalMs")
                Slider(
                    value = positionIntervalMs.toFloat(),
                    modifier = modifier.weight(1f),
                    steps = 199, // 0 -> 199 steps (each +100 increment) -> 20000
                    enabled = (allSensorSwitchesEnabled && currentMethod == "fused"),
                    onValueChange = { positionIntervalMs = it.roundToInt() },
                    onValueChangeFinished = {
                        unregisterLocationListener(currentMethod)
                        registerLocationListener(currentMethod)
                    },
                    valueRange = 0f..20000f
                )
            }
        }
    }
}