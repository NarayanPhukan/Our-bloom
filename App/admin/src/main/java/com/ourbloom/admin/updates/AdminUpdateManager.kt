package com.ourbloom.admin.updates

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.ourbloom.admin.BuildConfig
import com.ourbloom.admin.R
import com.ourbloom.admin.bugs.AdminBugRadar
import com.ourbloom.admin.bugs.BugSeverity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val latestVersionCode: Long = 1,
    val latestVersionName: String = "1.0.0",
    val downloadUrl: String = "",
    val changelog: String = "Performance and bug fixes.",
    val forceUpdate: Boolean = false,
    val isFromGitCommit: Boolean = false
)

object AdminUpdateManager {

    private const val TAG = "BloomUpdateManager"
    private const val COLLECTION_CONFIG = "admin_config"
    private const val DOC_VERSION_INFO = "version_info"
    private const val GITHUB_COMMITS_API = "https://api.github.com/repos/NarayanPhukan/Our-bloom/commits/main"
    
    // Direct raw APK fallback URL
    private const val DIRECT_RAW_APK_URL = "https://raw.githubusercontent.com/NarayanPhukan/Our-bloom/main/updates/admin/OurBloomAdmin.apk"
    private const val DIRECT_SERVER_APK_URL = "https://our-bloom.onrender.com/api/admin/app/download"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private var activeDownloadCall: Call? = null
    private var pendingInstallApk: File? = null

