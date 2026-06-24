package com.example.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.NetDatabase

class ScanResultWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // Simulated post-scan analysis
        val scanId = inputData.getLong("scan_id", -1L)
        if (scanId == -1L) return Result.failure()

        // Simulate analyzing database for port 80/443
        // In reality, this would query NetDatabase for open HTTP ports
        // and fire a notification.

        sendNotification("Automation Trigger", "Scan analysis complete for Scan ID $scanId. Found interesting ports!")

        return Result.success()
    }

    private fun sendNotification(title: String, message: String) {
        val channelId = "scan_results_channel"
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Scan Results",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        notificationManager.notify(scanIdNotificationCounter++, builder.build())
    }

    companion object {
        var scanIdNotificationCounter = 1000
    }
}
