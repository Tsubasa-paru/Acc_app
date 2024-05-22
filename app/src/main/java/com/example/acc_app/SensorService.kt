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

class SensorService : Service(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private lateinit var fileWriter: FileWriter
    private val fileName = "sensor_data_${System.currentTimeMillis()}.csv"
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val sensorDataList = mutableListOf<SensorData>()

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
        createFileForSensorData()

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
        event ?: return
        val now = System.currentTimeMillis()
        synchronized(sensorDataList) {
            val data = sensorDataList.lastOrNull()?.takeIf { it.timestamp == now }
                ?: SensorData(timestamp = now).also { sensorDataList.add(it) }

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
                synchronized(sensorDataList) {
                    dataToWrite = ArrayList(sensorDataList)
                    sensorDataList.clear()
                }
                dataToWrite.forEach { data ->
                    fileWriter.append("${data.timestamp}," +
                            "${data.accelerometer?.let { "${it.first},${it.second},${it.third}" } ?: "N/A,N/A,N/A"}," +
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
            append("Timestamp,AccelX,AccelY,AccelZ,GravityX,GravityY,GravityZ,LinearAccX,LinearAccY,LinearAccZ,StepCount,GyroX,GyroY,GyroZ\n")
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
    var accelerometer: Triple<Float, Float, Float>? = null,
    var gravity: Triple<Float, Float, Float>? = null,
    var linearAcceleration: Triple<Float, Float, Float>? = null,
    var stepCount: Float? = null,
    var gyroscope: Triple<Float, Float, Float>? = null
)