package com.gmap.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.gmap.data.HostEntity
import com.gmap.data.NetDatabase
import com.gmap.data.ScanEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class NmapForegroundService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NetInsight Authorized Scan")
            .setContentText("Execution engine is running...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
            
        startForeground(1, notification)

        // Here we simulate the execution engine bridging to JNI/Nmap binary.
        // It populates the local Room database to drive the topology canvas in real-time.
        serviceScope.launch {
            val db = NetDatabase.getDatabase(applicationContext)
            val dao = db.netDao()
            
            val targetScope = intent?.getStringExtra("TARGET_SCOPE") ?: "192.168.1.0/24"
            
            val scanId = dao.insertScan(
                ScanEntity(
                    timestamp = System.currentTimeMillis(),
                    targetScope = targetScope,
                    commandUsed = "nmap -sS -O -T4 $targetScope"
                )
            )
            
            // Progressive simulated discovery
            val ips = listOf("192.168.1.1", "192.168.1.15", "192.168.1.42", "192.168.1.100", "192.168.1.254")
            for (ip in ips) {
                delay(1200) // Delay to showcase dynamic graph updates in physics engine
                dao.insertHost(
                    HostEntity(
                        scanId = scanId,
                        ipAddress = ip,
                        macAddress = "00:1A:2B:3C:4D:${(10..99).random()}",
                        osGuess = if (ip.endsWith(".1") || ip.endsWith(".254")) "Linux 2.6.x (Router)" else "Windows 10",
                        status = "up"
                    )
                )
            }
            
            // Stop service naturally when scan is fully completed
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Scan Execution Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    companion object {
        const val CHANNEL_ID = "NmapServiceChannel"
    }
}
