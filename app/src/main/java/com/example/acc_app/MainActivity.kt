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
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import android.util.Log

class MainActivity : ComponentActivity() {
    private lateinit var predictionReceiver: BroadcastReceiver
    private val predictionsList = mutableStateListOf<Pair<String, Map<String, Float>>>()
    private val showGraph = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Acc_appTheme {
                SensorScreen(predictionsList)
            }
        }
        // ログの出力を追加
        Log.d("MainActivity", "onCreate called")

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
        val predictionsData = remember { loadPredictionsData() }

        LaunchedEffect(predictionsData) {
            // ログに7日分の判別結果を表示
            predictionsData.forEach { (date, activities) ->
                Log.d("PredictionsData", "Date: $date")
                activities.forEach { (activity, count) ->
                    Log.d("PredictionsData", "Activity: $activity, Count: $count")
                }
            }
        }

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

            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {

            }) {
                Text("Show Activity Graph", fontSize = 18.sp)
            }
            Button(onClick = {
                showGraph.value = !showGraph.value
            }) {
                Text(if (showGraph.value) "Hide Activity Graph" else "Show Activity Graph", fontSize = 18.sp)
            }
            if (showGraph.value) {
                ActivityGraph(predictionsData)
            }


            val showGraph = remember { mutableStateOf(false) }
        }
    }

    @Composable
    fun ActivityGraph(predictionsData: Map<String, Map<String, Int>>) {
        val activityLabels = SensorService.WHEELCHAIR_ACTIVITIES
        val dates = predictionsData.keys.sorted().takeLast(7)

        Column {
            Text("Activity Graph (Past 7 Days)", fontSize = 18.sp)
            Spacer(modifier = Modifier.height(16.dp))

            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val barWidth = width / (activityLabels.size * dates.size)
                val maxCount = predictionsData.values.flatMap { it.values }.maxOrNull() ?: 0

                dates.forEachIndexed { dateIndex, date ->
                    val dateActivities = predictionsData[date] ?: emptyMap()
                    activityLabels.forEachIndexed { activityIndex, label ->
                        val count = dateActivities[label] ?: 0
                        val barHeight = if (maxCount > 0) (count.toFloat() / maxCount) * height else 0f
                        val x = (dateIndex * activityLabels.size + activityIndex) * barWidth
                        drawRect(
                            color = Color.Blue,
                            topLeft = Offset(x, height - barHeight),
                            size = Size(barWidth, barHeight)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyRow {
                items(dates) { date ->
                    Text(
                        text = date,
                        modifier = Modifier.padding(horizontal = 8.dp),
                        fontSize = 12.sp
                    )
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


    private fun loadPredictionsData(): Map<String, Map<String, Int>> {
        Log.d("PredictionsData", "loadPredictionsData called")
        val predictionsData = mutableMapOf<String, Map<String, Int>>()
        val folderList = getExternalFilesDir(null)?.listFiles { file -> file.isDirectory && file.name.startsWith("sensor_data_") }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val currentDate = dateFormat.format(System.currentTimeMillis())
        val oneWeekAgo = dateFormat.format(System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000)

        folderList?.forEach { folder ->
            val predictionsFile = File(folder, "predictions_data.csv")

            if (predictionsFile.exists()) {
                Log.d("PredictionsData", "Reading file: ${predictionsFile.absolutePath}")
                val lines = predictionsFile.readLines()
                if (lines.isNotEmpty()) {
                    val header = lines.first().split(",")
                    val activityLabels = header.drop(2)

                    for (line in lines.drop(1)) {
                        val values = line.split(",")
                        if (values.size == header.size) {
                            val timestamp = values[0].toLong()
                            val date = dateFormat.format(timestamp)

                            if (date in oneWeekAgo..currentDate) {
                                val activities = activityLabels.mapIndexed { index, label ->
                                    label to values[index + 2].toFloat().toInt()
                                }.toMap()

                                val existingActivities = predictionsData[date] ?: emptyMap()
                                val updatedActivities = existingActivities.toMutableMap()
                                activities.forEach { (label, count) ->
                                    val existingCount = updatedActivities[label] ?: 0
                                    updatedActivities[label] = existingCount + count
                                }
                                predictionsData[date] = updatedActivities
                            }
                        }
                    }
                }
            } else {
                Log.d("PredictionsData", "File not found: ${predictionsFile.absolutePath}")
            }
        }

        return predictionsData
    }
}
