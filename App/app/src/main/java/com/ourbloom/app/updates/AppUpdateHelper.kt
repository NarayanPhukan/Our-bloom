package com.ourbloom.app.updates

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.ourbloom.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * In-App Self-Update Helper for OurBloom
 * Checks for updates on git push / server manifest, displays a romantic Bloom-styled update prompt,
 * streams the APK download with live progress, and seamlessly triggers Android's native package installer.
 */
class AppUpdateHelper(private val activity: Activity) {

    data class UpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val title: String?,
        val changelog: String?,
        val apkUrl: String,
        val forceUpdate: Boolean = false
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private var activeDownloadCall: Call? = null
    private var isCheckInProgress = false
    private var updateDialog: AlertDialog? = null
    private var progressDialog: AlertDialog? = null

    companion object {
        private const val TAG = "AppUpdateHelper"
        private const val UPDATE_MANIFEST_GITHUB =
            "https://raw.githubusercontent.com/NarayanPhukan/Our-bloom/main/app-update.json"
        private const val UPDATE_MANIFEST_SERVER =
            "https://our-bloom.onrender.com/api/app-update"
        private const val UPDATE_MANIFEST_SERVER_STATIC =
            "https://our-bloom.onrender.com/updates/app-update.json"

        // Holds downloaded APK reference across activity pauses (e.g. going to settings)
        private var pendingApkFile: File? = null
    }

    private fun getCurrentVersionCode(): Long {
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activity.packageManager.getPackageInfo(activity.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                activity.packageManager.getPackageInfo(activity.packageName, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
        } catch (e: Exception) {
            1L
        }
    }

    private fun getCurrentVersionName(): String {
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activity.packageManager.getPackageInfo(activity.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                activity.packageManager.getPackageInfo(activity.packageName, 0)
            }
            pInfo.versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }

    /**
     * Checks if a newer version is available. Can be invoked during app start (onCreate or onResume).
     */
    fun checkForUpdates() {
        if (isCheckInProgress || activity.isFinishing || activity.isDestroyed) return
        isCheckInProgress = true

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val updateInfo = fetchUpdateManifest()
                if (updateInfo != null) {
                    val currentVersionCode = getCurrentVersionCode()
                    Log.d(TAG, "Current versionCode=$currentVersionCode, Remote versionCode=${updateInfo.versionCode}")

                    if (updateInfo.versionCode > currentVersionCode) {
                        withContext(Dispatchers.Main) {
                            if (!activity.isFinishing && !activity.isDestroyed) {
                                showUpdatePrompt(updateInfo)
                            }
                        }
                    } else {
                        Log.d(TAG, "App is on the latest version ($currentVersionCode)")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Update check failed: ${e.message}")
            } finally {
                isCheckInProgress = false
            }
        }
    }

    /**
     * Resumes updates or installation if returning from permission screen
     */
    fun resumeUpdates() {
        val apk = pendingApkFile
        if (apk != null && apk.exists()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (activity.packageManager.canRequestPackageInstalls()) {
                    Log.d(TAG, "Install permission granted, auto-launching installer")
                    launchPackageInstaller(apk)
                }
            } else {
                launchPackageInstaller(apk)
            }
        }
    }

