package com.example.analysis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
    private lateinit var pipeline: AnalysisPipeline

    companion object {
        const val EXTRA_ARGUMENTS = "extra_arguments"
        private const val CHANNEL_ID = "analysis_channel_id"
        private const val NOTIFICATION_ID = 2

        private val _analysisState = MutableStateFlow<AnalysisState?>(null)
        val analysisState: StateFlow<AnalysisState?> = _analysisState.asStateFlow()
    }

    override fun onCreate() {
        super.onCreate()
        pipeline = AnalysisPipeline(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification("Processing..."))

        val args = intent?.getStringArrayListExtra(EXTRA_ARGUMENTS) ?: emptyList<String>()

        serviceScope.launch {
            runAnalysisTask(args)
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun runAnalysisTask(args: List<String>) {
        try {
            pipeline.executeAnalysis(assetPath = "bin/nmap", arguments = args).collect { event ->
                when (event) {
                    is AnalysisPipelineEvent.Started -> {
                        reportProgress(AnalysisState.Started)
                    }
                    is AnalysisPipelineEvent.Result -> {
                        val resultString = "${event.hosts.size} hosts found: ${event.hosts.joinToString()}"
                        reportProgress(AnalysisState.Success(resultString))
                        updateNotification("Analysis complete")
                    }
                    is AnalysisPipelineEvent.Error -> {
                        reportProgress(AnalysisState.Error(event.message))
                        updateNotification("Analysis failed: ${event.message}")
                    }
                }
            }
        } catch (e: Exception) {
            reportProgress(AnalysisState.Error(e.message ?: "Unknown error occurred"))
            updateNotification("Analysis failed")
        } finally {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun reportProgress(state: AnalysisState) {
        _analysisState.value = state
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Analysis Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Analysis Task")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, createNotification(contentText))
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
