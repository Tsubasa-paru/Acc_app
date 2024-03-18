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

class SensorService : Service(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private lateinit var fileWriter: FileWriter
    private val fileName = "sensor_data_${System.currentTimeMillis()}.csv"
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val sensorDataList = mutableListOf<SensorData>()

    private var gravitySensor: Sensor? = null
    private var linearAccelSensor: Sensor? = null
    private var stepCounterSensor: Sensor? = null
    private var gyroscopeSensor: Sensor? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sensor Service")
            .setContentText("Collecting sensor data...")
            .setSmallIcon(R.drawable.ic_launcher_background)
            .build()

        startForeground(1, notification)

        initializeSensors()
        createFileForSensorData()

        Thread(
            Runnable {
                while(true){
                    Thread.sleep(10000)
                    //ここ　書き込み別で作る ボタン、書き込みでファイルを分ける
                    // ここで無限ループとsleepなどを書いて、定期的にファイルを書き込むようにする
                    startPeriodicWrite()
                }

                stopForeground(Service.STOP_FOREGROUND_REMOVE)
            }).start()


        return START_STICKY
    }

    private fun initializeSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        linearAccelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        sensorManager.registerListener(this, gravitySensor, SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager.registerListener(this, linearAccelSensor, SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager.registerListener(this, stepCounterSensor, SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager.registerListener(this, gyroscopeSensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        val now = System.currentTimeMillis()
        synchronized(sensorDataList) {
            val data = sensorDataList.lastOrNull()?.takeIf { it.timestamp == now }
                ?: SensorData(timestamp = now).also { sensorDataList.add(it) }

            when (event.sensor.type) {
                // Update the sensorDataList safely within the synchronized block
            }
        }
    }


    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun startPeriodicWrite() {
        coroutineScope.launch {
            while (isActive) {
                //delay(10000) // 10 seconds
                val dataToWrite: List<SensorData>
                synchronized(sensorDataList) {
                    dataToWrite = ArrayList(sensorDataList) // Create a copy for safe iteration
                    sensorDataList.clear() // Clear the original list for new data
                }
                dataToWrite.forEach { data ->
                    // Safely iterate over the copy of sensorDataList to write data
                    fileWriter.append("${data.timestamp}," +
                            "${data.gravity?.let { "${it.first},${it.second},${it.third}" } ?: "N/A,N/A,N/A"}," +
                            "${data.linearAcceleration?.let { "${it.first},${it.second},${it.third}" } ?: "N/A,N/A,N/A"}," +
                            "${data.stepCount ?: "N/A"}," +
                            "${data.gyroscope?.let { "${it.first},${it.second},${it.third}" } ?: "N/A,N/A,N/A"}\n")
                }
                fileWriter.flush()
            }
        }
    }


    private fun createFileForSensorData() {
        val file = File(getExternalFilesDir(null), fileName).apply {
            if (!exists()) createNewFile()
        }
        fileWriter = FileWriter(file, true).apply {
            append("Timestamp,GravityX,GravityY,GravityZ,LinearAccX,LinearAccY,LinearAccZ,StepCount,GyroX,GyroY,GyroZ\n")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        fileWriter.close()
        coroutineScope.cancel()
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "Sensor Service Channel",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(serviceChannel)
    }

    companion object {
        const val CHANNEL_ID = "SensorServiceChannel"
    }
}

data class SensorData(
    val timestamp: Long,
    val gravity: Triple<Float, Float, Float>? = null,
    val linearAcceleration: Triple<Float, Float, Float>? = null,
    val stepCount: Float? = null, // Assuming step counter only provides a single value
    val gyroscope: Triple<Float, Float, Float>? = null
)
