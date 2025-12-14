@file:Suppress("COMPOSE_APPLIER_CALL_MISMATCH")

package com.example.praktikum2

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.preference.PreferenceManager
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import java.lang.Thread.sleep

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import androidx.compose.foundation.layout.Spacer

@RequiresApi(Build.VERSION_CODES.R)
class MainActivity : ComponentActivity() {
    private fun startApplication() {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        // https://stackoverflow.com/a/19253868
        val clipboard: ClipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        enableEdgeToEdge()
        setContent {
            Praktikum2Theme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Application(Modifier
                        .wrapContentHeight()
                        .padding(innerPadding),
                        locationManager,
                        clipboard)
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
        val ctx = applicationContext
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))
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
fun Application(modifier: Modifier, locationManager: LocationManager, clipboard: ClipboardManager) {
    val ctx = LocalContext.current
    val fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(ctx)
    val collectionModel = CollectionViewModel(LocalContext.current, clipboard)
    val errorModel = PositionErrorViewModel(collectionModel)
    Menu(modifier, locationManager, fusedLocationProviderClient, collectionModel, errorModel)
}

// Parent composable
@RequiresApi(Build.VERSION_CODES.R)
@Composable
fun Menu(modifier: Modifier,
         locationManager: LocationManager,
         fusedLocationProviderClient: FusedLocationProviderClient,
         collectionModel: CollectionViewModel,
         errorModel: PositionErrorViewModel) {
    // Window selection: SensorConfig and RecordWindow (0) or DisplayWindow (1)
    var window by remember { mutableIntStateOf(0)}
    // Predetermined routes
    val route1: List<FloatArray> = listOf(
        floatArrayOf(51.44785f, 7.27073f),
        floatArrayOf(51.44755f, 7.27099f),
        floatArrayOf(51.4473f, 7.2712f),
        floatArrayOf(51.44725f, 7.27105f),
        floatArrayOf(51.44713f, 7.27116f),
        floatArrayOf(51.44717f, 7.27131f),
        floatArrayOf(51.44696f, 7.27148f),
        floatArrayOf(51.44676f, 7.27166f),
        floatArrayOf(51.44651f, 7.27188f),
        floatArrayOf(51.44629f, 7.27206f),
        floatArrayOf(51.44616f, 7.27221f),
        floatArrayOf(51.44584f, 7.27275f),
        floatArrayOf(51.44624f, 7.27281f),
        floatArrayOf(51.44629f, 7.27294f),
        floatArrayOf(51.44659f, 7.27272f),
        floatArrayOf(51.44687f, 7.27247f),
        floatArrayOf(51.44718f, 7.2722f),
        floatArrayOf(51.44727f, 7.27253f),
        floatArrayOf(51.44759f, 7.27224f),
        floatArrayOf(51.44789f, 7.27198f)
    )
    val route2: List<FloatArray> = listOf(
        floatArrayOf(51.44631f,7.26073f),
        floatArrayOf(51.4463f,7.26039f),
        floatArrayOf(51.44624f,7.26018f),
        floatArrayOf(51.44591f,7.26009f),
        floatArrayOf(51.44584f,7.25988f),
        floatArrayOf(51.44582f,7.25979f),
        floatArrayOf(51.44542f,7.26004f),
        floatArrayOf(51.44516f,7.26006f),
        floatArrayOf(51.44491f,7.26005f),
        floatArrayOf(51.44465f,7.26021f),
        floatArrayOf(51.44436f,7.26052f),
        floatArrayOf(51.44414f,7.26075f),
        floatArrayOf(51.4439f,7.26097f),
        floatArrayOf(51.44361f,7.26125f),
        floatArrayOf(51.44345f,7.26154f),
        floatArrayOf(51.44316f,7.26183f),
        floatArrayOf(51.44284f,7.26219f),
        floatArrayOf(51.4431f,7.2629f),
        floatArrayOf(51.44343f,7.26264f),
        floatArrayOf(51.44379f,7.2624f)
    )
    val ctx: Context = LocalContext.current

    Column(modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Row { // Ground truth selection (), fill width at top of screen
            Button( // Button for selecting route 1
                onClick = {
                    if (route1.isNotEmpty()) {
                        collectionModel.setGroundTruth(route1 as MutableList<FloatArray>)
                    } else {
                        collectionModel.setGroundTruth(emptyList<FloatArray>().toMutableList())
                    }
                    addedGroundTruthToast(ctx, collectionModel.getGroundTruth().size)
            }) { Text("Route 1 - HSBO") }
            Button( // Button for selecting route 2
                onClick = {
                    if (route2.isNotEmpty()) {
                        collectionModel.setGroundTruth(route2 as MutableList<FloatArray>)
                    } else {
                        collectionModel.setGroundTruth(emptyList<FloatArray>().toMutableList())
                    }
                    addedGroundTruthToast(ctx, collectionModel.getGroundTruth().size)
            }) { Text("Route 2 - RUB") }
        }
        Row { // Window selection, fill width below ground truth selection
            Button(onClick = { window = 0 }) { Text("Record") }
            Button(onClick = { window = 1 }) { Text("Display") }
            Button(onClick = { window = 2 }) { Text("Analyze") }
        }
        Row { // Window content, remaining space
            when (window) {
                0 -> { // Configure sensors, check, store, load, and clear collection
                    Column {
                        /* Record button needs currentMethod and is therefore placed at the bottom
                           of SensorConfig. Ideally, the button will reside center of screen for
                           easy access while walking the routes. */
                        SensorConfig(locationManager, fusedLocationProviderClient, collectionModel)
                        RecordWindow(collectionModel)
                    }
                }
                1 -> {
                    DisplayWindow(collectionModel)
                }
                2 -> {
                    AnalyzeWindow(errorModel)
                }
            }
        }
    }
}
fun convertFloatToGeoPoint(list: List<FloatArray>): List<GeoPoint> {
    val geoPointList = mutableListOf<GeoPoint>()
    for (point in list) {
        geoPointList.add(GeoPoint(point[0].toDouble(), point[1].toDouble()))
    }
    return geoPointList
}

fun convertGeoPointToFloat(list: List<GeoPoint>): List<FloatArray> {
    val floatList = mutableListOf<FloatArray>()
    for (point in list) {
        floatList.add(floatArrayOf(point.latitude.toFloat(), point.longitude.toFloat()))
    }
    return floatList
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

fun addedWaypointToast(context: Context) {
    val toastTest = "Added waypoint"
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
fun RecordWindow(collectionModel: CollectionViewModel, modifier: Modifier = Modifier) {
    var title by remember { mutableStateOf("Run1") }
    var jsonInput by remember { mutableStateOf("") }
    val ctx = LocalContext.current
    /* Layout (fill width):
         TextField
        Check Store
        Clear Load */
    Column(modifier) {
        // Input run or file name
        TextField(value = title, onValueChange = { value -> title = value }, label = { Text("Title") })
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
                    collectionModel.storeCollection(title)
                    foundMeasurementsToast(
                        ctx,
                        collectionModel.getMeasurementsCount(),
                        collectionModel.getWaypointsCount())
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
                    collectionModel.loadCollection(title)
                    sleep(500)
                    foundMeasurementsToast(
                        ctx,
                        collectionModel.getMeasurementsCount(),
                        collectionModel.getWaypointsCount())
                }
            }) { Text("Load") }
        }
        Column {
            TextField(value = jsonInput, label = { Text("JSON") },
                onValueChange = { value -> jsonInput = value })
            Button(onClick = {
                collectionModel.loadCollectionFromJson(jsonInput)
                sleep(500)
                foundMeasurementsToast(
                    ctx,
                    collectionModel.getMeasurementsCount(),
                    collectionModel.getWaypointsCount())
            }) { Text("Load from string") }
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
                // LocationManager.NETWORK_PROVIDER, // temporarily disabled for Praktikum2
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
            /* Column() {
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
            } */ // temporarily disabled for Praktikum2
        } else { // Config for fused
            /*Column() {
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
            }*/ // temporarily disabled for Praktikum2
            Column() {
                Text(text = "Position priority: $positionPriority")
                Slider(
                    value = positionPriority.toFloat(),
                    steps = 1, // 100 -> step -> 102
                    onValueChange = onPositionPriorityChange,
                    onValueChangeFinished = onValueChangeFinished,
                    valueRange = 100f..102f
                ) // Copy of the above with less options
            }
        }
    }
}

