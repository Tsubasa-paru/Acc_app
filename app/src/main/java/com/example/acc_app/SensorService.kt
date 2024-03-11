package com.example.acc_app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

class SensorService : Service() {
    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        // Create a notification channel and start foreground service with a notification
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sensor Service")
            .setContentText("Collecting sensor data...")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Set your own icon
            .build()

        Thread(
            Runnable {
                while(true){
                    Thread.sleep(1000)
                    //ここ　書き込み別で作る ボタン、書き込みでファイルを分ける
                }
                // ここで無限ループとsleepなどを書いて、定期的にファイルを書き込むようにする
                stopForeground(Service.STOP_FOREGROUND_REMOVE)
            }).start()

        startForeground(1, notification)

        // Place your sensor data collection logic here

        return START_STICKY
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "Sensor Service Channel",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
    }

    companion object {
        const val CHANNEL_ID = "SensorServiceChannel"
    }
}