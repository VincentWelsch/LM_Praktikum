package com.example.praktikum2

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
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
import androidx.core.content.ContextCompat
import com.example.praktikum2.ui.theme.Praktikum2Theme
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlin.math.roundToInt

@RequiresApi(Build.VERSION_CODES.R)
class MainActivity : ComponentActivity() {
    private fun startApplication() {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        enableEdgeToEdge()
        setContent {
            Praktikum2Theme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Application(Modifier.wrapContentHeight().padding(innerPadding),
                        locationManager)
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

@RequiresApi(Build.VERSION_CODES.R)
@Composable
fun Application(modifier: Modifier, locationManager: LocationManager) {
    val ctx = LocalContext.current
    val fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(ctx)
    val collectionModel = CollectionViewModel(LocalContext.current)
    Menu(modifier, locationManager, fusedLocationProviderClient, collectionModel)
}

// Parent composable
@RequiresApi(Build.VERSION_CODES.R)
@Composable
fun Menu(modifier: Modifier,
         locationManager: LocationManager,
         fusedLocationProviderClient: FusedLocationProviderClient,
         collectionModel: CollectionViewModel) {
    // Window selection: SensorConfig and RecordWindow (0) or DisplayWindow (1)
    var window by remember { mutableStateOf(0)}
    // Predetermined routes
    val route1: List<FloatArray> = listOf(floatArrayOf(1f, 2f)) // <- TODO: Add first route using floatArrayOf()
    val route2: List<FloatArray> = listOf() // <- TODO: Add second route using floatArrayOf()
    val route3: List<FloatArray> = listOf() // <- TODO: Add third route using floatArrayOf()
    val ctx: Context = LocalContext.current

    Column(modifier = modifier) {
        Row { // Ground truth selection (), fill width at top of screen
            Button( // Button for selecting route 1
                onClick = {
                    if (route1.isNotEmpty()) {
                        collectionModel.setGroundTruth(route1 as MutableList<FloatArray>)
                    } else {
                        collectionModel.setGroundTruth(emptyList<FloatArray>().toMutableList())
                    }
                    addedGroundTruthToast(ctx, collectionModel.getGroundTruth().size)
            }) { Text("Route 1") }
            Button( // Button for selecting route 2
                onClick = {
                    if (route2.isNotEmpty()) {
                        collectionModel.setGroundTruth(route2 as MutableList<FloatArray>)
                    } else {
                        collectionModel.setGroundTruth(emptyList<FloatArray>().toMutableList())
                    }
                    addedGroundTruthToast(ctx, collectionModel.getGroundTruth().size)
            }) { Text("Route 2") }
            Button( // Button for selecting route 3
                onClick = {
                    if (route3.isNotEmpty()) {
                        collectionModel.setGroundTruth(route3 as MutableList<FloatArray>)
                    } else {
                        collectionModel.setGroundTruth(emptyList<FloatArray>().toMutableList())
                    }
                    addedGroundTruthToast(ctx, collectionModel.getGroundTruth().size)
            }) { Text("Route 3") }
        }
        Row { // Window selection, fill width below ground truth selection
            Button(onClick = { window = 0 }) { Text("Record") }
            Button(onClick = { window = 1 }) { Text("Display") }
        }
        Row { // Window content, remaining space
            when (window) {
                0 -> { // Configure sensors, check, store, load, and clear collection
                    Column {
                        /* Record button needs currentMethod and is therefore placed at the bottom
                           of SensorConfig. Ideally, the button will reside center of screen for
                           easy access while walking the routes. */
                        SensorConfig(modifier, locationManager, fusedLocationProviderClient, collectionModel)
                        RecordWindow(modifier, collectionModel)
                    }
                }
                1 -> {
                    // DisplayWindow(collectionModel) // TODO: define DisplayWindow
                }
            }
        }
    }
}

// Visual feedback
fun actionFailedToast(context: Context) {
    val toastText = "Action failed"
    Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
}

fun noTitleToast(context: Context) {
    val toastText = "No title was provided"
    Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
}

fun addedWaypointToast(context: Context, longitude: Float, latitude: Float) {
    val toastTest = "Added waypoint at $latitude, $longitude"
    Toast.makeText(context, toastTest, Toast.LENGTH_SHORT).show()
}

fun foundMeasurementsToast(context: Context, mc: Int, wc: Int) {
    val toastText = "Found $mc measurements and $wc waypoints"
    Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
}

fun addedGroundTruthToast(context: Context, gc: Int) {
    val toastText = "$gc points added to ground truth"
    Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
}

// First child composable of menu
@Composable
fun RecordWindow(modifier: Modifier, collectionModel: CollectionViewModel) {
    var title by remember { mutableStateOf("Run1") }
    val ctx = LocalContext.current
    /* Layout (fill width):
         TextField
        Check Store
        Clear Load */
    Column(modifier) {
        // Input run or file name
        TextField(value = title, onValueChange = { value -> title = value })
        Row { // First row: Check and Store
            // Check for data in collectionModel
            Button(onClick = {
                foundMeasurementsToast(
                    ctx,
                    collectionModel.getMeasurementsCount(),
                    collectionModel.getWaypointsCount()
                )
            }) { Text("Check") }
            // Store data in "$title.json"
            Button(onClick = {
                if (title.isEmpty()) {
                    noTitleToast(ctx)
                } else {
                    if (!collectionModel.storeCollection(title)) {
                        actionFailedToast(ctx)
                    } else {
                        foundMeasurementsToast(
                            ctx,
                            collectionModel.getMeasurementsCount(),
                            collectionModel.getWaypointsCount()
                        )
                    }
                }
            }) { Text("Store") }
        }
        Row { // Second row: Clear and Load
            // Clear data from collectionModel
            Button(onClick = {
                val mwc: Array<Int> = collectionModel.clearCollection()
                if (mwc.size > 1) {
                    foundMeasurementsToast(ctx, mwc[0], mwc[1])
                }
            }) { Text("Clear") }
            // Load data from "$title.json"
            Button(onClick = {
                if (title.isEmpty()) {
                    noTitleToast(ctx)
                } else {
                    if (!collectionModel.loadCollection(title)) {
                        actionFailedToast(ctx)
                    } else {
                        foundMeasurementsToast(
                            ctx,
                            collectionModel.getMeasurementsCount(),
                            collectionModel.getWaypointsCount())
                    }
                }
            }) { Text("Load") }
        }
    }
}

@Composable
fun PositionControl(
    modifier: Modifier = Modifier,
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
    // https://developer.android.com/develop/ui/compose/components/radio-button?hl=de
    // Radio Buttons to choose method for determining position
    Column(modifier) { // All
        Column(modifier.selectableGroup()) { // Method
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

        if (currentMethod != "fused") { // Config for gps and network
            Column(modifier) {
                Text(text = "Minimum time in ms between updates: $positionMinTimeMs")
                Slider(
                    value = positionMinTimeMs.toFloat(),
                    steps = 59, // 0 -> 59 steps (each +1000 increment) -> 60000
                    onValueChange = { onPositionMinTimeMsChange(it.roundToInt()) },
                    onValueChangeFinished = onValueChangeFinished,
                    valueRange = 0f..60000f
                )
                Text(text = "Minimum distance in m between updates: $positionDistanceM")
                Slider(
                    value = positionDistanceM,
                    steps = 99, // 0 -> 99 steps (each +1 increment) -> 100
                    onValueChange = onPositionDistanceMChange,
                    onValueChangeFinished = onValueChangeFinished,
                    valueRange = 0f..100f
                )
            }
        } else { // Config for fused
            Column(modifier) {
                Text(text = "Position priority: $positionPriority")
                Slider(
                    value = positionPriority.toFloat(),
                    steps = 4, // 100 -> step -> step -> step -> step -> 105
                    onValueChange = onPositionPriorityChange,
                    onValueChangeFinished = onValueChangeFinished,
                    valueRange = 100f..105f
                ) // 103 and 101 do not exist
                Text(text = "Position interval in ms: $positionIntervalMs")
                Slider(
                    value = positionIntervalMs.toFloat(),
                    steps = 199, // 0 -> 199 steps (each +100 increment) -> 20000
                    onValueChange = { onPositionIntervalMsChange(it.roundToInt()) },
                    onValueChangeFinished = onValueChangeFinished,
                    valueRange = 0f..20000f
                )
            }
        }
    }
}

// Children of RecordWindow
@RequiresApi(Build.VERSION_CODES.R)
@Composable
fun SensorConfig(
    modifier: Modifier,
    locationManager: LocationManager,
    fusedLocationProviderClient: FusedLocationProviderClient,
    collectionModel: CollectionViewModel) {
    // gps, network, or fused
    var currentMethod: String by remember { mutableStateOf(LocationManager.GPS_PROVIDER) }
    // for gps and network
    var positionPriority: Int by remember { mutableIntStateOf(Priority.PRIORITY_BALANCED_POWER_ACCURACY) }
    var positionIntervalMs: Int by remember { mutableIntStateOf(1000) }
    // for fused
    var positionMinTimeMs: Int by remember { mutableIntStateOf(1000) }
    var positionDistanceM: Float by remember { mutableFloatStateOf(10f) }

    // Init position event listeners
    val locationListener: LocationListener = remember {
        LocationListener { location ->
            collectionModel.addMeasurement(location.longitude.toFloat(),
                location.latitude.toFloat())
        }
    }

    // locationCallback for fused location provider always the same -> outside registerLocationListener
    val locationCallback = remember {
        object: LocationCallback() {
            override fun onLocationResult(p0: LocationResult) {
                super.onLocationResult(p0)
                if (p0.lastLocation != null) {
                    collectionModel.addMeasurement(p0.lastLocation!!.longitude.toFloat(),
                        p0.lastLocation!!.latitude.toFloat())
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

    /*DisposableEffect(Unit) {
        onDispose {
            Log.d("SensorListenerRegistration", "Disposing of all listeners")
            unregisterLocationListener(currentMethod)
        }
    }*/
    Column(modifier) {
        PositionControl(
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

        // Display current count of measurements and waypoints
        val ctx: Context = LocalContext.current
        var takeNew: Boolean by remember { mutableStateOf(collectionModel.getTakesNew()) }
        Text("Since last update")
        Button(onClick = {
            if (!takeNew) return@Button
            if (currentMethod != "fused") {
                // Request single location for "gps" or "network"
                @SuppressLint("MissingPermission")
                locationManager.getCurrentLocation(
                    currentMethod,
                    null,
                    ContextCompat.getMainExecutor(ctx),
                    { location ->
                        if (location != null) {
                            collectionModel.addWaypoint(location.longitude.toFloat(),
                                location.latitude.toFloat())
                            addedWaypointToast(ctx, location.longitude.toFloat(),
                                location.latitude.toFloat())
                        } else {
                            actionFailedToast(ctx)
                        }
                    }
                )
            } else {
                // Request single location for "fused"
                @SuppressLint("MissingPermission")
                fusedLocationProviderClient.getCurrentLocation(
                    positionPriority,
                    CancellationTokenSource().token).addOnSuccessListener { location ->
                    if (location != null) {
                        collectionModel.addWaypoint(location.longitude.toFloat(),
                            location.latitude.toFloat())
                        addedWaypointToast(ctx, location.longitude.toFloat(),
                            location.latitude.toFloat())
                    }
                }.addOnFailureListener { exception ->
                    Log.e("GetNextWaypoint", "$exception")
                    actionFailedToast(ctx)
                }
            }
        }) { Text("Next Waypoint") }
        Row {
            // Toggle if new data can be added to collectionModel
            Text("Allow new")
            Switch(checked = takeNew,
                // Presumably, this is not updated if takeNew collectionModel is updated from elsewhere
                onCheckedChange = { checked ->
                    collectionModel.setTakesNew(checked)
                    takeNew = checked
                })
        }
    }
}
