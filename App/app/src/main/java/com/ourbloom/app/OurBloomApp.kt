package com.ourbloom.app

import android.app.Application
import android.util.Log
import com.ourbloom.app.util.ErrorReporter

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
    }
}
