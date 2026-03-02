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
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.serialization.json.Json

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
    Json.encodeToString(PositionFix(51.482f, 7.217f, 0f, true))
    val ctx = LocalContext.current
    val client: ClientViewModel = viewModel(
        // Clean way of creating a persistent view model
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            // Pass the application context to avoid memory leaks
            return ClientViewModel(ctx.applicationContext) as T
        } })
    // ClientViewModel is only created when none exists
    // Should finally prevent loss of run data when flipping the phone

    // ViewModels should not call UI events
    // Instead client sends an intended message to be displayed as a toast
    LaunchedEffect(Unit) {client.uiEvent.collect { message: String ->
        Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
    } }

    Column (
        modifier = Modifier
            .padding(innerPadding)
            .fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        var display: String by rememberSaveable { mutableStateOf("map") }
        Row{ // Nav bar
            Button(onClick = { display = "rec" }) {
                Text(text = "Record")
            }
            Button(onClick = { display = "disp" }) {
                Text(text = "Display")
            }

        }
        Spacer(modifier = Modifier.height(16.dp))
        when (display) { // Content
            "rec" -> SensorConfig(
                sensorManager = sensorManager,
                locationManager = locationManager,
                client = client,
                ctx = ctx)
            "disp" -> site_map(client)
        }
    }
}

