package com.example.praktikum2

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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Checkbox
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import com.example.praktikum2.ui.theme.Praktikum2Theme
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlin.math.roundToInt


class MainActivity : ComponentActivity() {
    private fun startApplication() {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        val pointsModel = PointsViewModel()

        enableEdgeToEdge()
        setContent {
            Praktikum2Theme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Application(locationManager, pointsModel)
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
        enableEdgeToEdge()

        // Get requested permissions from AndroidManifest.xml
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
                startApplication()
            }
        } catch (e: Exception) {
            Log.e("PermissionError", "Failed to get permissions: ${e.message}")
        }

    }
}

@Composable
fun Application(locationManager: LocationManager,
                pointsModel: PointsViewModel) {
    val ctx = LocalContext.current
    val fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(ctx)
    SensorConfig(locationManager, fusedLocationProviderClient)
}

@Composable
fun SensorConfig(// modifier: Modifier = Modifier,
    locationManager: LocationManager,
    fusedLocationProviderClient: FusedLocationProviderClient) {
    // https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview?hl=de#sensors-identify
    // Logic
    // Variables
    var positionChecked: Boolean by remember { mutableStateOf(false)}
    var currentMethod: String by remember { mutableStateOf(LocationManager.GPS_PROVIDER) }

    var positionMinTimeMs: Int by remember { mutableIntStateOf(15000) }
    var positionDistanceM: Float by remember { mutableFloatStateOf(10f) }
    var positionPriority: Int by remember { mutableIntStateOf(Priority.PRIORITY_BALANCED_POWER_ACCURACY) }
    var positionIntervalMs: Int by remember { mutableIntStateOf(10000) }

    // Init position event listeners
    val locationListener: LocationListener = remember {
        LocationListener { location ->
            viewModel.onNewPositionData(floatArrayOf(location.longitude.toFloat(), location.latitude.toFloat()))
        }
    }

    // locationCallback for fused location provider always the same -> outside registerLocationListener
    val locationCallback = remember {
        object: LocationCallback() {
            override fun onLocationResult(p0: LocationResult) {
                super.onLocationResult(p0)
                if (p0.lastLocation != null) {
                    viewModel.onNewPositionData(floatArrayOf(p0.lastLocation!!.longitude.toFloat(),
                        p0.lastLocation!!.latitude.toFloat()))
                }
            }
        }
    }

    // Register/unregister
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

    DisposableEffect(Unit) {
        onDispose {
            Log.d("SensorListenerRegistration", "Disposing of all listeners")
            unregisterLocationListener(currentMethod)
        }
    }

    PositionControl(
        isChecked = positionChecked,
        onCheckedChange = { checked ->
            positionChecked = checked
        },
        isEnabled = allSensorSwitchesEnabled,
        currentMethod = currentMethod,
        changePositionMethod = { method -> changePositionMethod(method) },
        onValueChangeFinished = {
            unregisterLocationListener(currentMethod)
            registerLocationListener(currentMethod)
        },
        positionMinTimeMs = positionMinTimeMs,
        onPositionMinTimeMsChange = { positionMinTimeMs = it },
        positionDistanceM = positionDistanceM,
        onPositionDistanceMChange = { positionDistanceM = it },
        positionPriority = positionPriority,
        onPositionPriorityChange = { newValue ->
            positionPriority = when {
                newValue <= 100f -> Priority.PRIORITY_HIGH_ACCURACY // 100
                newValue <= 102f -> Priority.PRIORITY_BALANCED_POWER_ACCURACY // 102
                newValue <= 104f -> Priority.PRIORITY_LOW_POWER // 104
                else -> Priority.PRIORITY_PASSIVE // 105
            } // 103 and 101 do not exist
        },
        positionIntervalMs = positionIntervalMs,
        onPositionIntervalMsChange = { positionIntervalMs = it }
        )
    }
}

