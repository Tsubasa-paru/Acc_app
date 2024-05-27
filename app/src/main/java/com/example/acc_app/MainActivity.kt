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
import java.util.Calendar
import android.graphics.Paint
import androidx.compose.ui.graphics.nativeCanvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.Alignment
import kotlin.math.log


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
                activities.forEach { (activity, duration) ->
                    Log.d("PredictionsData", "Activity: $activity, Duration: $duration")
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
        Log.d("GraphData", "predictionsData: $predictionsData")
        val activityLabels = SensorService.WHEELCHAIR_ACTIVITIES
        val dates = (0 until 7).map { index ->
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, -index)
            SimpleDateFormat("MM/dd", Locale.getDefault()).format(calendar.time)
        }.reversed()

        LaunchedEffect(predictionsData) {
            predictionsData.forEach { (date, activities) ->
                Log.d("GraphData", "Date: $date")
                activities.forEach { (activity, duration) ->
                    Log.d("GraphData", "Activity: $activity, Duration: $duration")
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
        ) {
            Text("Activity Graph (Past 7 Days)", fontSize = 18.sp)
            Spacer(modifier = Modifier.height(60.dp))

            Canvas(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                val width = size.width
                val height = size.height - 40.dp.toPx()
                val barWidth = width / dates.size
                val maxDuration = (predictionsData.values.flatMap { it.values }.maxOrNull() ?: 1).toInt()
                Log.d("maxDuration", "$maxDuration")

                // Draw y-axis labels
                val maxDurationMinutes = maxDuration / 60000f // Convert milliseconds to minutes
                val yAxisStep = (maxDurationMinutes / 10).coerceAtLeast(1f) // At least 1 minute per step
                val maxDurationForGraph = (yAxisStep * 10).toInt() // Round up to the nearest step

                for (i in 0..11) {
                    val durationLabel = (i * yAxisStep).toInt()
                    val y = height - (i * height / 10f).toInt()
                    drawContext.canvas.nativeCanvas.drawText(
                        "${durationLabel}m",
                        0f,
                        y,
                        Paint().apply {
                            color = android.graphics.Color.BLACK
                            textSize = 12.sp.toPx()
                        }
                    )
                }

                dates.forEachIndexed { dateIndex, date ->
                    val dateActivities = predictionsData[date] ?: activityLabels.associateWith { 0 }
                    var stackedDuration = 0
                    Log.d("GraphData", "predictionsData: $predictionsData")
                    Log.d("GraphData", "Date: $date, dateActivities: $dateActivities")

                    activityLabels.forEachIndexed { index, label ->
                        val duration = dateActivities[label] ?: 0
                        val barHeight = (duration.toFloat() / (maxDurationForGraph * 60000)) * height
                        val x = dateIndex * barWidth + 16.dp.toPx()
                        val y = height - stackedDuration - barHeight.toInt()
                        val color = androidx.compose.ui.graphics.Color.hsl(index * 360f / activityLabels.size, 0.8f, 0.5f)
                        drawRect(
                            color = color,
                            topLeft = Offset(x, y),
                            size = Size(barWidth, barHeight)
                        )
                        stackedDuration += barHeight.toInt()
                    }

                    // Draw x-axis labels
                    val x = dateIndex * barWidth + barWidth / 2
                    drawContext.canvas.nativeCanvas.drawText(
                        date,
                        x,
                        height + 20.dp.toPx(),
                        Paint().apply {
                            color = android.graphics.Color.BLACK
                            textSize = 12.sp.toPx()
                            textAlign = Paint.Align.CENTER
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Draw legend
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    activityLabels.take(5).forEachIndexed { index, label ->
                        val color = androidx.compose.ui.graphics.Color.hsl(index * 360f / activityLabels.size, 0.8f, 0.5f)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(16.dp).background(color)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(label, fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    activityLabels.drop(5).forEachIndexed { index, label ->
                        val color = androidx.compose.ui.graphics.Color.hsl((index + 5) * 360f / activityLabels.size, 0.8f, 0.5f)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(16.dp).background(color)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(label, fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                    }
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
        val predictionsData = mutableMapOf<String, MutableMap<String, Int>>()
        val folderList = getExternalFilesDir(null)?.listFiles { file -> file.isDirectory && file.name.startsWith("sensor_data_") }

        val dateFormat = SimpleDateFormat("MM/dd", Locale.getDefault())

        // 活動ラベルのリストを取得
        val activityLabels = SensorService.WHEELCHAIR_ACTIVITIES

        folderList?.forEach { folder ->
            val predictionsFile = File(folder, "predictions_data.csv")

            if (predictionsFile.exists()) {
                Log.d("PredictionsData", "Reading file: ${predictionsFile.absolutePath}")
                val lines = predictionsFile.readLines()
                if (lines.isNotEmpty()) {
                    val header = lines.first().split(",")

                    var previousTimestamp = 0L
                    var previousActivity = ""

                    for (line in lines.drop(1)) {
                        val values = line.split(",")
                        if (values.size == header.size) {
                            val timestamp = values[0].toLong()
                            val date = dateFormat.format(timestamp)
                            val activity = values[1]

                            if (previousTimestamp != 0L) {
                                val duration = (timestamp - previousTimestamp).toInt()
                                val existingActivities = predictionsData[date] ?: mutableMapOf()
                                existingActivities[previousActivity] = (existingActivities[previousActivity] ?: 0) + duration
                                predictionsData[date] = existingActivities
                            }

                            previousTimestamp = timestamp
                            previousActivity = activity
                        }
                    }
                }
            } else {
                Log.d("PredictionsData", "File not found: ${predictionsFile.absolutePath}")
            }
        }

        // 7日分の日付のリストを作成
        val dates = (0 until 7).map { index ->
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, -index)
            dateFormat.format(calendar.time)
        }.reversed()

        val missingDates = dates.toSet() - predictionsData.keys
        for (date in missingDates) {
            predictionsData[date] = activityLabels.associateWith { 0 }.toMutableMap()
        }
        return predictionsData
    }
}
