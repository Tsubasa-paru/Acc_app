package com.example.acc_app

import android.content.Intent
import android.os.Bundle
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.acc_app.ui.theme.Acc_appTheme
import java.io.File
import java.io.FileWriter
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gravitySensor: Sensor? = null
    private var linearAccelSensor: Sensor? = null
    private var stepCounterSensor: Sensor? = null
    private var gyroscopeSensor: Sensor? = null

    private var accelerometerData by mutableStateOf(Triple(0f, 0f, 0f))
    private var gravityData by mutableStateOf(Triple(0f, 0f, 0f))
    private var linearAccelData by mutableStateOf(Triple(0f, 0f, 0f))
    private var stepCountData by mutableStateOf(0f)
    private var gyroscopeData by mutableStateOf(Triple(0f, 0f, 0f))

    private var isRecording = false
    private val recordedData = mutableListOf<SensorData>()

    private var statusMessage by mutableStateOf("")

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            event?.let {
                when (it.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> accelerometerData = Triple(it.values[0], it.values[1], it.values[2])
                    Sensor.TYPE_GRAVITY -> gravityData = Triple(it.values[0], it.values[1], it.values[2])
                    Sensor.TYPE_LINEAR_ACCELERATION -> linearAccelData = Triple(it.values[0], it.values[1], it.values[2])
                    Sensor.TYPE_STEP_COUNTER -> stepCountData = it.values[0]
                    Sensor.TYPE_GYROSCOPE -> gyroscopeData = Triple(it.values[0], it.values[1], it.values[2])
                }
                if (isRecording) {
                    recordedData.add(SensorData(System.currentTimeMillis(), accelerometerData, gravityData, /*... other sensor data ...*/))
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            // Not used in this example
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        linearAccelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        setContent {
            Acc_appTheme {
                SensorScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(sensorListener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager.registerListener(sensorListener, gravitySensor, SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager.registerListener(sensorListener, linearAccelSensor, SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager.registerListener(sensorListener, stepCounterSensor, SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager.registerListener(sensorListener, gyroscopeSensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(sensorListener)
    }

    @Composable
    fun SensorScreen() {
        var showData by remember { mutableStateOf(false) }
        var lastUpdate by remember { mutableStateOf(0L) }

        LaunchedEffect(showData) {
            while (showData) {
                delay(1000) // Delay for 1 second
                lastUpdate = System.currentTimeMillis()
            }
        }

        Column(modifier = Modifier.padding(16.dp)) {
            Button(onClick = { showData = !showData }) {
                Text("Toggle Sensor Data", fontSize = 18.sp)
            }
            Button(onClick = {
                isRecording = true
                statusMessage = "Recording started"
            }) {
                Text("Start Recording", fontSize = 18.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(onClick = { isRecording = false }) {
                Text("Stop Recording", fontSize = 18.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(onClick = {
                saveDataToFile()
                statusMessage = "Data saved to CSV"
            }) {
                Text("Save Recorded Data", fontSize = 18.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(statusMessage, fontSize = 16.sp)

            if (showData) {
                Text("Last Updated: $lastUpdate", fontSize = 16.sp)
                Text("Accelerometer: X=${accelerometerData.first} Y=${accelerometerData.second} Z=${accelerometerData.third}", fontSize = 16.sp)
                Text("Gravity: X=${gravityData.first} Y=${gravityData.second} Z=${gravityData.third}", fontSize = 16.sp)
                Text("Linear Accel: X=${linearAccelData.first} Y=${linearAccelData.second} Z=${linearAccelData.third}", fontSize = 16.sp)
                Text("Step Counter: $stepCountData", fontSize = 16.sp)
                Text("Gyroscope: X=${gyroscopeData.first} Y=${gyroscopeData.second} Z=${gyroscopeData.third}", fontSize = 16.sp)
            }
        }
    }
    private fun saveDataToFile() {
        val fileName = "sensor_data_${System.currentTimeMillis()}.csv"
        val file = File(getExternalFilesDir(null), fileName)
        FileWriter(file).use { writer ->
            writer.append("Timestamp,AccelX,AccelY,AccelZ,GravityX,GravityY,GravityZ\n")
            recordedData.forEach { data ->
                writer.append("${data.timestamp},${data.accelerometerData.first},${data.accelerometerData.second},${data.accelerometerData.third},${data.gravityData.first},${data.gravityData.second},${data.gravityData.third}\n")
            }
        }
        // Check if the file is saved correctly and accessible
        if (file.exists()) {
            statusMessage = "CSV file saved: ${file.absolutePath}"
        } else {
            statusMessage = "Failed to save CSV file"
        }
        // Uncomment to share the file
        // shareDataFile(file)
    }

    private fun shareDataFile(file: File) {
        // Share implementation (as previously described)
    }
}

data class SensorData(
    val timestamp: Long,
    val accelerometerData: Triple<Float, Float, Float>,
    val gravityData: Triple<Float, Float, Float>
    // Include other sensors if needed
)