// Children of RecordWindow
@RequiresApi(Build.VERSION_CODES.R)
@Composable
fun SensorConfig(
    locationManager: LocationManager,
    fusedLocationProviderClient: FusedLocationProviderClient,
    collectionModel: CollectionViewModel,
    modifier: Modifier = Modifier) {
    // gps, network, or fused
    var currentMethod: String by remember { mutableStateOf("") }
    // for gps and network
    var positionMinTimeMs: Int by remember { mutableIntStateOf(1000) }
    var positionDistanceM: Float by remember { mutableFloatStateOf(1f) }
    // for fused
    var positionPriority: Int by remember { mutableIntStateOf(Priority.PRIORITY_BALANCED_POWER_ACCURACY) }
    var positionIntervalMs: Int by remember { mutableIntStateOf(1000) }


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

    DisposableEffect(Unit) {
        onDispose {
            Log.d("SensorListenerRegistration", "Disposing of all listeners")
            unregisterLocationListener(currentMethod)
        }
    }
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
        Button(enabled = takeNew, onClick = {
            if (!takeNew) return@Button
            collectionModel.addWaypoint(0f,0f) // Dummy values to prevent locking
            addedWaypointToast(ctx)
            /*if (currentMethod != "fused") {
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
                    Log.e("WaypointReached", "$exception")
                    actionFailedToast(ctx)
                }
            }*/
        }) { Text("Waypoint reached") }
        Row {
            // Toggle if new data can be added to collectionModel
            Text("Allow new incoming data")
            Switch(checked = takeNew,
                // Presumably, this is not updated if takeNew collectionModel is updated from elsewhere
                onCheckedChange = { checked ->
                    collectionModel.setTakesNew(checked)
                    takeNew = checked
                })
        }

    }
}
@Composable
fun DisplayWindow(collectionModel: CollectionViewModel, modifier: Modifier = Modifier) {
    // Holt die Ground-Truth-Route aus dem ViewModel
    val routePoints = collectionModel.getGroundTruth()

    // AndroidView wird verwendet, um eine klassische Android-View (MapView) in Compose zu nutzen
    AndroidView(
        modifier = modifier.fillMaxSize().clip(RectangleShape),
        factory = {
            MapView(it).apply {
                setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
                controller.setZoom(15.0)
                setMultiTouchControls(true)
                controller.setCenter(
                    GeoPoint(
                        51.482582,
                        7.217153
                    )
                ) // Default: Bochumer Innenstadt (nur Startposition)
            }
        },
        update = { mapView ->
            // Dieser Block wird ausgeführt, wenn sich die Daten (routePoints) ändern

            // 2. Alle bisherigen Overlays (Linien, Marker etc.) entfernen
            mapView.overlays.clear()

            if (routePoints.isNotEmpty()) {
                // 3. Eine Polyline (Linie) aus den Routenpunkten erstellen
                val polyline = org.osmdroid.views.overlay.Polyline()
                val geoPoints = convertFloatToGeoPoint(routePoints) // Umwandlung in GeoPoints
                polyline.setPoints(geoPoints)
                polyline.color = android.graphics.Color.RED // Farbe der Linie
                polyline.width = 8.0f // Dicke der Linie

                // 4. Die Polyline zur Karte hinzufügen
                mapView.overlays.add(polyline)

                // 5. Die Karte auf den Startpunkt der Route zentrieren
                mapView.controller.setCenter(geoPoints.first())
            }

            // Die Karte neu zeichnen, um die Änderungen anzuzeigen
            mapView.invalidate()
        }
    )
}

