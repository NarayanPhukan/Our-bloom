package com.ourbloom.app.updates

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.ourbloom.app.MainActivity
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

    private var lastAutoCheckTime = 0L

    companion object {
        const val TAG = "AppUpdateHelper"
        const val UPDATE_CHANNEL_ID = "ourbloom_update_channel"
        const val NOTIFICATION_ID_UPDATE = 9999
        const val ACTION_SHOW_UPDATE = "com.ourbloom.app.ACTION_SHOW_UPDATE"
        const val EXTRA_ACTION_SHOW_UPDATE = "show_update"

        private const val UPDATE_MANIFEST_GITHUB_API =
            "https://api.github.com/repos/NarayanPhukan/Our-bloom/contents/app-update.json"
        private const val UPDATE_MANIFEST_GITHUB =
            "https://raw.githubusercontent.com/NarayanPhukan/Our-bloom/main/app-update.json"
        private const val UPDATE_MANIFEST_SERVER =
            "https://our-bloom.onrender.com/api/app-update"
        private const val UPDATE_MANIFEST_SERVER_STATIC =
            "https://our-bloom.onrender.com/updates/app-update.json"

        // Holds downloaded APK reference across activity pauses (e.g. going to settings)
        private var pendingApkFile: File? = null

        fun getCurrentVersionCode(context: Context): Long {
            return try {
                val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(context.packageName, 0)
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

        fun getCurrentVersionName(context: Context): String {
            return try {
                val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(context.packageName, 0)
                }
                pInfo.versionName ?: "1.0"
            } catch (e: Exception) {
                "1.0"
            }
        }

        fun fetchUpdateManifestSync(client: OkHttpClient): UpdateInfo? {
            val timestamp = System.currentTimeMillis()

            // 1. Primary: GitHub API with raw accept header (zero Fastly CDN caching delay on push)
            try {
                val apiRequest = Request.Builder()
                    .url(UPDATE_MANIFEST_GITHUB_API)
                    .header("Accept", "application/vnd.github.v3.raw")
                    .header("User-Agent", "OurBloomApp")
                    .header("Cache-Control", "no-cache, no-store, must-revalidate")
                    .header("Pragma", "no-cache")
                    .build()

                client.newCall(apiRequest).execute().use { response ->
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
                Log.d(TAG, "GitHub API manifest fetch failed: ${e.message}")
            }

            // 2. Fallbacks: raw GitHub with timestamp query, Render server endpoints
            val urls = listOf(
                "$UPDATE_MANIFEST_GITHUB?t=$timestamp",
                UPDATE_MANIFEST_SERVER,
                UPDATE_MANIFEST_SERVER_STATIC
            )

            for (urlStr in urls) {
                try {
                    val request = Request.Builder()
                        .url(urlStr)
                        .header("Cache-Control", "no-cache, no-store, must-revalidate")
                        .header("Pragma", "no-cache")
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

        fun showUpdateNotification(context: Context, info: UpdateInfo) {
            try {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        UPDATE_CHANNEL_ID,
                        "App Updates",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Notifications when a new version of OurBloom is available"
                        enableLights(true)
                        lightColor = Color.parseColor("#FF4D6D")
                        enableVibration(true)
                        vibrationPattern = longArrayOf(0, 250, 200, 250)
                        setShowBadge(true)
                    }
                    notificationManager.createNotificationChannel(channel)
                }

                val intent = Intent(context, MainActivity::class.java).apply {
                    action = ACTION_SHOW_UPDATE
                    putExtra("action", EXTRA_ACTION_SHOW_UPDATE)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }

                val pendingIntent = PendingIntent.getActivity(
                    context,
                    NOTIFICATION_ID_UPDATE,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )

                val title = info.title?.takeIf { it.isNotBlank() } ?: "New Bloom Update Available! 🌸"
                val shortText = "Version ${info.versionName} is ready to install. Tap to update!"
                val changelogText = info.changelog?.takeIf { it.isNotBlank() } ?: "• Performance improvements and bug fixes"

                val bigTextStyle = NotificationCompat.BigTextStyle()
                    .setBigContentTitle(title)
                    .setSummaryText("v${info.versionName} available")
                    .bigText("Version ${info.versionName} is ready! 🌸\n\n$changelogText\n\nTap to download and install now.")

                val notification = NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(title)
                    .setContentText(shortText)
                    .setStyle(bigTextStyle)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_STATUS)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .setColor(Color.parseColor("#FF4D6D"))
                    .addAction(
                        R.drawable.ic_system_update,
                        "Update Now",
                        pendingIntent
                    )
                    .build()

                notificationManager.notify(NOTIFICATION_ID_UPDATE, notification)
                Log.d(TAG, "Update notification displayed for v${info.versionName}")
            } catch (e: Exception) {
                Log.e(TAG, "Error displaying update notification", e)
            }
        }

        fun dismissUpdateNotification(context: Context) {
            try {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(NOTIFICATION_ID_UPDATE)
                Log.d(TAG, "Update notification dismissed")
            } catch (e: Exception) {
                Log.e(TAG, "Error dismissing update notification", e)
            }
        }
    }

    private fun getCurrentVersionCode(): Long = getCurrentVersionCode(activity)

    private fun getCurrentVersionName(): String = getCurrentVersionName(activity)

    /**
     * Checks if a newer version is available.
     * @param manualCheck If true, displays a toast when the app is already on the latest version or if network check fails.
     */
    fun checkForUpdates(manualCheck: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!manualCheck && now - lastAutoCheckTime < 60_000) return
        if (isCheckInProgress || activity.isFinishing || activity.isDestroyed) return
        isCheckInProgress = true
        lastAutoCheckTime = now

        if (manualCheck) {
            Toast.makeText(activity, "Checking for Bloom updates... 🌸", Toast.LENGTH_SHORT).show()
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val updateInfo = fetchUpdateManifest()
                if (updateInfo != null) {
                    val currentVersionCode = getCurrentVersionCode()
                    Log.d(TAG, "Current versionCode=$currentVersionCode, Remote versionCode=${updateInfo.versionCode}")

                    if (updateInfo.versionCode > currentVersionCode) {
                        val prefs = activity.getSharedPreferences("ourbloom_update_prefs", Context.MODE_PRIVATE)
                        val dismissedCode = prefs.getLong("dismissed_version_code", -1L)
                        val dismissedTime = prefs.getLong("dismissed_time", 0L)
                        val isDismissedRecent = (dismissedCode == updateInfo.versionCode.toLong()) && (now - dismissedTime < 24 * 3600 * 1000L)

                        if (!manualCheck && !updateInfo.forceUpdate && isDismissedRecent) {
                            Log.d(TAG, "Update v${updateInfo.versionName} was recently dismissed. Suppressing auto-prompt.")
                            return@launch
                        }

                        withContext(Dispatchers.Main) {
                            if (!activity.isFinishing && !activity.isDestroyed) {
                                showUpdatePrompt(updateInfo)
                            } else {
                                showUpdateNotification(activity, updateInfo)
                            }
                        }
                    } else {
                        Log.d(TAG, "App is on the latest version ($currentVersionCode)")
                        dismissUpdateNotification(activity)
                        if (manualCheck) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(activity, "OurBloom is up to date (v${getCurrentVersionName()}) 🌸", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } else if (manualCheck) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(activity, "Unable to reach update server. Please try again later.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Update check failed: ${e.message}")
                if (manualCheck) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(activity, "Update check failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
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
        val timestamp = System.currentTimeMillis()

        // 1. Primary: GitHub API with raw accept header (zero Fastly CDN caching delay on push)
        try {
            val apiRequest = Request.Builder()
                .url(UPDATE_MANIFEST_GITHUB_API)
                .header("Accept", "application/vnd.github.v3.raw")
                .header("User-Agent", "OurBloomApp")
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .header("Pragma", "no-cache")
                .build()

            client.newCall(apiRequest).execute().use { response ->
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
            Log.d(TAG, "GitHub API manifest fetch failed: ${e.message}")
        }

        // 2. Fallbacks: raw GitHub with timestamp query, Render server endpoints
        val urls = listOf(
            "$UPDATE_MANIFEST_GITHUB?t=$timestamp",
            UPDATE_MANIFEST_SERVER,
            UPDATE_MANIFEST_SERVER_STATIC
        )

        for (urlStr in urls) {
            try {
                val request = Request.Builder()
                    .url(urlStr)
                    .header("Cache-Control", "no-cache, no-store, must-revalidate")
                    .header("Pragma", "no-cache")
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
            val prefs = activity.getSharedPreferences("ourbloom_update_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putLong("dismissed_version_code", info.versionCode.toLong())
                .putLong("dismissed_time", System.currentTimeMillis())
                .apply()
            dialog.dismiss()
        }

        dialog.setOnCancelListener {
            val prefs = activity.getSharedPreferences("ourbloom_update_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putLong("dismissed_version_code", info.versionCode.toLong())
                .putLong("dismissed_time", System.currentTimeMillis())
                .apply()
        }

        btnInstall.setOnClickListener {
            dialog.dismiss()
            dismissUpdateNotification(activity)
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

                val cacheBusterUrl = if (info.apkUrl.contains("?")) {
                    "${info.apkUrl}&t=${System.currentTimeMillis()}"
                } else {
                    "${info.apkUrl}?t=${System.currentTimeMillis()}"
                }

                val request = Request.Builder()
                    .url(cacheBusterUrl)
                    .header("Cache-Control", "no-cache, no-store, must-revalidate")
                    .header("Pragma", "no-cache")
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

        val pInfo = activity.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
        val apkVersionCode = if (pInfo != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pInfo.longVersionCode else @Suppress("DEPRECATION") pInfo.versionCode.toLong()
        } else null

        if (apkVersionCode != null && apkVersionCode <= getCurrentVersionCode()) {
            Log.w(TAG, "Downloaded APK versionCode ($apkVersionCode) <= current (${getCurrentVersionCode()})! Stale cache.")
            Toast.makeText(activity, "Installed version is already up to date (v${getCurrentVersionName()}) 🌸", Toast.LENGTH_LONG).show()
            dismissUpdateNotification(activity)
            apkFile.delete()
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
