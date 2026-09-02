package com.ourbloom.app.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.net.HttpURLConnection
import java.net.URL

class PingServerWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val url = URL("https://our-bloom.onrender.com/api/health")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                Log.d("PingServerWorker", "Server pinged successfully.")
            } else {
                Log.w("PingServerWorker", "Failed to ping server. Code: $responseCode")
            }
            Result.success()
        } catch (e: Exception) {
            Log.w("PingServerWorker", "Error pinging server: ${e.message}")
            Result.success()
        }
    }
}
