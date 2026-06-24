package com.example.analysis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AnalysisState {
    object Started : AnalysisState()
    data class Progress(val percentage: Int) : AnalysisState()
    data class Success(val result: String) : AnalysisState()
    data class Error(val message: String) : AnalysisState()
}

class AnalysisService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val EXTRA_ARGUMENTS = "extra_arguments"
        private const val CHANNEL_ID = "analysis_channel_id"
        private const val NOTIFICATION_ID = 2 // Unique ID for this service

        private val _analysisState = MutableStateFlow<AnalysisState?>(null)
        // Expose progress as a StateFlow for the UI to observe
        val analysisState: StateFlow<AnalysisState?> = _analysisState.asStateFlow()
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification("Analysis in progress..."))

        val args = intent?.getStringArrayListExtra(EXTRA_ARGUMENTS) ?: emptyList<String>()

        serviceScope.launch {
            runAnalysisTask(args)
        }

        return START_NOT_STICKY
    }

    private suspend fun runAnalysisTask(args: List<String>) {
        try {
            reportProgress(AnalysisState.Started)

            // TODO: Replace with actual long-running data processing tool
            for (i in 1..100) {
                delay(50) // Simulate work
                reportProgress(AnalysisState.Progress(i))
            }

            reportProgress(AnalysisState.Success("Analysis completed for args: $args"))
        } catch (e: Exception) {
            reportProgress(AnalysisState.Error(e.message ?: "Unknown error occurred"))
        } finally {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun reportProgress(state: AnalysisState) {
        _analysisState.value = state
        
        // Update the ongoing notification with the latest progress
        if (state is AnalysisState.Progress) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(
                NOTIFICATION_ID, 
                createNotification("Analysis in progress... ${state.percentage}%")
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // Plain started service, exposing state via companion StateFlow
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel() // Clean up coroutines when service is destroyed
    }

    private fun createNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Data Analysis")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_info) 
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Analysis Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