@Composable
fun SensorConfig(sensorManager: SensorManager, locationManager: LocationManager,
                 client: ClientViewModel, ctx: Context
) {
    // State
    var currentMethod: String by rememberSaveable { mutableStateOf(LocationManager.GPS_PROVIDER) }
    var currentStrategy: ReportingStrategies by rememberSaveable { mutableStateOf(ReportingStrategies.NONE) }
    var acceleration: Double by rememberSaveable { mutableDoubleStateOf(0.0) }
    var accelerationTextColor: Color by remember { mutableStateOf(Color.DarkGray) }
    var periodicJob: Job? by rememberSaveable { mutableStateOf(null) }
    val scope = rememberCoroutineScope()

    // Strategy Config
    var jobDelay: Long by rememberSaveable { mutableLongStateOf(client.getJobDelay()) }
        // Rate of updates with PERIODIC equals: 1 / jobDelay
    var distanceThreshold: Float by rememberSaveable { mutableFloatStateOf(client.getDistanceThreshold()) } // in m
        // Updates with DISTANCE_BASED when distance between periodic fix and last fix >= distanceThreshold
    var maxVelocity: Float by rememberSaveable { mutableFloatStateOf(client.getMaxVelocity()) } // in m/s
        // Rate of updates with MANAGED_PERIODIC equals: distanceThreshold / maxVelocity
    var accelThreshold: Double by rememberSaveable { mutableDoubleStateOf(client.getAccelThreshold()) } // in m/s^2
        // Movent with MANAGED_MOVEMENT detected when: acceleration >= accelThreshold


    // Sensor config
    var accelDelay: Int by rememberSaveable { mutableIntStateOf(SensorManager.SENSOR_DELAY_NORMAL) }
    var positionMinTimeMs: Int by rememberSaveable { mutableIntStateOf(15000) }
    var positionDistanceM: Float by rememberSaveable { mutableFloatStateOf(10f) }
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
        // during movement, acceleration might drop below threshold in small intervals
        // isMoving should therefore remain true for some time and not be immediately set to false
        val MIN_IS_MOVING_DUATION: Int = 50
        var minIsMovingDuration: Int = MIN_IS_MOVING_DUATION
        // SENSOR_DELAY_NORMAL is 0.2 s delay
        // with minIsMovingDuration set to 50, isMoving should remain true for at least 10 s
        // however, at least the debug text is displayed as green for much shorter

        object : SensorEventListener {
            @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
            override fun onSensorChanged(event: SensorEvent?) {
                if (event != null) {
                    acceleration = sqrt( // calculate overall acceleration
                        event.values[0].toDouble().pow(2.0) +
                                event.values[1].toDouble().pow(2.0) +
                                event.values[2].toDouble().pow(2.0))

                    if (acceleration >= accelThreshold) { // check if above threshold
                        minIsMovingDuration = MIN_IS_MOVING_DUATION // still moving -> reset timer
                        client.setIsMoving(true)
                        accelerationTextColor = Color.Green
                    } else {
                        minIsMovingDuration -= 1 // count down in 0.2 s increments
                        if (minIsMovingDuration <= 0) { // if below threshold for long enough
                            // set isMoving to false
                            client.setIsMoving(false)
                            accelerationTextColor = Color.DarkGray
                        }
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
            unregisterLocationListener(currentMethod)
            currentMethod = LocationManager.GPS_PROVIDER
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
        StrategySelection(client, currentStrategy, ::changeStrategy)
        Spacer(modifier = Modifier.height(16.dp))
        StrategyConfiguration(client, currentStrategy, ::changeStrategy)
        if (currentStrategy == ReportingStrategies.MANAGED_MOVEMENT || currentStrategy == ReportingStrategies.MANAGED_PLUS_MOVEMENT)
            Text(text = "Accel $acceleration", color = accelerationTextColor)
    }
}

@Composable
fun StrategySelection(client: ClientViewModel,
                      currentStrategy: ReportingStrategies,
                      changeStrategy: (ReportingStrategies) -> Unit) {
    // Reporting strategy selection
    Column(
        modifier = Modifier.selectableGroup().background(Color.DarkGray),
        verticalArrangement = Arrangement.spacedBy(16.dp) // Abstand zwischen allen Items
    ) {
        Text(text = "Select reporting strategy:")
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
}

@Composable
fun StrategyConfiguration(client: ClientViewModel,
                          currentStrategy: ReportingStrategies,
                          changeStrategy: (ReportingStrategies) -> Unit) {
    // Strategy-specific configuration
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Column(
            modifier = Modifier.width(330.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            var periodicText by rememberSaveable { mutableStateOf(client.getJobDelay().toString()) }
            var distanceText by rememberSaveable {
                mutableStateOf(
                    client.getDistanceThreshold().toString()
                )
            }
            var maxVelocityText by rememberSaveable {
                mutableStateOf(
                    client.getMaxVelocity().toString()
                )
            }
            var accelThresholdText by rememberSaveable {
                mutableStateOf(
                    client.getAccelThreshold().toString()
                )
            }
            var runId by remember { mutableStateOf("") }
            when (currentStrategy) {
                ReportingStrategies.NONE -> Column {
                    Row { // Run ID input
                        TextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = runId,
                            onValueChange = { newText ->
                                runId = newText
                            },
                            label = { Text("Run ID") },
                            singleLine = true
                        )
                    }
                    Row { // Run control
                        Button(
                            modifier = Modifier.width(110.dp),
                            onClick = {
                                client.clearRun()
                            }) { Text(text = "Clear run") }
                        Button(
                            modifier = Modifier.width(110.dp),
                            onClick = {
                                client.loadRun(runId)
                            }) { Text(text = "Load run") }
                        Button(
                            modifier = Modifier.width(110.dp),
                            onClick = {
                                client.storeRun(runId)
                            }) { Text(text = "Store run") }
                    }
                    Row { // Get fix count
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                client.showFixCounts()
                            }
                        ) { Text(text = "Get fix count") }
                    }
                    Row { // Reset strategy vars
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                changeStrategy(ReportingStrategies.NONE)
                                client.resetStrategyVars()
                                periodicText = client.getJobDelay().toString()
                                distanceText = client.getDistanceThreshold().toString()
                                maxVelocityText = client.getMaxVelocity().toString()
                                accelThresholdText = client.getAccelThreshold().toString()
                            }) { Text(text = "Restore default strategy vars") }
                    }
                }

                ReportingStrategies.PERIODIC -> Row {
                    TextField(
                        value = periodicText,
                        onValueChange = { newText ->
                            periodicText = newText.filter { it.isDigit() }
                        },
                        label = { Text("Delay (ms as Long)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.width(250.dp),
                    )
                    Button(
                        modifier = Modifier.width(80.dp),
                        onClick = {
                            val newJobDelay: Long? = periodicText.toLongOrNull()
                            if (newJobDelay != null) {
                                client.setJobDelay(newJobDelay)
                            }
                            periodicText = client.getJobDelay().toString()
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
                            label = { Text("Distance threshold (in m as Float)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.width(250.dp),
                        )
                        Button(
                            modifier = Modifier.width(80.dp),
                            onClick = {
                                val newDistance: Float? = distanceText.toFloatOrNull()
                                if (newDistance != null) {
                                    client.setDistanceThreshold(newDistance)
                                    if (currentStrategy == ReportingStrategies.MANAGED_PERIODIC)
                                        changeStrategy(ReportingStrategies.MANAGED_PERIODIC)
                                    // recalculate timeToWait
                                    // timeToWait in job is only calculated once to avoid unnecessary calculations
                                }
                                distanceText = client.getDistanceThreshold().toString()
                            }) { Text(text = "Set") }
                    }
                    Row { // 1c) and 1d) are extensions of 1b)
                        when (currentStrategy) {
                            ReportingStrategies.MANAGED_PERIODIC -> {
                                TextField(
                                    value = client.getMaxVelocity().toString(),
                                    onValueChange = { newText ->
                                        maxVelocityText = newText.filter { it.isDigit() }
                                    },
                                    label = { Text("Max velocity (m/s as Float)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.width(250.dp),
                                )
                                Button(
                                    modifier = Modifier.width(80.dp),
                                    onClick = {
                                        val newMaxVelocity: Float? = maxVelocityText.toFloatOrNull()
                                        if (newMaxVelocity != null) {
                                            client.setMaxVelocity(newMaxVelocity)
                                            changeStrategy(ReportingStrategies.MANAGED_PERIODIC)
                                            // recalculate timeToWait
                                            // timeToWait in job is only calculated once to avoid unnecessary calculations
                                        }
                                        maxVelocityText = client.getMaxVelocity().toString()
                                    }) { Text(text = "Set") }
                            }

                            ReportingStrategies.MANAGED_MOVEMENT -> {
                                TextField(
                                    value = client.getAccelThreshold().toString(),
                                    onValueChange = { newText ->
                                        accelThresholdText = newText.filter { it.isDigit() }
                                    },
                                    label = { Text("Acceleration threshold (m/s^2 as Double)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.width(250.dp),
                                )
                                Button(
                                    modifier = Modifier.width(80.dp),
                                    onClick = {
                                        val newAccelThreshold: Double? =
                                            accelThresholdText.toDoubleOrNull()
                                        if (newAccelThreshold != null) {
                                            client.setAccelThreshold(newAccelThreshold)
                                        }
                                        accelThresholdText = client.getAccelThreshold().toString()
                                    }) { Text(text = "Set") }
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


@Composable
fun site_map(client: ClientViewModel) {
    // Holt das array der Positionfixes
    val fixes = client.getLocalFixes()
    OsmMapScreen(fixes = fixes)
}

@Composable
fun OsmMapScreen(fixes: Array<PositionFix>) {
    val ctx = LocalContext.current

    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(600.dp)
            .clip(RectangleShape),
        factory = { context ->
            org.osmdroid.views.MapView(context).apply {
                setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
                controller.setZoom(15.0)
                setMultiTouchControls(true)
                controller.setCenter(org.osmdroid.util.GeoPoint(51.482582, 7.217153))
            }
        },
        update = { mapView ->
            if (fixes.isNotEmpty()) {
                mapView.overlays.clear()

                fixes.forEach { fix ->
                    val geoPoint = org.osmdroid.util.GeoPoint(fix.latitude.toDouble(), fix.longitude.toDouble())
                    val marker = org.osmdroid.views.overlay.Marker(mapView)
                    marker.position = geoPoint

                    val icon = marker.icon

                    // Farbe basierend auf 'wasReported' setzen
                    if (fix.wasReported) {
                        // Grün für gemeldete Fixes
                        icon.setTint(android.graphics.Color.GREEN)
                        marker.title = "Gesendet"
                    } else {
                        // Orange für nicht gemeldete Fixe
                        icon.setTint(android.graphics.Color.rgb(255, 165, 0))
                        marker.title = "Nicht gesendet"
                    }

                    marker.icon = icon
                    marker.setAnchor(
                        org.osmdroid.views.overlay.Marker.ANCHOR_CENTER,
                        org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM
                    )
                    mapView.overlays.add(marker)
                }

                // Auf den neuesten Fix zentrieren
                val latestFix = fixes.last()
                mapView.controller.animateTo(
                    org.osmdroid.util.GeoPoint(latestFix.latitude.toDouble(), latestFix.longitude.toDouble())
                )

                mapView.invalidate()
            }
        }
    )
}