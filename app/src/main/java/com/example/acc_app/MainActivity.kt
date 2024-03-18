package com.example.acc_app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.acc_app.ui.theme.Acc_appTheme

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
        }
    }

    private fun startSensorService() {
        val serviceIntent = Intent(this, SensorService::class.java)
        startService(serviceIntent)
    }

    private fun stopSensorService() {
        val serviceIntent = Intent(this, SensorService::class.java)
        stopService(serviceIntent)
    }
}
