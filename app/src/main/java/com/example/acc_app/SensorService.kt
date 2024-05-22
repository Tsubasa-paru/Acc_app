package com.example.acc_app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.FileWriter
import kotlinx.coroutines.*
import android.os.Build
import android.content.pm.ServiceInfo
import kotlin.math.pow
import kotlin.math.sqrt

private fun createFolderName(): String {
    val timestamp = System.currentTimeMillis()
    return "sensor_data_$timestamp"
}

class SensorService : Service(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private lateinit var predictionsFileWriter: FileWriter
    private val predictionsFileName = "predictions_${System.currentTimeMillis()}.csv"

    private lateinit var accelerometerFileWriter: FileWriter
    private lateinit var gravityFileWriter: FileWriter
    private lateinit var linearAccelerationFileWriter: FileWriter
    private lateinit var stepCountFileWriter: FileWriter
    private lateinit var gyroscopeFileWriter: FileWriter

    private val accelerometerFileName = "accelerometer_data_${System.currentTimeMillis()}.csv"
    private val gravityFileName = "gravity_data_${System.currentTimeMillis()}.csv"
    private val linearAccelerationFileName = "linear_acceleration_data_${System.currentTimeMillis()}.csv"
    private val stepCountFileName = "step_count_data_${System.currentTimeMillis()}.csv"
    private val gyroscopeFileName = "gyroscope_data_${System.currentTimeMillis()}.csv"

    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val sensorDataBuffer = mutableListOf<SensorData>()
    private val extractedFeatures = mutableListOf<Map<String, Float>>()
    private val sensorDataLock = Any()

    private var accelerometer: Sensor? = null
    private var gravitySensor: Sensor? = null
    private var linearAccelSensor: Sensor? = null
    private var stepCounterSensor: Sensor? = null
    private var gyroscopeSensor: Sensor? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        val folderName = createFolderName()
        val folder = File(getExternalFilesDir(null), folderName)
        if (!folder.exists()) {
            folder.mkdirs()
        }

        try {
            createFileForAccelerometerData(folder)
            createFileForGravityData(folder)
            createFileForLinearAccelerationData(folder)
            createFileForStepCountData(folder)
            createFileForGyroscopeData(folder)
            createFileForPredictionsData(folder)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sensor Service")
            .setContentText("Collecting sensor data...")
            .setSmallIcon(R.drawable.ic_launcher_background)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(1, notification)
        }

        initializeSensors()

        Thread(
            Runnable {
                while (true) {
                    Thread.sleep(10000)
                    startPeriodicWrite()
                }

                stopForeground(Service.STOP_FOREGROUND_REMOVE)
            }).start()

        return START_STICKY
    }

    private fun initializeSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        linearAccelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager.registerListener(this, gravitySensor, SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager.registerListener(this, linearAccelSensor, SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager.registerListener(this, stepCounterSensor, SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager.registerListener(this, gyroscopeSensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor == null) return
        val now = System.currentTimeMillis()
        synchronized(sensorDataLock) {
            val data = sensorDataBuffer.lastOrNull()?.takeIf { it.timestamp == now }
                ?: SensorData(timestamp = now).also { sensorDataBuffer.add(it) }

            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> data.accelerometer = Triple(event.values[0], event.values[1], event.values[2])
                Sensor.TYPE_GRAVITY -> data.gravity = Triple(event.values[0], event.values[1], event.values[2])
                Sensor.TYPE_LINEAR_ACCELERATION -> data.linearAcceleration = Triple(event.values[0], event.values[1], event.values[2])
                Sensor.TYPE_STEP_COUNTER -> data.stepCount = event.values[0]
                Sensor.TYPE_GYROSCOPE -> data.gyroscope = Triple(event.values[0], event.values[1], event.values[2])
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun startPeriodicWrite() {
        coroutineScope.launch {
            while (isActive) {
                val dataToWrite: List<SensorData>
                synchronized(sensorDataLock) {
                    dataToWrite = ArrayList(sensorDataBuffer)
                    sensorDataBuffer.clear()
                }
                try {
                    dataToWrite.forEach { data ->
                        data.accelerometer?.let {
                            accelerometerFileWriter.append("${data.timestamp},${it.first},${it.second},${it.third}\n")
                        }
                        data.gravity?.let {
                            gravityFileWriter.append("${data.timestamp},${it.first},${it.second},${it.third}\n")
                        }
                        data.linearAcceleration?.let {
                            linearAccelerationFileWriter.append("${data.timestamp},${it.first},${it.second},${it.third}\n")
                        }
                        data.stepCount?.let {
                            stepCountFileWriter.append("${data.timestamp},$it\n")
                        }
                        data.gyroscope?.let {
                            gyroscopeFileWriter.append("${data.timestamp},${it.first},${it.second},${it.third}\n")
                        }
                    }
                    accelerometerFileWriter.flush()
                    gravityFileWriter.flush()
                    linearAccelerationFileWriter.flush()
                    stepCountFileWriter.flush()
                    gyroscopeFileWriter.flush()
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                val features = extractedFeatures.lastOrNull()
                val predictedClass = if (features != null) runInference(features) else "N/A"
                try {
                    predictionsFileWriter.append("${System.currentTimeMillis()},$predictedClass\n")
                    predictionsFileWriter.flush()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun createFileForAccelerometerData(folder: File) {
        val file = File(folder, "accelerometer_data.csv")
        accelerometerFileWriter = FileWriter(file, true).apply {
            append("Timestamp,AccelX,AccelY,AccelZ\n")
        }
    }

    private fun createFileForGravityData(folder: File) {
        val file = File(folder, "gravity_data.csv")
        gravityFileWriter = FileWriter(file, true).apply {
            append("Timestamp,GravityX,GravityY,GravityZ\n")
        }
    }

    private fun createFileForLinearAccelerationData(folder: File) {
        val file = File(folder, "linear_acceleration_data.csv")
        linearAccelerationFileWriter = FileWriter(file, true).apply {
            append("Timestamp,LinearAccX,LinearAccY,LinearAccZ\n")
        }
    }

    private fun createFileForStepCountData(folder: File) {
        val file = File(folder, "step_count_data.csv")
        stepCountFileWriter = FileWriter(file, true).apply {
            append("Timestamp,StepCount\n")
        }
    }

    private fun createFileForGyroscopeData(folder: File) {
        val file = File(folder, "gyroscope_data.csv")
        gyroscopeFileWriter = FileWriter(file, true).apply {
            append("Timestamp,GyroX,GyroY,GyroZ\n")
        }
    }

    private fun createFileForPredictionsData(folder: File) {
        val file = File(folder, "predictions_data.csv")
        predictionsFileWriter = FileWriter(file, true).apply {
            append("Timestamp,PredictedClass\n")
        }
    }

    private fun extractFeatures(dataList: List<SensorData>) {
        if (dataList.size < 100) return

        val featureList = mutableListOf<Map<String, Float>>()
        var startIndex = 0
        var endIndex = 99

        while (endIndex < dataList.size) {
            val windowData = dataList.subList(startIndex, endIndex + 1)
            val accelerometerData = windowData.mapNotNull { it.accelerometer }
            val gyroscopeData = windowData.mapNotNull { it.gyroscope }

            val accelerometerFeatures = extractFeaturesFromSensorData(accelerometerData).mapValues { it.value.toFloat() }
            val gyroscopeFeatures = extractFeaturesFromSensorData(gyroscopeData).mapValues { it.value.toFloat() }

            val features = accelerometerFeatures + gyroscopeFeatures
            featureList.add(features)

            startIndex += 50
            endIndex += 50
        }

        extractedFeatures.addAll(featureList)
    }

    private fun extractFeaturesFromSensorData(sensorData: List<Triple<Float, Float, Float>>): Map<String, Float> {
        val features = mutableMapOf<String, Float>()
        val xValues = sensorData.map { it.first }
        val yValues = sensorData.map { it.second }
        val zValues = sensorData.map { it.third }

        for ((index, values) in listOf(xValues, yValues, zValues).withIndex()) {
            val column = when (index) {
                0 -> "x"
                1 -> "y"
                else -> "z"
            }
            features["${column}_mean"] = values.average().toFloat()
            features["${column}_std"] = values.stdDev().toFloat()
            features["${column}_max"] = values.maxOrNull()?.toFloat() ?: 0f
            features["${column}_min"] = values.minOrNull()?.toFloat() ?: 0f
            features["${column}_quantile25"] = values.percentile(25)?.toFloat() ?: 0f
            features["${column}_median"] = values.percentile(50)?.toFloat() ?: 0f
            features["${column}_quantile75"] = values.percentile(75)?.toFloat() ?: 0f
        }

        return features
    }

    private fun List<Float>.stdDev(): Double {
        val avg = average()
        val squareDiffs = map { (it - avg).pow(2) }
        return sqrt(squareDiffs.sum() / (size - 1))
    }

    private fun List<Float>.percentile(percentile: Int): Double? {
        if (isEmpty()) return null
        val sortedList = sorted()
        val index = (size * percentile / 100.0).toInt()
        return sortedList[index].toDouble()
    }



    private fun createFileForPredictions() {
        val file = File(getExternalFilesDir(null), predictionsFileName).apply {
            if (!exists()) createNewFile()
        }
        predictionsFileWriter = FileWriter(file, true).apply {
            append("Timestamp,PredictedClass\n")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        coroutineScope.cancel()
        closeFileWriter(accelerometerFileWriter)
        closeFileWriter(gravityFileWriter)
        closeFileWriter(linearAccelerationFileWriter)
        closeFileWriter(stepCountFileWriter)
        closeFileWriter(gyroscopeFileWriter)
        closeFileWriter(predictionsFileWriter)
    }

    private fun closeFileWriter(fileWriter: FileWriter) {
        try {
            fileWriter.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "Sensor Service Channel",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(serviceChannel)
    }

    private fun runInference(features: Map<String, Float>): String {
        // ONNXモデルを使用して推論を行う処理を実装
        // 推論結果を文字列として返す
        // 例: "Walking", "Running", "Sitting", etc.
        return "Predicted Activity"
    }

    companion object {
        const val CHANNEL_ID = "SensorServiceChannel"
    }
}

data class SensorData(
    val timestamp: Long,
    var accelerometer: Triple<Float, Float, Float>? = null,
    var gravity: Triple<Float, Float, Float>? = null,
    var linearAcceleration: Triple<Float, Float, Float>? = null,
    var stepCount: Float? = null,
    var gyroscope: Triple<Float, Float, Float>? = null
)
