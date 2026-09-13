package com.ourbloom.admin.updates

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.util.Log
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.ourbloom.admin.BuildConfig
import com.ourbloom.admin.R
import com.ourbloom.admin.bugs.AdminBugRadar
import com.ourbloom.admin.bugs.BugSeverity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class UpdateInfo(
    val latestVersionCode: Long = 1,
    val latestVersionName: String = "1.0.0",
    val downloadUrl: String = "",
    val changelog: String = "Performance and bug fixes.",
    val forceUpdate: Boolean = false
)

object AdminUpdateManager {

    private const val TAG = "BloomUpdateManager"
    private const val COLLECTION_CONFIG = "admin_config"
    private const val DOC_VERSION_INFO = "version_info"

    /**
     * Checks Firestore for newer admin app releases.
     * @param activity Context & lifecycle for displaying updates
     * @param manualCheck If true, shows a toast if already on latest version
     */
    fun checkForUpdates(activity: AppCompatActivity, manualCheck: Boolean = false) {
        activity.lifecycleScope.launch {
            try {
                val db = FirebaseFirestore.getInstance()
                val snapshot = db.collection(COLLECTION_CONFIG).document(DOC_VERSION_INFO).get().await()

                if (!snapshot.exists()) {
                    // Seed initial version configuration in Firestore if missing
                    val initialConfig = mapOf(
                        "latestVersionCode" to BuildConfig.VERSION_CODE.toLong(),
                        "latestVersionName" to BuildConfig.VERSION_NAME,
                        "downloadUrl" to "https://github.com/NarayanPhukan/Our-bloom/releases",
                        "changelog" to "• Auto-detect updates & bug radar engine active\n• Real-time financial push alerts & instant PayU disbursal\n• Biometric admin authentication",
                        "forceUpdate" to false,
                        "lastUpdated" to System.currentTimeMillis()
                    )
                    db.collection(COLLECTION_CONFIG).document(DOC_VERSION_INFO).set(initialConfig, SetOptions.merge())
                    if (manualCheck) {
                        Toast.makeText(activity, "App is up to date (v${BuildConfig.VERSION_NAME})", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val latestCode = snapshot.getLong("latestVersionCode") ?: BuildConfig.VERSION_CODE.toLong()
                val latestName = snapshot.getString("latestVersionName") ?: BuildConfig.VERSION_NAME
                val downloadUrl = snapshot.getString("downloadUrl") ?: ""
                val changelog = snapshot.getString("changelog") ?: "General stability and performance improvements."
                val forceUpdate = snapshot.getBoolean("forceUpdate") ?: false

                val updateInfo = UpdateInfo(
                    latestVersionCode = latestCode,
                    latestVersionName = latestName,
                    downloadUrl = downloadUrl,
                    changelog = changelog,
                    forceUpdate = forceUpdate
                )

                if (updateInfo.latestVersionCode > BuildConfig.VERSION_CODE) {
                    showUpdateDialog(activity, updateInfo)
                } else if (manualCheck) {
                    Toast.makeText(
                        activity,
                        "✨ You are on the latest version of OurBloom Admin (v${BuildConfig.VERSION_NAME})",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: Exception) {
                Log.w(TAG, "Update check failed", e)
                AdminBugRadar.record(
                    tag = "UpdateManager/CheckFailed",
                    message = "Failed to query version config: ${e.message}",
                    throwable = e,
                    severity = BugSeverity.WARNING
                )
                if (manualCheck) {
                    Toast.makeText(activity, "Could not check for updates: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showUpdateDialog(activity: AppCompatActivity, updateInfo: UpdateInfo) {
        if (activity.isFinishing || activity.isDestroyed) return

        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_update_available)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.92).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        dialog.findViewById<TextView>(R.id.tv_update_version_title)?.text =
            "Admin v${updateInfo.latestVersionName} is now ready"
        dialog.findViewById<TextView>(R.id.tv_current_version)?.text =
            "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        dialog.findViewById<TextView>(R.id.tv_latest_version)?.text =
            "v${updateInfo.latestVersionName} (${updateInfo.latestVersionCode})"
        dialog.findViewById<TextView>(R.id.tv_update_changelog)?.text =
            updateInfo.changelog

        val forceNotice = dialog.findViewById<TextView>(R.id.tv_force_update_notice)
        val btnLater = dialog.findViewById<Button>(R.id.btn_update_later)
        val btnUpdateNow = dialog.findViewById<Button>(R.id.btn_update_now)

        if (updateInfo.forceUpdate) {
            forceNotice?.visibility = View.VISIBLE
            btnLater?.visibility = View.GONE
            dialog.setCancelable(false)
            dialog.setCanceledOnTouchOutside(false)
        } else {
            forceNotice?.visibility = View.GONE
            btnLater?.visibility = View.VISIBLE
            dialog.setCancelable(true)
            btnLater?.setOnClickListener { dialog.dismiss() }
        }

        btnUpdateNow?.setOnClickListener {
            if (updateInfo.downloadUrl.isNotBlank()) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.downloadUrl))
                    activity.startActivity(intent)
                } catch (e: Exception) {
                    AdminBugRadar.record(
                        tag = "UpdateManager/DownloadUrlLaunch",
                        message = "Could not open update URL: ${updateInfo.downloadUrl}",
                        throwable = e,
                        severity = BugSeverity.WARNING
                    )
                    Toast.makeText(activity, "Cannot open download link: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(activity, "No download link configured yet.", Toast.LENGTH_SHORT).show()
            }
            if (!updateInfo.forceUpdate) {
                dialog.dismiss()
            }
        }

        dialog.show()
    }
}
