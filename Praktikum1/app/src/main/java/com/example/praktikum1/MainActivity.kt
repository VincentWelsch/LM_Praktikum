package com.example.praktikum1

import android.annotation.SuppressLint
import android.content.Context
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.praktikum1.ui.theme.Praktikum1Theme
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlin.math.roundToInt

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import co.yml.charts.axis.AxisData
import co.yml.charts.common.model.Point
import co.yml.charts.ui.linechart.LineChart
import co.yml.charts.ui.linechart.model.GridLines
import co.yml.charts.ui.linechart.model.IntersectionPoint
import co.yml.charts.ui.linechart.model.Line
import co.yml.charts.ui.linechart.model.LineChartData
import co.yml.charts.ui.linechart.model.LinePlotData
import co.yml.charts.ui.linechart.model.LineStyle
import co.yml.charts.ui.linechart.model.SelectionHighlightPoint
import co.yml.charts.ui.linechart.model.SelectionHighlightPopUp
import co.yml.charts.ui.linechart.model.ShadowUnderLine
import kotlinx.coroutines.delay


class MainActivity : ComponentActivity() {
    private fun startApplication() {
        val sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        val viewModel = DataAggregationViewModel()
        /* DataAggregationViewModel is a ViewModel that receives live sensor data and calculates the average
        *  of every batch (currently every half second) and stores it as a snapshot in a StateFlow.
        *  To access the snapshot:
        *  - create an instance of DataAggregationViewModel
        *  - call viewModel.startProcessing() -> here, done in DisposableEffect within Application()
        *  - in a composable, access viewModel.processedData.collectAsState() as such:
        *      val sensorData by viewModel.processedData.collectAsState()
        *      Text(text = "x: ${sensorData.accelData[0]}..."}
        *  - though not necessary, call viewModel.stopProcessing() when disposing
        */

        enableEdgeToEdge()
        setContent {
            Praktikum1Theme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Application(sensorManager, locationManager, viewModel)
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
fun Application(sensorManager: SensorManager,
                locationManager: LocationManager,
                viewModel: DataAggregationViewModel) {
    val sensorData by viewModel.processedData.collectAsState()
    // Not in startApplication because LocalCOntext.current must be inside a composable
    val ctx = LocalContext.current
    val fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(ctx)
    Column(
        modifier = Modifier
            .padding(20.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SensorConfig(sensorManager = sensorManager,
            locationManager = locationManager,
            fusedLocationProviderClient = fusedLocationProviderClient,
            viewModel = viewModel) // relied on by sensor listeners for viewModel.onNew...Data()

        // Separate composable for Data Storage and Data Visualisation here!
        TestTextOutput(sensorData.accelData,
            sensorData.gyroData,
            sensorData.magnetData,
            sensorData.positionData)
        ChartAcc(
            modifier =Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            sensorData = sensorData)
    }
    DisposableEffect(Unit) {
        viewModel.startProcessing()
        onDispose {
            viewModel.stopProcessing()
        }
    }
}

// Graphen
@Composable
fun ChartAcc(modifier: Modifier, sensorData: SensorData){
    val context = LocalContext.current
    val steps = 20
    var pointsData by remember {
        mutableStateOf(
            listOf(
                Point(0f, -10f), // Dummy für Skala
                Point(0f, 10f)   // Dummy für Skala
            )
        )
    }
    val lineChartData = remember(pointsData) {

        // Wir erstellen eine neue Liste, die nur die letzten 30 Punkte enthält
        val visiblePoints = pointsData.takeLast(30)

        val xAxisData = AxisData.Builder()
            .axisStepSize(100.dp)
            .backgroundColor(Color.Blue)
            .steps(maxOf(2, visiblePoints.size))
            // KORREKTE BESCHRIFTUNG: Holt den X-Wert vom sichtbaren Punkt
            .labelData { i ->
                visiblePoints.getOrNull(i)?.x?.toInt()?.toString() ?: ""
            }
            .labelAndAxisLinePadding(15.dp)
            .build()

        val yAxisData = AxisData.Builder()
            .steps(steps)
            .backgroundColor(Color.Red)
            .labelAndAxisLinePadding(20.dp)
            .labelData { i -> (-10 + i).toString() }.build()

        LineChartData (
            linePlotData = LinePlotData(
                lines = listOf(
                    Line(
                        // Wir übergeben nur die sichtbaren Punkte zum Zeichnen
                        dataPoints = visiblePoints,
                        LineStyle(),
                        IntersectionPoint(),
                        SelectionHighlightPoint(),
                        ShadowUnderLine(),
                        SelectionHighlightPopUp()
                    )

                ),
            ),
            xAxisData = xAxisData,
            yAxisData = yAxisData,
            gridLines = GridLines(),
            backgroundColor = Color.White
        )
    }

    LaunchedEffect(Unit) {
        while (true) {
            // Magnitude Berechnen und als Punkt dem Array hinzufügen
            pointsData = pointsData + Point(pointsData.size.toFloat(),kotlin.math.sqrt((sensorData.accelData[0] * sensorData.accelData[0] + sensorData.accelData[1] * sensorData.accelData[1] + sensorData.accelData[2] * sensorData.accelData[2]).toDouble()).toFloat()
            )

            delay(1000) // Jede Sekunde wird ein Punkt hinzugefügt und der
        }
    }
}
@Composable
fun SensorConfig(modifier: Modifier = Modifier,
                 sensorManager: SensorManager,
                 locationManager: LocationManager,
                 fusedLocationProviderClient: FusedLocationProviderClient,
                 viewModel: DataAggregationViewModel) {
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
    // SensorConfig() no longer uses accelData, ... in the component to make it accessible from outside
    // SensorConfig() no longer uses "on...Change" lambdas to update values
    // SensorConfig() now uses viewModel to update values and keep track

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
    val accelListener = remember {
        object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                viewModel.onNewAccelData(event?.values!!.copyOf(3))
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                generalAccuracyChanged("Accelerometer", accuracy)
            }
        }
    }

    val gyroListener = remember {
        object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                viewModel.onNewGyroData(event?.values!!.copyOf(3))
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                generalAccuracyChanged("Gyroscope", accuracy)
            }
        }
    }

