package com.ourbloom.app.updates

import android.app.Activity
import android.util.Log
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability

class AppUpdateHelper(private val activity: Activity) {

    private val appUpdateManager = AppUpdateManagerFactory.create(activity)
    private val UPDATE_REQUEST_CODE = 1234

    fun checkForUpdates() {
        val appUpdateInfoTask = appUpdateManager.appUpdateInfo

        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
            ) {
                // Request the update
                try {
                    appUpdateManager.startUpdateFlowForResult(
                        appUpdateInfo,
                        AppUpdateType.IMMEDIATE,
                        activity,
                        UPDATE_REQUEST_CODE
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start update flow", e)
                }
            }
        }
    }

    fun resumeUpdates() {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                // If an in-app update is already running, resume the update.
                try {
                    appUpdateManager.startUpdateFlowForResult(
                        appUpdateInfo,
                        AppUpdateType.IMMEDIATE,
                        activity,
                        UPDATE_REQUEST_CODE
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to resume update flow", e)
                }
            }
        }
    }

    companion object {
        private const val TAG = "AppUpdateHelper"
    }
}