@Composable
fun AnalyzeWindow(errorModel: PositionErrorViewModel, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var confidence by remember { mutableFloatStateOf(0.5f) }
    var errorFromConfidence by remember { mutableFloatStateOf(0f) }
    var positionErrorCDF by remember { mutableStateOf(emptyList<FloatArray>()) }
    var errorFromConfidenceMeters by remember { mutableFloatStateOf(0f) }

    Column(modifier = modifier.padding(16.dp)) {

        AndroidView(
            factory = { context ->
                GraphView(context).apply {
                    gridLabelRenderer.horizontalAxisTitle = "Positionsfehlern (m)"
                    gridLabelRenderer.verticalAxisTitle = "CDF"

                    // X-Achse von 0.1 bis 1.0
                    viewport.isXAxisBoundsManual = true
                    viewport.setMinX(0.1)
                    viewport.setMaxX(1.0)

                    // Y-Achse von 0 bis 1
                    viewport.isYAxisBoundsManual = true
                    viewport.setMinY(0.0)
                    viewport.setMaxY(1.0)
                }
            },
            update = { graph ->
                graph.removeAllSeries()

                if (positionErrorCDF.isNotEmpty()) {
                    val series = LineGraphSeries(
                        positionErrorCDF.map {
                            DataPoint(it[0].toDouble(), it[1].toDouble())
                        }.toTypedArray()
                    )
                    graph.addSeries(series)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Daten berechnen und anzeigen
        Button(onClick = {
            positionErrorCDF = errorModel.positionErrorCDF()
        }) {
            Text("Calculate CDF")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text("Confidence level: $confidence")
        Text("Error from confidence level: $errorFromConfidence m")

        Slider(
            value = confidence,
            steps = 19,
            onValueChange = { confidence = it },
            onValueChangeFinished = {
                // Optional: direkt hier berechnen
                // errorFromConfidence = errorModel.getPositionErrorFromConfidence(confidence)
            },
            valueRange = 0f..1f
        )

        Button(onClick = {
            try {
                errorFromConfidence = errorModel.getPositionErrorFromConfidence(confidence)
            } catch (e: Exception) {
                Log.e("PositionError", "Failed to calculate position error: ${e.message}")
                actionFailedToast(ctx)
            }
        }) {
            Text("Calculate error from confidence")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text("Error from confidence level: $errorFromConfidenceMeters m")
        Button(onClick = {
            try {
                errorFromConfidenceMeters = errorModel.errorToMeters(
                    errorModel.getPositionErrorFromConfidence(confidence)
                )
            } catch (e: Exception) {
                Log.e("PositionError", "Failed to calculate position error: ${e.message}")
                actionFailedToast(ctx)
            }
        }) {
            Text("Calculate error (meters)")
        }
    }
}