package com.ourbloom.app.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ourbloom.app.updates.AppUpdateHelper
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppUpdateWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()

            val updateInfo = AppUpdateHelper.fetchUpdateManifestSync(client)
            if (updateInfo != null) {
                val currentCode = AppUpdateHelper.getCurrentVersionCode(appContext)
                Log.d(TAG, "AppUpdateWorker checked: currentCode=$currentCode, remoteCode=${updateInfo.versionCode}")
                if (updateInfo.versionCode > currentCode) {
                    Log.i(TAG, "New version found (v${updateInfo.versionName}), posting status bar update notification")
                    AppUpdateHelper.showUpdateNotification(appContext, updateInfo)
                } else {
                    AppUpdateHelper.dismissUpdateNotification(appContext)
                }
            }
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "AppUpdateWorker error: ${e.message}")
            Result.success()
        }
    }

    companion object {
        private const val TAG = "AppUpdateWorker"
    }
}
