package com.ourbloom.app

import android.app.Application
import android.util.Log
import com.ourbloom.app.util.ErrorReporter

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ourbloom.app.workers.AppUpdateWorker
import java.util.concurrent.TimeUnit

class OurBloomApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Register uncaught crash handler to auto-detect and persist fatal crashes
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Log.e("OurBloomApp", "Uncaught crash detected on thread ${thread.name}", throwable)
                ErrorReporter.recordCrash(this, throwable)
            } catch (e: Exception) {
                Log.e("OurBloomApp", "Error in crash handler", e)
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }

        try {
            val updateRequest = PeriodicWorkRequestBuilder<AppUpdateWorker>(2, TimeUnit.HOURS).build()
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "AppUpdateWork",
                ExistingPeriodicWorkPolicy.KEEP,
                updateRequest
            )
        } catch (e: Exception) {
            Log.e("OurBloomApp", "Error scheduling AppUpdateWorker", e)
        }

        // Register update notification channel early so high-priority FCM notifications display reliably
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                val notificationManager = getSystemService(android.app.NotificationManager::class.java)
                val updateChannel = android.app.NotificationChannel(
                    com.ourbloom.app.updates.AppUpdateHelper.UPDATE_CHANNEL_ID,
                    "App Updates",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications when a new version of OurBloom is available"
                    enableLights(true)
                    lightColor = android.graphics.Color.parseColor("#FF4D6D")
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 250, 200, 250)
                    setShowBadge(true)
                }
                notificationManager?.createNotificationChannel(updateChannel)
            } catch (e: Exception) {
                Log.e("OurBloomApp", "Error initializing update notification channel", e)
            }
        }
    }
}
