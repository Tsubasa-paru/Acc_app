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
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import ai.onnxruntime.OnnxValue
import com.opencsv.CSVWriter
import ai.onnxruntime.TensorInfo
import ai.onnxruntime.SequenceInfo
import java.util.concurrent.TimeUnit
import ai.onnxruntime.OnnxSequence
import ai.onnxruntime.*


private fun createFolderName(): String {
    val timestamp = System.currentTimeMillis()
    return "sensor_data_$timestamp"
}

class SensorService : Service(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private lateinit var folderName: String

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

    private lateinit var featuresFileWriter: FileWriter
    private val FEATURES_FILE_NAME = "features_data.csv"

    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val sensorDataBuffer = mutableListOf<SensorData>()
    private val extractedFeatures = mutableListOf<Map<String, Float>>()
    private val sensorDataLock = Any()

    private var accelerometer: Sensor? = null
    private var gravitySensor: Sensor? = null
    private var linearAccelSensor: Sensor? = null
    private var stepCounterSensor: Sensor? = null
    private var gyroscopeSensor: Sensor? = null

    private lateinit var ortEnvironment: OrtEnvironment
    private lateinit var wheelchairSession: OrtSession
    private val wheelchairActivities = listOf("carry", "clean", "clothes", "cooking", "high", "low", "mid", "rest", "tablet")
    private lateinit var predictionsFileWriter: FileWriter
    private val predictionsFileName = "predictions_data.csv"
    private val writtenTimestamps = mutableSetOf<Long>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        loadWheelchairModel()
    }

    private fun loadWheelchairModel() {
        try {
            val modelFile = assets.open("wheelchair_rw_model.onnx")
            val modelBytes = modelFile.readBytes()
            ortEnvironment = OrtEnvironment.getEnvironment()
            val sessionOptions = OrtSession.SessionOptions()
            wheelchairSession = ortEnvironment.createSession(modelBytes, sessionOptions)

            // 入力層と出力層の名前をログに出力
            val inputNames = wheelchairSession.inputNames
            val outputNames = wheelchairSession.outputNames

            Log.d("Model Input Names", "Input names: $inputNames")
            Log.d("Model Output Names", "Output names: $outputNames")

            // 出力層の情報を取得
            val outputInfo = wheelchairSession.outputInfo
            Log.d("Model Output Info", "Output info: $outputInfo")
            for ((name, value) in outputInfo) {
                Log.d("Model Output Info", "Output name: $name")
                val typeInfo = value.info
                when (typeInfo) {
                    is TensorInfo -> {
                        val shape = typeInfo.shape
                        Log.d("Model Output Shape", "Output shape: ${shape.joinToString(", ")}")
                    }
                    is SequenceInfo -> {
                        Log.d("Model Output Info", "Output info is a sequence: $typeInfo")
                    }
                    else -> {
                        Log.d("Model Output Info", "Output info is not a tensor: $typeInfo")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("Wheelchair Model Loading", "Error loading the model", e)
        }
    }




    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        folderName = createFolderName()
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
            createFileForFeaturesData(folder)
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

                // 特徴量の計算と保存
                calculateAndSaveFeatures()

                // 10秒間隔で特徴量を計算
                delay(10000)
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


    private fun createFileForFeaturesData(folder: File) {
        val file = File(folder, FEATURES_FILE_NAME)
        featuresFileWriter = FileWriter(file, true).apply {
            if (file.length() == 0L) {
                append("Timestamp,Ax_m, Ay_m, Az_m, Axy_m, Ayz_m, Axz_m, Axyz_m, Ax_sd, Ay_sd, Az_sd, Axy_sd, Ayz_sd, Axz_sd, Axyz_sd, " +
                        "Ax_max, Ay_max, Az_max, Axy_max, Ayz_max, Axz_max, Axyz_max, Ax_min, Ay_min, Az_min, Axy_min, Ayz_min, Axz_min, Axyz_min, " +
                        "Ax_25, Ay_25, Az_25, Axy_25, Ayz_25, Axz_25, Axyz_25, Ax_50, Ay_50, Az_50, Axy_50, Ayz_50, Axz_50, Axyz_50, " +
                        "Ax_75, Ay_75, Az_75, Axy_75, Ayz_75, Axz_75, Axyz_75\n")
            }
        }
    }

    private fun createFileForPredictionsData(folder: File) {
        val file = File(folder, predictionsFileName)
        predictionsFileWriter = FileWriter(file, true).apply {
            append("Timestamp,PredictedActivity,${wheelchairActivities.joinToString(",")}\n")
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

    private fun extractFeaturesFromSensorData(sensorData: List<Triple<Float, Float, Float>>): Map<String, Double> {
        val features = mutableMapOf<String, Double>()
        val axis = listOf("x", "y", "z")

        val axisValues = listOf(
            sensorData.map { it.first.toDouble() },
            sensorData.map { it.second.toDouble() },
            sensorData.map { it.third.toDouble() }
        )

        val combinedValues = listOf(
            sensorData.map { sqrt(it.first.toDouble().pow(2) + it.second.toDouble().pow(2)) },
            sensorData.map { sqrt(it.second.toDouble().pow(2) + it.third.toDouble().pow(2)) },
            sensorData.map { sqrt(it.first.toDouble().pow(2) + it.third.toDouble().pow(2)) },
            sensorData.map { sqrt(it.first.toDouble().pow(2) + it.second.toDouble().pow(2) + it.third.toDouble().pow(2)) }
        )

        val statistics = listOf("mean", "std", "max", "min", "25", "50", "75")

        axis.forEachIndexed { index, a ->
            statistics.forEach { s ->
                when (s) {
                    "mean" -> features["A${a}_m"] = axisValues[index].average()
                    "std" -> features["A${a}_sd"] = axisValues[index].stdDev()
                    "max" -> features["A${a}_max"] = axisValues[index].maxOrNull() ?: 0.0
                    "min" -> features["A${a}_min"] = axisValues[index].minOrNull() ?: 0.0
                    else -> features["A${a}_$s"] = axisValues[index].percentile(s.toInt()) ?: 0.0
                }
            }
        }

        combinedValues.forEachIndexed { index, values ->
            val combination = when (index) {
                0 -> "xy"
                1 -> "yz"
                2 -> "xz"
                else -> "xyz"
            }

            statistics.forEach { s ->
                when (s) {
                    "mean" -> features["A${combination}_m"] = values.average()
                    "std" -> features["A${combination}_sd"] = values.stdDev()
                    "max" -> features["A${combination}_max"] = values.maxOrNull() ?: 0.0
                    "min" -> features["A${combination}_min"] = values.minOrNull() ?: 0.0
                    else -> features["A${combination}_$s"] = values.percentile(s.toInt()) ?: 0.0
                }
            }
        }

        return features
    }

    private fun List<Double>.stdDev(): Double {
        val avg = average()
        val squareDiffs = map { (it - avg).pow(2) }
        return sqrt(squareDiffs.sum() / (size - 1))
    }

    private fun List<Double>.percentile(percentile: Int): Double? {
        if (isEmpty()) return null
        val sortedList = sorted()
        val index = (size * percentile / 100.0).toInt()
        return sortedList[index]
    }

    private fun predictWheelchairActivity(features: Map<String, Double>): Pair<String, Map<String, Float>> {
        Log.d("Wheelchair Activity Prediction", "Input features: $features")
        try {
            val inputName = wheelchairSession.inputNames.iterator().next()
            val shape = longArrayOf(1, features.size.toLong())
            val floatArray = features.values.map { it.toFloat() }.toFloatArray()
            val input = OnnxTensor.createTensor(ortEnvironment, FloatBuffer.wrap(floatArray), shape)

            Log.d("Wheelchair Activity Prediction", "Input tensor shape: ${input.info.shape}")
            Log.d("Wheelchair Activity Prediction", "Input tensor data: ${input.floatBuffer.array().contentToString()}")

            val outputs = wheelchairSession.run(mapOf(inputName to input))
            val outputNames = wheelchairSession.outputNames

            Log.d("Wheelchair Activity Prediction", "Output names: $outputNames")

            val labelOutput = outputs[0] as OnnxTensor
            Log.d("Wheelchair Activity Prediction", "Label output: ${labelOutput.info}")

            val probOutput = outputs[1] as OnnxSequence
            Log.d("Wheelchair Activity Prediction", "Probability output: ${probOutput.info}")

            val probabilities = mutableMapOf<String, Float>()
            val probList = probOutput.value as List<*>
            for (item in probList) {
                if (item is OnnxMap) {
                    val map = item.value as Map<*, *>
                    for ((key, value) in map) {
                        probabilities[key as String] = value as Float
                    }
                }
            }

            val predictedLabel = if (probabilities.isNotEmpty()) {
                probabilities.maxByOrNull { it.value }?.key ?: "Unknown"
            } else {
                "Unknown"
            }

            Log.d("Wheelchair Activity Prediction", "Predicted Label: $predictedLabel")
            Log.d("Wheelchair Activity Prediction", "Probabilities: $probabilities")

            // Save outputs to CSV file
            saveOutputsToCSV(predictedLabel, probabilities)

            // Send broadcast with prediction result and probabilities
            val intent = Intent("com.example.acc_app.PREDICTION")
            intent.putExtra("predictedActivity", predictedLabel)
            intent.putExtra("probabilities", HashMap(probabilities))
            sendBroadcast(intent)

            return Pair(predictedLabel, probabilities)
        } catch (e: Exception) {
            Log.e("Wheelchair Activity Prediction", "Error predicting activity: ${e.message}")
            return Pair("Unknown", emptyMap())
        }
    }



    private fun saveOutputsToCSV(predictedLabel: String, probabilities: Map<String, Float>) {
        val folder = File(getExternalFilesDir(null), folderName)
        val file = File(folder, "model_outputs.csv")

        try {
            val fileWriter = FileWriter(file, true)
            val csvWriter = CSVWriter(fileWriter)

            // Write header row if the file is empty
            if (file.length() == 0L) {
                val headerRow = arrayOf("Timestamp", "Label Output", "Probability Output")
                csvWriter.writeNext(headerRow)
            }

            // Write output data
            val timestamp = System.currentTimeMillis()
            val probData = probabilities.entries.joinToString(separator = "|") { "${it.key}=${it.value}" }
            val row = arrayOf(timestamp.toString(), predictedLabel, probData)
            csvWriter.writeNext(row)

            csvWriter.close()
            fileWriter.close()
        } catch (e: Exception) {
            Log.e("Wheelchair Activity Prediction", "Error saving outputs to CSV: ${e.message}")
        }
    }


    private fun calculateAndSaveFeatures() {
        //Log.d("SensorService", "calculateAndSaveFeatures called")
        val folder = File(getExternalFilesDir(null), folderName)
        val accelerometerFile = File(folder, "accelerometer_data.csv")
        //val writtenTimestamps = mutableSetOf<Long>()

        if (accelerometerFile.exists()) {
            //Log.d("SensorService", "accelerometer_data.csv exists")
            val accelerometerData = accelerometerFile.readLines()
                .drop(1)
                .map { line ->
                    val values = line.split(",")
                    Pair(values[0].toLong(), Triple(values[1].toFloat(), values[2].toFloat(), values[3].toFloat()))
                }

            val windowSize = 100 // 1秒分のデータ数
            val windowStep = 100 // 1秒ごとにスライドするステップ数

            accelerometerData.windowed(windowSize, windowStep).forEachIndexed { index, windowData ->
                val accelerometerSensorData = windowData.map { it.second }
                val features = extractFeaturesFromSensorData(accelerometerSensorData)
                val timestamp = windowData.first().first + (index * 1000) // タイムスタンプを計算
                val (predictedLabel, probabilities) = predictWheelchairActivity(features)

                if (!writtenTimestamps.contains(timestamp)) {
                    try {
                        val featureValues = features.values.joinToString(",")
                        featuresFileWriter.appendLine("$timestamp,$featureValues")
                        writtenTimestamps.add(timestamp)

                        predictionsFileWriter.appendLine("$timestamp,$predictedLabel,${probabilities.values.joinToString(",")}")
                        saveOutputsToCSV(predictedLabel, probabilities)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    // Send broadcast with prediction result
                    val intent = Intent("com.example.acc_app.PREDICTION")
                    intent.putExtra("timestamp", timestamp)
                    intent.putExtra("predictedActivity", predictedLabel)
                    intent.putExtra("probabilities", HashMap(probabilities))
                    sendBroadcast(intent)
                }
            }

            featuresFileWriter.flush()
            predictionsFileWriter.flush()
        } else {
            Log.d("SensorService", "accelerometer_data.csv does not exist")
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
        closeFileWriter(featuresFileWriter)

        wheelchairSession.close()
        ortEnvironment.close()

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
        // 特徴量をテンソルに変換
        val inputTensor = OnnxTensor.createTensor(ortEnvironment, FloatBuffer.wrap(features.values.toFloatArray()), longArrayOf(1, features.size.toLong()))

        // 入力テンソルを用いて推論を実行
        val result = wheelchairSession.run(mapOf(wheelchairSession.inputNames.first() to inputTensor))

        // 出力テンソルの取得
        val labelOutputTensor = result[wheelchairSession.outputNames.first()] as OnnxTensor
        val probOutputTensor = result[wheelchairSession.outputNames.last()] as OnnxTensor

        // ラベルの取得
        val labelIndex = labelOutputTensor.floatBuffer.argmax()
        val predictedLabel = wheelchairActivities[labelIndex]

        // 確率テンソルの処理
        val probabilities = mutableMapOf<String, Float>()
        wheelchairActivities.forEachIndexed { index, activity ->
            probabilities[activity] = probOutputTensor.floatBuffer.get(index)
        }

        // 出力結果のログ出力
        Log.d("Inference", "Model output: [array(['$predictedLabel'], dtype=object), [$probabilities]]")

        // 推論結果をCSVファイルに保存
        saveInferenceResultToCSV(predictedLabel, probabilities)

        return predictedLabel
    }


    private fun saveInferenceResultToCSV(predictedLabel: String, probabilities: Map<String, Float>) {
        val folder = File(getExternalFilesDir(null), folderName)
        val file = File(folder, "inference_results.csv")

        try {
            val fileWriter = FileWriter(file, true)
            val csvWriter = CSVWriter(fileWriter)

            // ヘッダー行を書き込む（ファイルが空の場合のみ）
            if (file.length() == 0L) {
                val headerRow = arrayOf("Timestamp", "Predicted Label") + wheelchairActivities
                csvWriter.writeNext(headerRow)
            }

            // 推論結果を書き込む
            val timestamp = System.currentTimeMillis()
            val resultRow = arrayOf(timestamp.toString(), predictedLabel) + probabilities.values.map { it.toString() }
            csvWriter.writeNext(resultRow)

            csvWriter.close()
            fileWriter.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        const val CHANNEL_ID = "SensorServiceChannel"
        val WHEELCHAIR_ACTIVITIES = listOf("carry", "clean", "clothes", "cooking", "high", "low", "mid", "rest", "tablet")
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

private fun FloatBuffer.argmax(): Int {
    var maxIndex = 0
    var maxValue = this.get(0)
    for (i in 1 until remaining()) {
        val value = get(i)
        if (value.toFloat() > maxValue.toFloat()) {
            maxIndex = i
            maxValue = value
        }
    }
    return maxIndex
}