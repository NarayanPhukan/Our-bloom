package com.ourbloom.app.widget

import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.os.PowerManager
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
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
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
        } catch (e: Exception) {
            // Ignore receiver registration error
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val thisWidget = ComponentName(this, LoveTimerWidgetProvider::class.java)
        val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)

        if (allWidgetIds.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (isScreenOn) {
            startTicker()
        }

        return START_STICKY
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            while (isActive && isScreenOn) {
                val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
                val thisWidget = ComponentName(applicationContext, LoveTimerWidgetProvider::class.java)
                val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
                if (allWidgetIds.isEmpty()) {
                    stopSelf()
                    break
                }
                LoveTimerWidgetProvider.updateAllWidgets(applicationContext)
                delay(1000L)
            }
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
            } catch (e: Exception) {
                // Ignore
            }
            receiverRegistered = false
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(context: Context) {
            try {
                val intent = Intent(context, WidgetLiveUpdateService::class.java)
                context.startService(intent)
            } catch (e: Exception) {
                // Ignore background start restrictions
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, WidgetLiveUpdateService::class.java)
                context.stopService(intent)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