    val magnetListener = remember {
        object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                viewModel.onNewMagnetData(event?.values!!.copyOf(3))
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                generalAccuracyChanged("Magnetometer", accuracy)
            }
        }
    }

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
            unregisterSensorListener(accelListener)
            unregisterSensorListener(gyroListener)
            unregisterSensorListener(magnetListener)
            unregisterLocationListener(currentMethod)
        }
    }

    // GUI elements
    // Toggle all
    Row {
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
                // note: changing the variables does not trigger onCheckedChange()
                unregisterSensorListener(accelListener)
                unregisterSensorListener(gyroListener)
                unregisterSensorListener(magnetListener)
                unregisterLocationListener(currentMethod)
            }
        })
        Text(text = "Enable data collection")
    }

    // Checkboxes for individual sensors
    Column { // Checkboxes
        // Accelerometer
        SensorControl(
            sensorName = "Accelerometer",
            isChecked = accelChecked,
            onCheckedChange = { checked ->
                Log.d("SensorControl", "Accelerometer checked: $checked")
                accelChecked = checked
                if (accelChecked) {
                    registerSensorLister(accelListener, Sensor.TYPE_ACCELEROMETER, accelDelay)
                } else {
                    unregisterSensorListener(accelListener)
                }
            },
            isEnabled = allSensorSwitchesEnabled,
            delayValue = accelDelay,
            onDelayChange = { accelDelay = it },
            onDelayChangeFinished = {
                unregisterSensorListener(accelListener)
                registerSensorLister(accelListener, Sensor.TYPE_ACCELEROMETER, accelDelay)
            }
        )

        // Gyroscope
        SensorControl(
            sensorName = "Gyroscope",
            isChecked = gyroChecked,
            onCheckedChange = { checked ->
                gyroChecked = checked
                if (gyroChecked) {
                    registerSensorLister(gyroListener, Sensor.TYPE_GYROSCOPE, gyroDelay)
                } else {
                    unregisterSensorListener(gyroListener)
                }
            },
            isEnabled = allSensorSwitchesEnabled,
            delayValue = gyroDelay,
            onDelayChange = { gyroDelay = it },
            onDelayChangeFinished = {
                unregisterSensorListener(gyroListener)
                registerSensorLister(gyroListener, Sensor.TYPE_GYROSCOPE, gyroDelay)
            }
        )

        // Magnetometer
        SensorControl(
            sensorName = "Magnetometer",
            isChecked = magnetChecked,
            onCheckedChange = { checked ->
                magnetChecked = checked
                if (magnetChecked) {
                    registerSensorLister(magnetListener, Sensor.TYPE_MAGNETIC_FIELD, magnetDelay)
                } else {
                    unregisterSensorListener(magnetListener)
                }
            },
            isEnabled = allSensorSwitchesEnabled,
            delayValue = magnetDelay,
            onDelayChange = { magnetDelay = it },
            onDelayChangeFinished = {
                unregisterSensorListener(magnetListener)
                registerSensorLister(magnetListener, Sensor.TYPE_MAGNETIC_FIELD, magnetDelay)
            }
        )

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
fun SensorControl(
    modifier: Modifier = Modifier,
    sensorName: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isEnabled: Boolean,
    delayValue: Int,
    onDelayChange: (Int) -> Unit,
    onDelayChangeFinished: () -> Unit
) {
    Column {
        Row { // Checkbox
            Checkbox(
                checked = isChecked,
                enabled = isEnabled,
                onCheckedChange = onCheckedChange
            )
            Text(text = sensorName)
        }
        if (isChecked) {
            Row {
                Column { // Delay slider
                    Row {
                        Text(text = "$sensorName delay: $delayValue")
                    }
                    Row {
                        Slider(
                            value = delayValue.toFloat(),
                            enabled = isEnabled,
                            modifier = modifier.weight(1f),
                            // supposedly helps with less jumpy sliders when the text length changes
                            steps = 2, // 0 -> step -> step -> 3
                            onValueChange = { onDelayChange(it.roundToInt()) },
                            onValueChangeFinished = onDelayChangeFinished,
                            // it.toInt was recommended to prevent recomposes with each fractional change
                            /* changed to it.roundToInt because testing showed that some steps are
                         * not rounded properly
                         * used Slider with range 0..60000 and 59 steps (each +1000 increment)
                         * at a certain interval got sequences like 52000 -> 52999 -> 54000
                         */
                            valueRange = SensorManager
                                .SENSOR_DELAY_FASTEST
                                .toFloat()..SensorManager.SENSOR_DELAY_NORMAL.toFloat()
                        )
                    }
                }
            }
        }
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

@Composable
fun TestTextOutput(accelData: FloatArray,
                   gyroData: FloatArray,
                   magnetData: FloatArray,
                   positionData: FloatArray) {
    Row { Text("x: ${accelData[0]} y: ${accelData[1]} z: ${accelData[2]}") }
    Row { Text("x: ${gyroData[0]} y: ${gyroData[1]} z: ${gyroData[2]}") }
    Row { Text("x: ${magnetData[0]} y: ${magnetData[1]} z: ${magnetData[2]}") }
    Row { Text("longitude: ${positionData[0]} latitude: ${positionData[1]}") }
}



// Komponenten für das Anzeigen de Daten

data class MagnetometerData(val xPoints: List<Point>, val yPoints: List<Point>, val zPoints: List<Point>)

//TODO Implementierung in die eigentliche Oberfläche

// Greetingmodul als Beispiel, wie man die Listener und Charts aufruft

@Composable
fun Greeting(modifier: Modifier) {
    val (accPoints, accMagnitude) = rememberAndProcessSensorDataAcc()
    val magData = rememberAndProcessSensorDataMag() // type: MagnetometerData


    Column(modifier = modifier) {
        Text(
            text = "Accelerometer Magnitude: $accMagnitude"
        )
        ChartSensor(points = accPoints, yStartValue = 6f, yEndValue = 12f)

        Spacer(modifier = Modifier.height(20.dp))

        Text(text = "Magnetometer \n X: ${magData.xPoints.last().y} (Cyan) \n Y:${magData.yPoints.last().y} (Green) \n Z: ${magData.zPoints.last().y} (Magenta)")
        MultiLineChartSensor(
            lines = listOf(magData.xPoints, magData.yPoints, magData.zPoints),
            yStartValue = -60f,
            yEndValue = 60f
        )
    }
}

// Funktion für das Gewinnen der Daten und die Umwandlung in ein geeignetes Format für die Charts
// TODO Hier muss nur der bereits bestehende Sensor ausgelesen werden und die Ergebnisse umgewandelt werden
@Composable
fun rememberAndProcessSensorDataAcc(): Pair<List<Point>, Float> {
    val ctx = LocalContext.current
    val sensorManager = remember { ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager }

    val accelerometer = remember { sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }
    data class AccelData(val x: Float, val y: Float, val z: Float)
    var magnitude by remember { mutableStateOf(0f) }
    var accDataList by remember { mutableStateOf<List<AccelData>>(emptyList()) }
    var listMagnitudePoints by remember { mutableStateOf(listOf(Point(0f, 0f), Point(1f, 0f))) }

    val accelerometerListener = remember {
        object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
                    accDataList = accDataList + AccelData(event.values[0], event.values[1], event.values[2])
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
    }

    DisposableEffect(Unit) {
        sensorManager.registerListener(accelerometerListener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        onDispose {
            sensorManager.unregisterListener(accelerometerListener)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            if (accDataList.isNotEmpty()) {
                val lastReading = accDataList.last()
                magnitude = kotlin.math.sqrt((lastReading.x * lastReading.x + lastReading.y * lastReading.y + lastReading.z * lastReading.z).toDouble()).toFloat()
                listMagnitudePoints = listMagnitudePoints + Point(listMagnitudePoints.size.toFloat(), magnitude)
            }
            delay(1000) // Damit sich das Diagramm jede Sekunde erneuert
        }
    }

    return Pair(listMagnitudePoints, magnitude)
}

@Composable
fun rememberAndProcessSensorDataMag(): MagnetometerData {
    val ctx = LocalContext.current
    val sensorManager = remember { ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager }

    val magnetSensor = remember { sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) }
    data class MagData(val x: Float, val y: Float, val z: Float)
    var magDataList by remember { mutableStateOf<List<MagData>>(emptyList()) }
    var listMagnitudePointsX by remember { mutableStateOf(listOf(Point(0f, 0f), Point(1f, 0f))) }
    var listMagnitudePointsY by remember { mutableStateOf(listOf(Point(0f, 0f), Point(1f, 0f))) }
    var listMagnitudePointsZ by remember { mutableStateOf(listOf(Point(0f, 0f), Point(1f, 0f))) }

    val magnetSensorListener = remember {
        object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event?.sensor?.type == Sensor.TYPE_MAGNETIC_FIELD) {
                    magDataList = magDataList + MagData(event.values[0], event.values[1], event.values[2])
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
    }

    DisposableEffect(Unit) {
        sensorManager.registerListener(magnetSensorListener, magnetSensor, SensorManager.SENSOR_DELAY_NORMAL)
        onDispose {
            sensorManager.unregisterListener(magnetSensorListener)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            if (magDataList.isNotEmpty()) {
                val lastReading = magDataList.last()
                listMagnitudePointsX = listMagnitudePointsX + Point(listMagnitudePointsX.size.toFloat(), lastReading.x)
                listMagnitudePointsY = listMagnitudePointsY + Point(listMagnitudePointsY.size.toFloat(), lastReading.y)
                listMagnitudePointsZ = listMagnitudePointsZ + Point(listMagnitudePointsZ.size.toFloat(), lastReading.z)
            }
            delay(1000)
        }
    }

    return MagnetometerData(listMagnitudePointsX, listMagnitudePointsY, listMagnitudePointsZ)
}

// Diagramme mit einer und Mehreren Linien

@Composable
fun ChartSensor(points: List<Point>, yStartValue: Float, yEndValue: Float, modifier: Modifier = Modifier) {
    val steps = (yEndValue - yStartValue).toInt()
    val lineChartData = remember(points, yStartValue, yEndValue) {
        val xAxisData = AxisData.Builder()
            .axisStepSize(100.dp)
            .backgroundColor(Color.Blue)
            .steps(maxOf(2, points.size - 1))
            .labelData { i -> points.getOrNull(i)?.x?.toInt()?.toString() ?: "" }
            .labelAndAxisLinePadding(15.dp)
            .build()

        val yAxisData = AxisData.Builder()
            .steps(steps)
            .backgroundColor(Color.Red)
            .labelAndAxisLinePadding(20.dp)

            .labelData { i -> (yStartValue + i).toInt().toString() }.build()

        LineChartData(
            linePlotData = LinePlotData(
                lines = listOf(
                    Line(
                        dataPoints = points,
                        LineStyle(),
                        IntersectionPoint(),
                        SelectionHighlightPoint(),
                        ShadowUnderLine(),
                        SelectionHighlightPopUp()
                    )
                )
            ),
            xAxisData = xAxisData,
            yAxisData = yAxisData,
            gridLines = GridLines(),
            backgroundColor = Color.White
        )
    }

    LineChart(
        modifier = modifier.fillMaxWidth().height(300.dp),
        lineChartData = lineChartData
    )
}

@Composable
fun MultiLineChartSensor(lines: List<List<Point>>, yStartValue: Float, yEndValue: Float, modifier: Modifier = Modifier) {
    val steps = 12 // 12 steps for a range of 120 (-60 to 60) in 10-step increments
    val lineChartData = remember(lines, yStartValue, yEndValue) {
        val xAxisData = AxisData.Builder()
            .axisStepSize(100.dp)
            .backgroundColor(Color.Blue)
            .steps(maxOf(2, lines.maxOfOrNull { it.size }?.minus(1) ?: 2))
            .labelData { i -> i.toString() }
            .labelAndAxisLinePadding(15.dp)
            .build()

        val yAxisData = AxisData.Builder()
            .steps(steps)
            .backgroundColor(Color.Red)

            .labelAndAxisLinePadding(20.dp)
            .labelData { i -> (yStartValue + (i * 10)).toInt().toString() }
            .build()

        val lineColors = listOf(Color.Cyan, Color.Green, Color.Magenta)

        LineChartData(
            linePlotData = LinePlotData(
                lines = lines.mapIndexed { index, points ->
                    Line(
                        dataPoints = points,
                        lineStyle = LineStyle(color = lineColors.getOrElse(index) { Color.Black }),
                        intersectionPoint = IntersectionPoint(),
                        selectionHighlightPoint = SelectionHighlightPoint(),
                        shadowUnderLine = ShadowUnderLine(),
                        selectionHighlightPopUp = SelectionHighlightPopUp()
                    )
                }
            ),
            xAxisData = xAxisData,
            yAxisData = yAxisData,
            gridLines = GridLines(),
            backgroundColor = Color.White
        )
    }

    LineChart(
        modifier = modifier.fillMaxWidth().height(500.dp),
        lineChartData = lineChartData
    )
}
