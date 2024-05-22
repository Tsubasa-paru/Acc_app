package com.example.acc_app

import com.example.acc_app.SensorService
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.acc_app.ui.theme.Acc_appTheme
import android.widget.Toast
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Acc_appTheme {
                SensorScreen()
            }
        }
    }

    @Composable
    fun SensorScreen() {
        val statusMessage = remember { mutableStateOf("") }
        val sensorDataList = remember { mutableStateListOf<String>() }
        val predictionsList = remember { mutableStateListOf<String>() }

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
            Text(text = "Sensor Data:", fontSize = 16.sp)
            LazyColumn {
                items(sensorDataList) { data ->
                    Text(text = data, fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Predictions:", fontSize = 16.sp)
            LazyColumn {
                items(predictionsList) { prediction ->
                    Text(text = prediction, fontSize = 14.sp)
                }
            }
        }

        // 定期的にCSVファイルからデータを読み込む
        // ...
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
}