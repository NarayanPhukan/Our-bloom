package com.ourbloom.app.widget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.ourbloom.app.MainActivity
import com.ourbloom.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class WidgetLiveUpdateService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var tickerJob: Job? = null
    private var isScreenOn = true
    private var receiverRegistered = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            try {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> {
                        isScreenOn = true
                        LoveTimerWidgetProvider.updateAllWidgets(applicationContext)
                        startTicker()
                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        isScreenOn = false
                        stopTicker()
                    }
                    Intent.ACTION_TIME_TICK,
                    Intent.ACTION_TIME_CHANGED,
                    Intent.ACTION_TIMEZONE_CHANGED -> {
                        LoveTimerWidgetProvider.updateAllWidgets(applicationContext)
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error in screenReceiver: ${e.message}")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            createNotificationChannel()

            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            isScreenOn = powerManager?.isInteractive ?: true

            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
            registerReceiver(screenReceiver, filter)
            receiverRegistered = true
        } catch (e: Throwable) {
            Log.e(TAG, "Error in onCreate: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // ALWAYS promote to foreground first to prevent ForegroundServiceDidNotStartInTimeException
        startForegroundNotification()

        val appWidgetManager = AppWidgetManager.getInstance(this)
        val thisWidget = ComponentName(this, LoveTimerWidgetProvider::class.java)
        val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)

        if (allWidgetIds.isEmpty()) {
            try {
                stopForegroundCompat()
            } catch (e: Throwable) {
                // Ignore
            }
            stopSelf()
            return START_NOT_STICKY
        }

        if (isScreenOn) {
            startTicker()
        }

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Love Counter Widget Sync",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps your live love timer widget updated on the home screen"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun startForegroundNotification() {
        try {
            val launchIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("OurBloom Love Counter")
                .setContentText("Keeping your love counter ticking live ❤️")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setSilent(true)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14+
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error starting foreground notification: ${e.message}")
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            while (isActive && isScreenOn) {
                try {
                    val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
                    val thisWidget = ComponentName(applicationContext, LoveTimerWidgetProvider::class.java)
                    val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
                    if (allWidgetIds.isEmpty()) {
                        stopForegroundCompat()
                        stopSelf()
                        break
                    }
                    LoveTimerWidgetProvider.updateAllWidgets(applicationContext)
                } catch (e: Throwable) {
                    Log.e(TAG, "Error during widget tick: ${e.message}")
                }
                delay(1000L)
            }
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTicker()
        if (receiverRegistered) {
            try {
                unregisterReceiver(screenReceiver)
            } catch (e: Throwable) {
                // Ignore
            }
            receiverRegistered = false
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "WidgetLiveUpdateService"
        private const val CHANNEL_ID = "love_widget_sync_channel"
        private const val NOTIFICATION_ID = 4040

        fun start(context: Context) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val thisWidget = ComponentName(context, LoveTimerWidgetProvider::class.java)
                val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
                if (allWidgetIds.isEmpty()) {
                    // Do NOT start service if no widgets exist!
                    return
                }

                val intent = Intent(context, WidgetLiveUpdateService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try {
                        context.startForegroundService(intent)
                    } catch (e: Throwable) {
                        Log.w(TAG, "startForegroundService restricted: ${e.message}")
                    }
                } else {
                    context.startService(intent)
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Could not start service: ${e.message}")
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, WidgetLiveUpdateService::class.java)
                context.stopService(intent)
            } catch (e: Throwable) {
                // Ignore
            }
        }
    }
}
