package com.example.acc_app

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
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class MainActivity : ComponentActivity() {
    private lateinit var sensorDataManager: SensorDataManager
    private var isRecording = false
    private lateinit var fileWriter: FileWriter
    private val fileName = "sensor_data.csv"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sensorDataManager = SensorDataManager(this) { sensorData ->
            if (isRecording) {
                saveDataToFile(sensorData)
            }
        }

        setContent {
            Acc_appTheme {
                SensorScreen()
            }
        }
    }

    @Composable
    fun SensorScreen() {
        val statusMessage = remember { mutableStateOf("") }

        Column(modifier = Modifier.padding(16.dp)) {
            Button(onClick = {
                isRecording = true
                createNewFileForRecording()
                sensorDataManager.startSensorListener()
                statusMessage.value = "Recording started"
            }) {
                Text("Start Recording", fontSize = 18.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(onClick = {
                isRecording = false
                sensorDataManager.stopSensorListener()
                statusMessage.value = "Recording stopped"
            }) {
                Text("Stop Recording", fontSize = 18.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(statusMessage.value, fontSize = 16.sp)
        }
    }

    private fun createNewFileForRecording() {
        val file = File(getExternalFilesDir(null), fileName)
        if (!file.exists()) {
            file.createNewFile()
        }
        fileWriter = FileWriter(file, true)
        fileWriter.append("Timestamp,AccelX,AccelY,AccelZ,GravityX,GravityY,GravityZ,LinearAccelX,LinearAccelY,LinearAccelZ,StepCount,GyroX,GyroY,GyroZ\n")
        fileWriter.flush()
    }

    private fun saveDataToFile(sensorData: SensorData) {
        CoroutineScope(Dispatchers.IO).launch {
            fileWriter.append("${sensorData.timestamp},${sensorData.accelerometerData.first},${sensorData.accelerometerData.second},${sensorData.accelerometerData.third},...")
            fileWriter.flush()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::fileWriter.isInitialized) {
            fileWriter.close()
        }
    }
}


class SensorDataManager(private val context: Context, private val onSensorDataChanged: (SensorData) -> Unit) {
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            event?.let {
                val sensorData = when (it.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> SensorData(
                        timestamp = System.currentTimeMillis(),
                        accelerometerData = Triple(it.values[0], it.values[1], it.values[2]),
                        gravityData = Triple(0f, 0f, 0f), // Example placeholder
                        linearAccelData = Triple(0f, 0f, 0f),
                        stepCountData = 0f,
                        gyroscopeData = Triple(0f, 0f, 0f)
                    )
                    // Add other sensors here
                    else -> null
                }

                sensorData?.let { data ->
                    onSensorDataChanged(data)
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            // Implement this if necessary
        }
    }

    fun startSensorListener() {
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        sensorManager.registerListener(sensorListener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        // Register other sensors here
    }

    fun stopSensorListener() {
        sensorManager.unregisterListener(sensorListener)
    }
}

data class SensorData(
    val timestamp: Long,
    val accelerometerData: Triple<Float, Float, Float>,
    val gravityData: Triple<Float, Float, Float>,
    val linearAccelData: Triple<Float, Float, Float>,
    val stepCountData: Float,
    val gyroscopeData: Triple<Float, Float, Float>
)