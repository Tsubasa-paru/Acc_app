package com.example.acc_app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.acc_app.ui.theme.Acc_appTheme

class MainActivity : ComponentActivity() {
    private lateinit var predictionReceiver: BroadcastReceiver
    private val predictionsList = mutableStateListOf<Pair<String, Map<String, Float>>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Acc_appTheme {
                SensorScreen(predictionsList)
            }
        }

        predictionReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val predictedActivity = intent?.getStringExtra("predictedActivity") ?: "Unknown"
                val probabilities = intent?.getSerializableExtra("probabilities") as? Map<String, Float> ?: emptyMap()
                updatePredictionList(predictedActivity, probabilities)
            }
        }

        val intentFilter = IntentFilter("com.example.acc_app.PREDICTION")
        registerReceiver(predictionReceiver, intentFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(predictionReceiver)
    }

    @Composable
    fun SensorScreen(predictionsList: List<Pair<String, Map<String, Float>>>) {
        val statusMessage = remember { mutableStateOf("") }
        val sensorDataList = remember { mutableStateListOf<String>() }
        val scope = rememberCoroutineScope()

        Column(modifier = Modifier.padding(16.dp)) {
            Button(onClick = {
                startSensorService()
                statusMessage.value = "Sensor data collection started."
            }) {
                Text("Start Sensor Service", fontSize = 18.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(onClick = {
                stopSensorService()
                statusMessage.value = "Sensor data collection stopped."
            }) {
                Text("Stop Sensor Service", fontSize = 18.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(text = statusMessage.value, fontSize = 16.sp)

            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Predictions:", fontSize = 16.sp)
            LazyColumn {
                items(predictionsList) { prediction ->
                    Text(text = "Predicted Activity: ${prediction.first}", fontSize = 14.sp)
                    Text(text = "Probabilities:", fontSize = 14.sp)
                    prediction.second.forEach { (activity, probability) ->
                        Text(text = "$activity: $probability", fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Predictions:", fontSize = 16.sp)
            LazyColumn {
                items(predictionsList) { prediction ->
                    Text(text = "Predicted Activity: ${prediction.first}", fontSize = 14.sp)
                    Text(text = "Probabilities:", fontSize = 14.sp)
                    prediction.second.forEach { (activity, probability) ->
                        Text(text = "$activity: $probability", fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }

    private fun startSensorService() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val serviceIntent = Intent(this, SensorService::class.java)
            startService(serviceIntent)
        } else {
            requestLocationPermission()
        }
    }

    private fun stopSensorService() {
        val serviceIntent = Intent(this, SensorService::class.java)
        stopService(serviceIntent)
    }

    private val LOCATION_PERMISSION_REQUEST_CODE = 1

    private fun requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, start the service
                startSensorService()
            } else {
                // Permission denied, handle accordingly (e.g., show a message to the user)
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updatePredictionList(predictedActivity: String, probabilities: Map<String, Float>) {
        predictionsList.add(Pair(predictedActivity, probabilities))
        // Optionally, keep only the latest N predictions
        if (predictionsList.size > 10) {
            predictionsList.removeAt(0)
        }
    }
}
