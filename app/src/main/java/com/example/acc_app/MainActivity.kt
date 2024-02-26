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
import kotlinx.coroutines.*
import android.content.Intent




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
                    recordedData.add(
                        SensorData(
                            System.currentTimeMillis(),
                            accelerometerData,
                            gravityData,
                            linearAccelData,
                            stepCountData,
                            gyroscopeData
                        )
                    )
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            // Not used in this example
        }
    }

    private lateinit var fileWriter: FileWriter
    private val fileName = "sensor_data.csv"

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

        val file = File(getExternalFilesDir(null), fileName)
        if (!file.exists()) {
            fileWriter = FileWriter(file, true)
            fileWriter.append("Timestamp,AccelX,AccelY,AccelZ,GravityX,GravityY,GravityZ,LinearAccelX,LinearAccelY,LinearAccelZ,StepCount,GyroX,GyroY,GyroZ\n")
            fileWriter.flush()
        } else {
            fileWriter = FileWriter(file, true)
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
        fileWriter.close() // Ensure fileWriter is properly closed
    }

    @Composable
    fun SensorScreen() {
        Column(modifier = Modifier.padding(16.dp)) {
            Button(onClick = {
                isRecording = true
                createNewFileForRecording() // Call a method to create a new file and FileWriter
                CoroutineScope(Dispatchers.IO).launch {
                    while (isRecording) {
                        saveDataToFile()
                        delay(10000) // Delay for 10 seconds
                    }
                }
                statusMessage = "Recording started"

                startSensorService()

            }) {
                Text("Start Recording", fontSize = 18.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(onClick = {
                isRecording = false
                statusMessage = "Recording stopped"

                stopSensorService()

            }) {
                Text("Stop Recording", fontSize = 18.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(statusMessage, fontSize = 16.sp)
        }
    }

    private fun createNewFileForRecording() {
        val newFileName = "sensor_data_${System.currentTimeMillis()}.csv"
        val file = File(getExternalFilesDir(null), newFileName)
        fileWriter = FileWriter(file, true) // Reinitialize the FileWriter with the new file
        fileWriter.append("Timestamp,AccelX,AccelY,AccelZ,GravityX,GravityY,GravityZ,LinearAccelX,LinearAccelY,LinearAccelZ,StepCount,GyroX,GyroY,GyroZ\n") // Write the header
        fileWriter.flush()
    }


    private fun saveDataToFile() {
        CoroutineScope(Dispatchers.IO).launch {
            val dataToWrite = synchronized(recordedData) {
                val copy = recordedData.toList() // Make a copy of the data to write
                recordedData.clear() // Clear the original list
                copy
            }

            dataToWrite.forEach { data ->
                fileWriter.append("${data.timestamp},${data.accelerometerData.first},${data.accelerometerData.second},${data.accelerometerData.third},${data.gravityData.first},${data.gravityData.second},${data.gravityData.third},${data.linearAccelData.first},${data.linearAccelData.second},${data.linearAccelData.third},${data.stepCountData},${data.gyroscopeData.first},${data.gyroscopeData.second},${data.gyroscopeData.third}\n")
            }
            fileWriter.flush()

            withContext(Dispatchers.Main) {
                // Update any UI components here if necessary
                statusMessage = "Data saved at ${System.currentTimeMillis()}"
            }
        }
    }
    private fun startSensorService() {
        val serviceIntent = Intent(this, SensorService::class.java)
        startForegroundService(serviceIntent)
    }

    private fun stopSensorService() {
        val serviceIntent = Intent(this, SensorService::class.java)
        stopService(serviceIntent)
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