    private fun fetchUpdateManifest(): UpdateInfo? {
        // 1. Try GitHub raw first (appends timestamp to prevent CDN caching)
        val timestamp = System.currentTimeMillis()
        val urls = listOf(
            "$UPDATE_MANIFEST_GITHUB?t=$timestamp",
            UPDATE_MANIFEST_SERVER,
            UPDATE_MANIFEST_SERVER_STATIC
        )

        for (urlStr in urls) {
            try {
                val request = Request.Builder()
                    .url(urlStr)
                    .header("Cache-Control", "no-cache")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string()
                        if (!bodyString.isNullOrBlank()) {
                            val json = JSONObject(bodyString)
                            return UpdateInfo(
                                versionCode = json.optInt("versionCode", 0),
                                versionName = json.optString("versionName", "1.0"),
                                title = json.optString("title", "New Bloom Update Available! 🌸"),
                                changelog = json.optString("changelog", "• New features and performance improvements."),
                                apkUrl = json.optString("apkUrl", ""),
                                forceUpdate = json.optBoolean("forceUpdate", false)
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Manifest fetch failed for $urlStr: ${e.message}")
            }
        }
        return null
    }

    private fun showUpdatePrompt(info: UpdateInfo) {
        if (updateDialog?.isShowing == true) return

        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_app_update, null)
        val tvTitle = view.findViewById<TextView>(R.id.tvUpdateTitle)
        val tvVersion = view.findViewById<TextView>(R.id.tvVersionInfo)
        val tvChangelog = view.findViewById<TextView>(R.id.tvChangelog)
        val btnLater = view.findViewById<MaterialButton>(R.id.btnLater)
        val btnInstall = view.findViewById<MaterialButton>(R.id.btnInstallUpdate)

        tvTitle.text = info.title ?: "New Bloom Update! 🌸"
        tvVersion.text = "Version ${info.versionName} is ready to install (Current: ${getCurrentVersionName()})"
        tvChangelog.text = info.changelog ?: "• Improvements and bug fixes"

        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(view)
            .setCancelable(!info.forceUpdate)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        btnLater.visibility = if (info.forceUpdate) View.GONE else View.VISIBLE
        btnLater.setOnClickListener {
            dialog.dismiss()
        }

        btnInstall.setOnClickListener {
            dialog.dismiss()
            startApkDownload(info)
        }

        updateDialog = dialog
        dialog.show()
    }

    private fun startApkDownload(info: UpdateInfo) {
        if (progressDialog?.isShowing == true) return

        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_app_update_progress, null)
        val progressBar = view.findViewById<LinearProgressIndicator>(R.id.progressBar)
        val tvBytes = view.findViewById<TextView>(R.id.tvProgressBytes)
        val tvPercent = view.findViewById<TextView>(R.id.tvProgressPercent)
        val btnCancel = view.findViewById<MaterialButton>(R.id.btnCancelDownload)

        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(view)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        btnCancel.visibility = if (info.forceUpdate) View.GONE else View.VISIBLE
        btnCancel.setOnClickListener {
            activeDownloadCall?.cancel()
            dialog.dismiss()
            Toast.makeText(activity, "Update download cancelled", Toast.LENGTH_SHORT).show()
        }

        progressDialog = dialog
        dialog.show()

        CoroutineScope(Dispatchers.IO).launch {
            var downloadSuccessful = false
            var finalApkFile: File? = null

            try {
                val updatesDir = File(activity.cacheDir, "updates")
                if (!updatesDir.exists()) updatesDir.mkdirs()

                val tempFile = File(updatesDir, "OurBloom_download.tmp")
                val destinationFile = File(updatesDir, "OurBloom_v${info.versionCode}.apk")

                val request = Request.Builder()
                    .url(info.apkUrl)
                    .build()

                val call = client.newCall(request)
                activeDownloadCall = call

                call.execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Failed to download APK: HTTP ${response.code}")

                    val body = response.body ?: throw IOException("Empty response body from APK URL")
                    val contentLength = body.contentLength()

                    body.byteStream().use { input ->
                        FileOutputStream(tempFile).use { output ->
                            val buffer = ByteArray(8192)
                            var bytesReadTotal = 0L
                            var read: Int
                            var lastProgressUpdate = 0L

                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                bytesReadTotal += read

                                val now = System.currentTimeMillis()
                                if (now - lastProgressUpdate > 60 || bytesReadTotal == contentLength) {
                                    lastProgressUpdate = now
                                    val percent = if (contentLength > 0) {
                                        ((bytesReadTotal * 100) / contentLength).toInt()
                                    } else 0

                                    withContext(Dispatchers.Main) {
                                        if (progressDialog?.isShowing == true) {
                                            if (contentLength > 0) {
                                                progressBar.isIndeterminate = false
                                                progressBar.progress = percent
                                                tvPercent.text = "$percent%"
                                                val readMb = bytesReadTotal / (1024f * 1024f)
                                                val totalMb = contentLength / (1024f * 1024f)
                                                tvBytes.text = String.format("%.1f MB / %.1f MB", readMb, totalMb)
                                            } else {
                                                progressBar.isIndeterminate = true
                                                val readMb = bytesReadTotal / (1024f * 1024f)
                                                tvBytes.text = String.format("%.1f MB downloaded", readMb)
                                                tvPercent.text = ""
                                            }
                                        }
                                    }
                                }
                            }
                            output.flush()
                        }
                    }

                    if (destinationFile.exists()) destinationFile.delete()
                    if (tempFile.renameTo(destinationFile)) {
                        finalApkFile = destinationFile
                        downloadSuccessful = true
                    } else {
                        finalApkFile = tempFile
                        downloadSuccessful = true
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading APK update", e)
                withContext(Dispatchers.Main) {
                    if (!callIsCancelled(e)) {
                        Toast.makeText(activity, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            } finally {
                activeDownloadCall = null
                withContext(Dispatchers.Main) {
                    try {
                        if (progressDialog?.isShowing == true) {
                            progressDialog?.dismiss()
                        }
                    } catch (_: Exception) {}

                    if (downloadSuccessful && finalApkFile != null) {
                        pendingApkFile = finalApkFile
                        promptInstall(finalApkFile!!)
                    }
                }
            }
        }
    }

    private fun callIsCancelled(e: Exception): Boolean {
        return e is IOException && (e.message?.contains("Canceled", ignoreCase = true) == true || activeDownloadCall?.isCanceled() == true)
    }

    private fun promptInstall(apkFile: File) {
        if (!apkFile.exists()) {
            Toast.makeText(activity, "Update package file not found", Toast.LENGTH_SHORT).show()
            return
        }

        // On Android 8.0+ (API 26+), verify UNKNOWN_APP_SOURCES permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.packageManager.canRequestPackageInstalls()) {
                MaterialAlertDialogBuilder(activity)
                    .setTitle("Permission Needed ⚙️")
                    .setMessage("To install the OurBloom update directly, please allow OurBloom to install apps in Android Settings.")
                    .setPositiveButton("Open Settings") { _, _ ->
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                data = Uri.parse("package:${activity.packageName}")
                            }
                            activity.startActivity(intent)
                        } catch (e: Exception) {
                            Log.e(TAG, "Cannot open unknown app sources settings", e)
                            launchPackageInstaller(apkFile)
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                return
            }
        }

        launchPackageInstaller(apkFile)
    }

    private fun launchPackageInstaller(apkFile: File) {
        try {
            val contentUri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            activity.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package installer", e)
            Toast.makeText(activity, "Unable to launch installer: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
