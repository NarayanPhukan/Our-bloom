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

    companion object {
        lateinit var instance: OurBloomApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

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

        // Register notification channels early so high-priority FCM notifications display reliably even when app is closed
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                val notificationManager = getSystemService(android.app.NotificationManager::class.java)
                val pinkColor = android.graphics.Color.parseColor("#FF4D6D")

                // 1. App Updates
                val updateChannel = android.app.NotificationChannel(
                    com.ourbloom.app.updates.AppUpdateHelper.UPDATE_CHANNEL_ID,
                    "App Updates",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications when a new version of OurBloom is available"
                    enableLights(true)
                    lightColor = pinkColor
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 250, 200, 250)
                    setShowBadge(true)
                }

                // 2. Chat Messages
                val soundUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
                val chatAudioAttributes = android.media.AudioAttributes.Builder()
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                    .build()
                val chatChannel = android.app.NotificationChannel(
                    "ourbloom_chat_heads_up_v3",
                    "Couple Chat Messages",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Instant WhatsApp-style messages from your partner"
                    enableLights(true)
                    lightColor = pinkColor
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 180, 100, 180)
                    setSound(soundUri, chatAudioAttributes)
                    setShowBadge(true)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }

                // 3. Video Calls
                val ringtoneUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE)
                val callAudioAttributes = android.media.AudioAttributes.Builder()
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .build()
                val callChannel = android.app.NotificationChannel(
                    "ourbloom_call_channel",
                    "Video Calls",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Incoming video call alerts"
                    enableLights(true)
                    lightColor = pinkColor
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 1000, 1000, 1000)
                    setSound(ringtoneUri, callAudioAttributes)
                    setShowBadge(true)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }

                // 4. Heartbeats
                val hbChannel = android.app.NotificationChannel(
                    "ourbloom_heartbeat_channel",
                    "Heartbeat & Thinking of You",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Realtime heartbeats from your partner"
                    enableLights(true)
                    lightColor = pinkColor
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 120, 80, 240)
                    setShowBadge(true)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }

                // 5. General Notifications
                val fcmChannel = android.app.NotificationChannel(
                    "ourbloom_fcm_channel",
                    "Our Bloom Notifications",
                    android.app.NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "General notifications from OurBloom"
                    enableLights(true)
                    lightColor = pinkColor
                }

                notificationManager?.createNotificationChannels(
                    listOf(updateChannel, chatChannel, callChannel, hbChannel, fcmChannel)
                )
            } catch (e: Exception) {
                Log.e("OurBloomApp", "Error initializing notification channels", e)
            }
        }
    }
}
