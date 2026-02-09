package com.example.praktikum3

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.praktikum3.ui.theme.Praktikum3Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        try {
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
                // Start application if all permissions were granted
                setContent {
                    Praktikum3Theme {
                        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                            val sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
                            val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
                            Menu(innerPadding, sensorManager, locationManager)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PermissionError", "Failed to get permissions: ${e.message}")
        }
    }
}

@Composable
fun Menu(innerPadding: PaddingValues, sensorManager: SensorManager, locationManager: LocationManager) {
    val client = ClientViewModel()
    val ctx = LocalContext.current

    Column (
        modifier = Modifier.padding(innerPadding).fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        SensorConfig(
            sensorManager = sensorManager,
            locationManager = locationManager,
            client = client,
            ctx = ctx,
        )
    }
}

@Composable
fun SensorConfig(sensorManager: SensorManager, locationManager: LocationManager,
                 client: ClientViewModel, ctx: Context
) {
    // State
    var currentMethod: String by remember { mutableStateOf(LocationManager.GPS_PROVIDER) }
    var currentStrategy: ReportingStrategies by remember { mutableStateOf(ReportingStrategies.NONE) }
    var acceleration: Double by remember { mutableDoubleStateOf(0.0) }
    var periodicJob: Job? by remember { mutableStateOf(null) }
    val scope = rememberCoroutineScope()

    // Strategy Config
    var jobDelay: Long by remember { mutableLongStateOf(5000) }
        // Rate of updates with PERIODIC equals: 1 / jobDelay
    var distanceThreshold: Float by remember { mutableFloatStateOf(client.getDistanceThreshold()) } // in m
        // Updates with DISTANCE_BASED when distance between periodic fix and last fix >= distanceThreshold
    var maxVelocity: Float by remember { mutableFloatStateOf(2f) } // in m/s
        // Rate of updates with MANAGED_PERIODIC equals: distanceThreshold / maxVelocity
    var accelThreshold: Double by remember { mutableDoubleStateOf(10.0) } // in m/s^2
        // Movent with MANAGED_MOVEMENT detected when: acceleration >= accelThreshold


    // Sensor config
    var accelDelay: Int by remember { mutableIntStateOf(SensorManager.SENSOR_DELAY_NORMAL) }
    var positionMinTimeMs: Int by remember { mutableIntStateOf(15000) }
    var positionDistanceM: Float by remember { mutableFloatStateOf(10f) }
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

    // Listeners
    val accelListener = remember {
        object : SensorEventListener {
            var acceleration: Double = 0.0

            @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
            override fun onSensorChanged(event: SensorEvent?) {
                if (event != null) {
                    acceleration = sqrt( // overall acceleration
                        event.values[0].toDouble().pow(2.0) +
                                event.values[1].toDouble().pow(2.0) +
                                event.values[2].toDouble().pow(2.0))

                    // TODO: find reliable method to detect movement (or get reference values)
                    if (acceleration >= accelThreshold) {
                        client.setIsMoving(true)
                    } else {
                        client.setIsMoving(false)
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                generalAccuracyChanged("Accelerometer", accuracy)
            }
        }
    }

    val locationListener: LocationListener = LocationListener { location ->
        client.incFixCount()
        client.reportToServer(
            PositionFix(
                location.latitude.toFloat(),
                location.longitude.toFloat(),
                0f),
            System.currentTimeMillis(),
            currentStrategy // ClientViewModel checks if new report is needed
        )
    }

    // Register/unregister Listeners
    fun registerSensorLister(listener: SensorEventListener, type: Int, delay: Int) {
        try {
            sensorManager.registerListener(listener, sensorManager.getDefaultSensor(
                type), delay)
            Log.d("SensorListenerRegistration", "Sensor listener registered")
        } catch (e: Exception) {
            Log.e("SensorListenerRegistrationError",
                "Failed to register sensor listener: ${e.message}")
        }
    }
    fun unregisterSensorListener(listener: SensorEventListener) {
        try {
            sensorManager.unregisterListener(listener)
            Log.d("SensorListenerRegistration", "Sensor listener unregistered")
        } catch (e: Exception) {
            Log.e("SensorListenerRegistrationError",
                "Failed to unregister sensor listener: ${e.message}")
        }

    }
    @Suppress("MissingPermission")
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
                    Log.w("LocationListenerRegistration",
                        "Implementation for fused location listener was removed")
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
                    Log.d("LocationListenerRegistration",
                        "Implementation for fused location listener was removed")
                }
                else -> Log.e("LocationListenerRegistrationError",
                    "Invalid location method: $method")
            }
        } catch (e: Exception) {
            Log.e("LocationListenerRegistrationError",
                "Failed to unregister location listener: ${e.message}")
        }
    }

    // Change method/strategy
    fun changePositionMethod(method: String) {
        unregisterLocationListener(currentMethod)
        registerLocationListener(method)
        currentMethod = method
    }
    @Suppress("MissingPermission")
    fun changeStrategy(strategy: ReportingStrategies) {
        if (currentMethod != LocationManager.GPS_PROVIDER) {
            changePositionMethod(LocationManager.GPS_PROVIDER)
            Log.w("StrategyChangeWarning", "Location method changed to GPS")
        }
        try {
            periodicJob?.cancel() // End job if already running

            // unregister location and sensor listeners
            unregisterLocationListener(currentMethod)
            unregisterSensorListener(accelListener)
            // unregisterSensorListener(gyroListener)
            // unregisterSensorListener(magnetListener)

            // reset to trigger immediate report at next fix (for DISTANCE_BASED)
            client.setLastSentLocation(null)

            // select strategy
            when (strategy) {
                ReportingStrategies.NONE -> {
                    Log.d("ReportingStrategies", "Selected NONE")
                }
                ReportingStrategies.PERIODIC -> {
                    Log.d("ReportingStrategies", "Selected PERIODIC")
                    periodicJob = scope.launch(Dispatchers.Default) {
                        Log.d("ReportingStrategies", "Periodic job started")
                        while (true) {
                            try {
                                client.incFixCount()
                                locationManager.getCurrentLocation(
                                    currentMethod,
                                    null,
                                    ContextCompat.getMainExecutor(ctx),
                                    { location ->
                                        if (location != null) {
                                            client.reportToServer(
                                                PositionFix(
                                                    location.latitude.toFloat(),
                                                    location.longitude.toFloat(),
                                                    0f
                                                ),
                                                System.currentTimeMillis(),
                                                ReportingStrategies.PERIODIC
                                            )
                                            Log.d("ReportingStrategies", "$location")
                                        }
                                    }
                                )
                            } catch (e: Exception) {
                                Log.e("GetCurrentLocation", "Failed to get location: ${e.message}")
                            }
                            delay(jobDelay)
                        }
                    }
                }
                ReportingStrategies.DISTANCE_BASED -> {
                    Log.d("ReportingStrategies", "Selected DISTANCE_BASED")
                    changePositionMethod(currentMethod)
                    // locationListener callback sends location fixes to client
                    // ClientViewModel checks if new report is needed

                    Log.d("ReportingStrategies", "Distance-based strategy activated")
                }
                ReportingStrategies.MANAGED_PERIODIC -> {
                    Log.d("ReportingStrategies", "Selected MANAGED_PERIODIC")
                    periodicJob = scope.launch(Dispatchers.Default) {
                        var timeToWait: Long = 1000 * (distanceThreshold / maxVelocity).toLong()
                        if (timeToWait < 1000)
                            timeToWait = 1000 //Das ist eine Schutzmaßnahme, damit das System nicht in eine extrem hohe Abfragefrequenz rutscht.
                        // distanceThreshold must not be too low - else fixes are requested too frequently
                        Log.d("ReportingStrategies", "timeToWait: $timeToWait")
                        Log.d("ReportingStrategies", "Periodic job started")
                        while (true) {
                            try {
                                client.incFixCount()
                                locationManager.getCurrentLocation(
                                    currentMethod,
                                    null,
                                    ContextCompat.getMainExecutor(ctx),
                                    { location ->
                                        if (location != null)
                                            client.reportToServer(
                                                PositionFix(
                                                    location.latitude.toFloat(),
                                                    location.longitude.toFloat(),
                                                    0f
                                                ),
                                                System.currentTimeMillis(),
                                                ReportingStrategies.MANAGED_PERIODIC
                                            )
                                    }
                                )
                            } catch (e: Exception) {
                                Log.e("GetCurrentLocation", "Failed to get location: ${e.message}")
                            }
                            delay(timeToWait)
                        }
                    }
                }
                ReportingStrategies.MANAGED_MOVEMENT -> {
                    // GPS fix is only requested when movement is detected
                    // fix is only sent to server if distance to last fix is greater/equal than the threshold
                    Log.d("ReportingStrategies", "Selected MANAGED_MOVEMENT")
                    changePositionMethod(currentMethod)
                    client.setIsMoving(false)
                    registerSensorLister(accelListener,Sensor.TYPE_ACCELEROMETER,
                        SensorManager.SENSOR_DELAY_NORMAL)

                    periodicJob = scope.launch(Dispatchers.Default) {
                        Log.d("ReportingStrategies", "Movement-based job started")
                        while (true) {
                            // accelListener tells ClientViewModel if movement is detected
                            // this job only requests a location while the client thinks it is moving
                            if (client.getIsMoving()) {
                                try {
                                    client.incFixCount()
                                    locationManager.getCurrentLocation(
                                        currentMethod,
                                        null,
                                        ContextCompat.getMainExecutor(ctx),
                                            // determine that this runs on the main or UI thread
                                        { location ->
                                            if (location != null) {
                                                client.reportToServer(
                                                    PositionFix(
                                                        location.latitude.toFloat(),
                                                        location.longitude.toFloat(),
                                                        0f
                                                    ),
                                                    System.currentTimeMillis(),
                                                    ReportingStrategies.MANAGED_MOVEMENT
                                                )
                                            }
                                        }
                                    )
                                } catch (e: Exception) {
                                    Log.e("GetCurrentLocation", "Failed to get location: ${e.message}")
                                }
                            }
                            delay(jobDelay) // sleep
                        }
                    }
                }
                else -> {} // not yet implemented
            }
            currentStrategy = strategy
            Log.d("StrategyChange", "Strategy changed to $strategy")
        } catch (e: Exception) {
            Log.e("StrategyChangeError", "Failed to change strategy: ${e.message}")
        }
    }

    // DisposableEffect
    DisposableEffect(Unit) {
        onDispose {
            Log.d("SensorListenerRegistration", "Disposing of all listeners")
            unregisterSensorListener(accelListener)
            // unregisterSensorListener(gyroListener)
            // unregisterSensorListener(magnetListener)
            unregisterLocationListener(currentMethod)
        }
    }

    // Components
    Column {
        // Reporting strategy selection
        Column(modifier = Modifier.selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(16.dp) // Abstand zwischen allen Items
        ) {
            Text(text = "Reporting strategy auswählen:")
            listOf(
                ReportingStrategies.NONE, // inactive
                ReportingStrategies.PERIODIC, // task 1a)
                ReportingStrategies.DISTANCE_BASED, // task 1b)
                ReportingStrategies.MANAGED_PERIODIC, // task 1c)
                ReportingStrategies.MANAGED_MOVEMENT, // task 1d)
                ReportingStrategies.MANAGED_PLUS_MOVEMENT, // extension of task 1d)
                ReportingStrategies.MANAGED_PLUS_PERIODIC, // extension of task 1c)
            ).forEach { strategy ->
                Row(
                    Modifier.selectable(
                        selected = (strategy == currentStrategy),
                        onClick = {
                            try {
                                changeStrategy(strategy)
                            } catch (e: Exception) {
                                Log.e(
                                    "StrategyChangeError",
                                    "Failed to change strategy: ${e.message}"
                                )
                            }
                        },
                        role = Role.RadioButton
                    )
                ) {
                    RadioButton(
                        selected = (strategy == currentStrategy),
                        onClick = null
                    )
                    Text(text = strategy.desc)
                }
            }
        }
        // Strategy-specific configuration
        Column {
            var periodicText by remember { mutableStateOf(jobDelay.toString()) }
            var distanceText by remember { mutableStateOf(distanceThreshold.toString()) }
            var maxVelocityText by remember { mutableStateOf(maxVelocity.toString()) }
            var accelThresholdText by remember { mutableStateOf(accelThreshold.toString()) }
            when (currentStrategy) {
                ReportingStrategies.NONE -> Row {
                    Text(text = "No strategy selected")
                }
                ReportingStrategies.PERIODIC -> Row {
                    TextField(
                        value = periodicText,
                        onValueChange = { newText ->
                            periodicText = newText.filter { it.isDigit() }
                        },
                        label = { Text("Delay (ms as Long)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    Button(onClick = {
                        val newJobDelay: Long? = periodicText.toLongOrNull()
                        if (newJobDelay != null && newJobDelay >= 500)
                            jobDelay = newJobDelay
                    }) { Text(text = "Set") }
                }
                ReportingStrategies.DISTANCE_BASED,
                ReportingStrategies.MANAGED_PERIODIC,
                ReportingStrategies.MANAGED_MOVEMENT -> Column {
                    Row { // distanceThreshold is shared across 1b), 1c) and 1d)
                        TextField(
                            value = distanceText,
                            onValueChange = { newText ->
                                distanceText = newText.filter { it.isDigit() || it == '.' }
                            },
                            label = { Text("distance in m)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.width(150.dp)
                        )
                        Button(onClick = {
                            val newDistance: Float? = distanceText.toFloatOrNull()
                            if (newDistance != null && newDistance >= 1f) {
                                client.setDistanceThreshold(newDistance)
                                distanceThreshold = newDistance
                                // note: timeToWait in job is only calculated once to avoid unnecessary calculations
                            } else if (newDistance != null) { // min 1 m
                                client.setDistanceThreshold(1f)
                                distanceThreshold = 1f
                                distanceText = 1f.toString()
                            }
                        }) { Text(text = "Set") }
                    }
                    Row { // 1c) and 1d) are extensions of 1b)
                        when (currentStrategy) {
                            ReportingStrategies.MANAGED_PERIODIC -> {
                                TextField(
                                    value = maxVelocity.toString(),
                                    onValueChange = { maxVelocityText = it },
                                    label = { Text("Max velocity (m/s as Float)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true
                                )
                                Button(onClick = {
                                    val newMaxVelocity: Float? = maxVelocityText.toFloatOrNull()
                                    if (newMaxVelocity != null && newMaxVelocity > 0f) {
                                        maxVelocity = newMaxVelocity
                                        changeStrategy(ReportingStrategies.MANAGED_PERIODIC)
                                            // timeToWait in job is only calculated once to avoid unnecessary calculations
                                    } else {
                                        maxVelocityText = maxVelocity.toString()
                                    }
                                }) { Text(text = "Set") }
                            }
                            ReportingStrategies.MANAGED_MOVEMENT -> {
                                TextField(
                                    value = accelThreshold.toString(),
                                    onValueChange = { accelThresholdText = it },
                                    label = { Text("Acceleration threshold (m/s^2 as Double)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true
                                )
                                Button(onClick = {
                                    val newAccelThreshold: Double? = accelThresholdText.toDoubleOrNull()
                                    if (newAccelThreshold != null && newAccelThreshold > 0.0) {
                                        accelThreshold = newAccelThreshold
                                    } else {
                                        accelThresholdText = accelThreshold.toString()
                                    }
                                }) { Text(text = "Set") }
                                Text(text = "Accel: $acceleration")
                            }
                            else -> {}
                        } // inner when
                    }
                }
                else -> Row {
                    Text(text = "Strategy not yet implemented")
                }
            } // outer when
        } // Strategy-specific configuration
    }
}