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
    }
}