@Composable
fun PositionControl(
    modifier: Modifier = Modifier,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isEnabled: Boolean,
    currentMethod: String,
    changePositionMethod: (String) -> Unit,
    onValueChangeFinished: () -> Unit,
    positionMinTimeMs: Int,
    onPositionMinTimeMsChange: (Int) -> Unit,
    positionDistanceM: Float,
    onPositionDistanceMChange: (Float) -> Unit,
    positionPriority: Int,
    onPositionPriorityChange: (Float) -> Unit,
    positionIntervalMs: Int,
    onPositionIntervalMsChange: (Int) -> Unit,
) {
    Column {
        Row { // Checkbox
            Checkbox(
                checked = isChecked,
                enabled = isEnabled,
                onCheckedChange = onCheckedChange
            )
            Text(text = "Position")
        }

        // https://developer.android.com/develop/ui/compose/components/radio-button?hl=de
        // Radio Buttons to choose method for determining position
        if (isChecked) {
            Row {
                Column(modifier.selectableGroup()) {
                    listOf(
                        LocationManager.GPS_PROVIDER,
                        LocationManager.NETWORK_PROVIDER,
                        "fused"
                    ).forEach { method ->
                        Row(
                            Modifier.selectable(
                                selected = (method == currentMethod),
                                onClick = {
                                    try {
                                        changePositionMethod(method)
                                    } catch (e: Exception) {
                                        Log.e(
                                            "MethodChangeError",
                                            "Failed to change method for position service: ${e.message}"
                                        )
                                    }
                                },
                                role = Role.RadioButton
                            )
                        ) {
                            RadioButton(
                                selected = (method == currentMethod),
                                onClick = null
                            )
                            Text(text = method)
                        }
                    }
                }
            }
        }

        if (isChecked && currentMethod != "fused") { // gps or network
            Row {
                Column {
                    Row {
                        Text(text = "Minimum time in ms between updates: $positionMinTimeMs")
                    }
                    Row {
                        Slider(
                            value = positionMinTimeMs.toFloat(),
                            modifier = modifier.weight(1f),
                            steps = 59, // 0 -> 59 steps (each +1000 increment) -> 60000
                            onValueChange = { onPositionMinTimeMsChange(it.roundToInt()) },
                            onValueChangeFinished = onValueChangeFinished,
                            valueRange = 0f..60000f
                        )
                    }
                    Row {
                        Text(text = "Minimum distance in m between updates: $positionDistanceM")
                    }
                    Row {
                        Slider(
                            value = positionDistanceM,
                            modifier = modifier.weight(1f),
                            steps = 99, // 0 -> 99 steps (each +1 increment) -> 100
                            onValueChange = onPositionDistanceMChange,
                            onValueChangeFinished = onValueChangeFinished,
                            valueRange = 0f..100f
                        )
                    }
                }
            }
        }

        if (isChecked && currentMethod == "fused") { // fused
            Row {
                Column {
                    Row {
                        Text(text = "Position priority: $positionPriority")
                    }
                    Row {
                        Slider(
                            value = positionPriority.toFloat(),
                            modifier = modifier.weight(1f),
                            steps = 4, // 100 -> step -> step -> step -> step -> 105
                            onValueChange = onPositionPriorityChange,
                            onValueChangeFinished = onValueChangeFinished,
                            valueRange = 100f..105f
                        ) // 103 and 101 do not exist
                    }
                    Row {
                        Text(text = "Position interval in ms: $positionIntervalMs")
                    }
                    Row {
                        Slider(
                            value = positionIntervalMs.toFloat(),
                            modifier = modifier.weight(1f),
                            steps = 199, // 0 -> 199 steps (each +100 increment) -> 20000
                            onValueChange = { onPositionIntervalMsChange(it.roundToInt()) },
                            onValueChangeFinished = onValueChangeFinished,
                            valueRange = 0f..20000f
                        )
                    }
                }
            }
        }
    }
}