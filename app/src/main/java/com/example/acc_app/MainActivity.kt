package com.example.acc_app

import android.os.Bundle
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.acc_app.ui.theme.Acc_appTheme

class MainActivity : ComponentActivity() {
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var accelerometerData by mutableStateOf(Triple(0f, 0f, 0f))

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            event?.let {
                accelerometerData = Triple(it.values[0], it.values[1], it.values[2])
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

        setContent {
            Acc_appTheme {
                SensorScreen(accelerometerData)
            }
        }
    }
    override fun onResume() {
        super.onResume()
        // Register the listener
        accelerometer?.let { sensor ->
            sensorManager.registerListener(sensorListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        // Unregister the listener
        sensorManager.unregisterListener(sensorListener)
    }
}

@Composable
fun SensorScreen(accelerometerData: Triple<Float, Float, Float>) {
    var showData by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(16.dp)) {
        Button(onClick = { showData = true }) {
            Text("Show Sensor Data", fontSize = 18.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (showData) {
            Text("Accelerometer Data", fontSize = 18.sp)
            Text("X: ${accelerometerData.first}", fontSize = 16.sp)
            Text("Y: ${accelerometerData.second}", fontSize = 16.sp)
            Text("Z: ${accelerometerData.third}", fontSize = 16.sp)
        }
    }
}