    /**
     * Checks Firestore AND GitHub for newer releases or git pushes.
     * @param activity Context & lifecycle for displaying updates
     * @param manualCheck If true, shows feedback if already on latest version
     */
    fun checkForUpdates(activity: AppCompatActivity, manualCheck: Boolean = false) {
        activity.lifecycleScope.launch {
            try {
                // 1. Check Firestore remote configuration
                val db = FirebaseFirestore.getInstance()
                val snapshot = db.collection(COLLECTION_CONFIG).document(DOC_VERSION_INFO).get().await()

                var updateFromFirestore: UpdateInfo? = null

                if (snapshot.exists()) {
                    val latestCode = snapshot.getLong("latestVersionCode") ?: 1L
                    val latestName = snapshot.getString("latestVersionName") ?: "1.0.0"
                    var downloadUrl = snapshot.getString("downloadUrl") ?: DIRECT_RAW_APK_URL
                    if (!downloadUrl.endsWith(".apk") && !downloadUrl.contains("/download")) {
                        downloadUrl = DIRECT_RAW_APK_URL
                    }
                    val changelog = snapshot.getString("changelog") ?: "General stability, White & Navy Blue theme, and admin profile management."
                    val forceUpdate = snapshot.getBoolean("forceUpdate") ?: false

                    if (latestCode > BuildConfig.VERSION_CODE) {
                        updateFromFirestore = UpdateInfo(
                            latestVersionCode = latestCode,
                            latestVersionName = latestName,
                            downloadUrl = downloadUrl,
                            changelog = changelog,
                            forceUpdate = forceUpdate,
                            isFromGitCommit = false
                        )
                    }
                }

                // 2. Check GitHub API for the latest pushed Git commit
                var updateFromGit: UpdateInfo? = null
                try {
                    val gitCommit = withContext(Dispatchers.IO) {
                        fetchLatestGitCommit()
                    }

                    if (gitCommit != null) {
                        val currentSha = BuildConfig.GIT_COMMIT_SHA
                        val remoteShaShort = gitCommit.sha.take(7)

                        if (remoteShaShort.isNotBlank() && currentSha != "unknown" && remoteShaShort != currentSha) {
                            updateFromGit = UpdateInfo(
                                latestVersionCode = BuildConfig.VERSION_CODE.toLong() + 1,
                                latestVersionName = "Git ($remoteShaShort)",
                                downloadUrl = DIRECT_RAW_APK_URL,
                                changelog = "• New Git Commit: ${gitCommit.message.take(120)}\n• Author: ${gitCommit.author}\n• Date: ${gitCommit.date}",
                                forceUpdate = false,
                                isFromGitCommit = true
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "GitHub check note: ${e.message}")
                }

                // Prioritize Firestore official release, then Git commit
                val finalUpdate = updateFromFirestore ?: updateFromGit

                if (finalUpdate != null) {
                    showUpdateDialog(activity, finalUpdate)
                } else if (manualCheck) {
                    Toast.makeText(
                        activity,
                        "✨ OurBloom Admin is up to date (v${BuildConfig.VERSION_NAME} • ${BuildConfig.GIT_COMMIT_SHA})",
                        Toast.LENGTH_LONG
                    ).show()
                }

            } catch (e: Exception) {
                Log.w(TAG, "Update check failed", e)
                AdminBugRadar.record(
                    tag = "UpdateManager/CheckFailed",
                    message = "Update check error: ${e.message}",
                    throwable = e,
                    severity = BugSeverity.WARNING
                )
                if (manualCheck) {
                    Toast.makeText(activity, "Could not check for updates: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Resumes APK install if user was sent to Settings for unknown sources permission
     */
    fun onResume(activity: AppCompatActivity) {
        val apk = pendingInstallApk
        if (apk != null && apk.exists()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (activity.packageManager.canRequestPackageInstalls()) {
                    pendingInstallApk = null
                    launchPackageInstaller(activity, apk)
                }
            }
        }
    }

    private data class GitCommitInfo(
        val sha: String,
        val message: String,
        val date: String,
        val author: String
    )

    private fun fetchLatestGitCommit(): GitCommitInfo? {
        val request = Request.Builder()
            .url(GITHUB_COMMITS_API)
            .header("User-Agent", "OurBloomAdmin")
            .header("Accept", "application/vnd.github.v3+json")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val json = JSONObject(body)

            val sha = json.optString("sha", "")
            val commitObj = json.optJSONObject("commit")
            val message = commitObj?.optString("message", "New git push update") ?: "New git push update"
            val committerObj = commitObj?.optJSONObject("committer")
            val date = committerObj?.optString("date", "") ?: ""
            val author = committerObj?.optString("name", "Git Admin") ?: "Git Admin"

            return GitCommitInfo(sha = sha, message = message, date = date, author = author)
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
            if (updateInfo.isFromGitCommit) "New Git Update Detected" else "Admin v${updateInfo.latestVersionName} is now ready"

        dialog.findViewById<TextView>(R.id.tv_current_version)?.text =
            "v${BuildConfig.VERSION_NAME} (${BuildConfig.GIT_COMMIT_SHA})"

        dialog.findViewById<TextView>(R.id.tv_latest_version)?.text =
            updateInfo.latestVersionName

        dialog.findViewById<TextView>(R.id.tv_update_changelog)?.text =
            updateInfo.changelog

        val forceNotice = dialog.findViewById<TextView>(R.id.tv_force_update_notice)
        val btnLater = dialog.findViewById<Button>(R.id.btn_update_later)
        val btnUpdateNow = dialog.findViewById<Button>(R.id.btn_update_now)

        btnUpdateNow.text = "Install Update Directly"

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
            dialog.dismiss()
            startDirectApkDownload(activity, updateInfo)
        }

        dialog.show()
    }

    /**
     * Downloads the APK directly inside the app with a live progress indicator,
     * then triggers Android's native package installer immediately.
     */
    private fun startDirectApkDownload(activity: AppCompatActivity, info: UpdateInfo) {
        val downloadDialog = Dialog(activity)
        downloadDialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        downloadDialog.setContentView(R.layout.dialog_update_download)
        downloadDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        downloadDialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.92).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        downloadDialog.setCancelable(false)

        val progressBar = downloadDialog.findViewById<LinearProgressIndicator>(R.id.progressBar)
        val tvBytes = downloadDialog.findViewById<TextView>(R.id.tvProgressBytes)
        val tvPercent = downloadDialog.findViewById<TextView>(R.id.tvProgressPercent)
        val btnCancel = downloadDialog.findViewById<Button>(R.id.btnCancelDownload)

        btnCancel.setOnClickListener {
            activeDownloadCall?.cancel()
            downloadDialog.dismiss()
            Toast.makeText(activity, "Update download cancelled", Toast.LENGTH_SHORT).show()
        }

        downloadDialog.show()

        CoroutineScope(Dispatchers.IO).launch {
            var downloadSuccess = false
            var downloadedFile: File? = null

            try {
                val updatesDir = File(activity.cacheDir, "admin_updates")
                if (!updatesDir.exists()) updatesDir.mkdirs()

                val tempFile = File(updatesDir, "OurBloomAdmin_download.tmp")
                val finalApk = File(updatesDir, "OurBloomAdmin_v${info.latestVersionCode}.apk")

                // Resolve download URL (fallback to direct raw github or server download)
                var resolvedUrl = if (info.downloadUrl.endsWith(".apk") || info.downloadUrl.contains("/download")) {
                    info.downloadUrl
                } else {
                    DIRECT_RAW_APK_URL
                }

                // Append cache buster query
                val urlWithBuster = if (resolvedUrl.contains("?")) {
                    "$resolvedUrl&t=${System.currentTimeMillis()}"
                } else {
                    "$resolvedUrl?t=${System.currentTimeMillis()}"
                }

                val request = Request.Builder()
                    .url(urlWithBuster)
                    .header("User-Agent", "OurBloomAdminApp")
                    .header("Cache-Control", "no-cache, no-store, must-revalidate")
                    .header("Pragma", "no-cache")
                    .build()

                val call = httpClient.newCall(request)
                activeDownloadCall = call

                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        // If direct raw github fails, attempt server fallback
                        if (resolvedUrl != DIRECT_SERVER_APK_URL) {
                            Log.d(TAG, "Primary download failed (HTTP ${response.code}), trying server endpoint...")
                            // Will be caught and handled
                        }
                        throw IOException("Failed to download APK: HTTP ${response.code}")
                    }

                    val body = response.body ?: throw IOException("Empty response body from APK URL")
                    val contentLength = body.contentLength()

                    body.byteStream().use { input ->
                        FileOutputStream(tempFile).use { output ->
                            val buffer = ByteArray(8192)
                            var totalBytesRead = 0L
                            var read: Int
                            var lastProgressTime = 0L

                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                totalBytesRead += read

                                val now = System.currentTimeMillis()
                                if (now - lastProgressTime > 80 || totalBytesRead == contentLength) {
                                    lastProgressTime = now
                                    val percent = if (contentLength > 0) {
                                        ((totalBytesRead * 100) / contentLength).toInt()
                                    } else 0

                                    withContext(Dispatchers.Main) {
                                        if (downloadDialog.isShowing) {
                                            if (contentLength > 0) {
                                                progressBar.isIndeterminate = false
                                                progressBar.progress = percent
                                                tvPercent.text = "$percent%"
                                                val readMb = totalBytesRead / (1024f * 1024f)
                                                val totalMb = contentLength / (1024f * 1024f)
                                                tvBytes.text = String.format(Locale.US, "%.1f MB / %.1f MB", readMb, totalMb)
                                            } else {
                                                progressBar.isIndeterminate = true
                                                val readMb = totalBytesRead / (1024f * 1024f)
                                                tvBytes.text = String.format(Locale.US, "%.1f MB downloaded", readMb)
                                                tvPercent.text = ""
                                            }
                                        }
                                    }
                                }
                            }
                            output.flush()
                        }
                    }

                    if (finalApk.exists()) finalApk.delete()
                    if (tempFile.renameTo(finalApk)) {
                        downloadedFile = finalApk
                        downloadSuccess = true
                    } else {
                        downloadedFile = tempFile
                        downloadSuccess = true
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error streaming APK update", e)
                withContext(Dispatchers.Main) {
                    if (activeDownloadCall?.isCanceled() != true) {
                        Toast.makeText(activity, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            } finally {
                activeDownloadCall = null
                withContext(Dispatchers.Main) {
                    try {
                        if (downloadDialog.isShowing) {
                            downloadDialog.dismiss()
                        }
                    } catch (_: Exception) {}

                    if (downloadSuccess && downloadedFile != null) {
                        promptInstall(activity, downloadedFile!!)
                    }
                }
            }
        }
    }

    private fun promptInstall(activity: AppCompatActivity, apkFile: File) {
        if (!apkFile.exists()) {
            Toast.makeText(activity, "Update package file not found.", Toast.LENGTH_SHORT).show()
            return
        }

        // On Android 8.0+ (API 26+), verify UNKNOWN_APP_SOURCES permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.packageManager.canRequestPackageInstalls()) {
                pendingInstallApk = apkFile
                MaterialAlertDialogBuilder(activity)
                    .setTitle("Permission Needed ⚙️")
                    .setMessage("To install the OurBloom Admin update directly, please enable 'Allow from this source' in Android Settings.")
                    .setPositiveButton("Open Settings") { _, _ ->
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                data = Uri.parse("package:${activity.packageName}")
                            }
                            activity.startActivity(intent)
                        } catch (e: Exception) {
                            Log.e(TAG, "Cannot open unknown app sources settings", e)
                            launchPackageInstaller(activity, apkFile)
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                return
            }
        }

        launchPackageInstaller(activity, apkFile)
    }

    private fun launchPackageInstaller(activity: AppCompatActivity, apkFile: File) {
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
