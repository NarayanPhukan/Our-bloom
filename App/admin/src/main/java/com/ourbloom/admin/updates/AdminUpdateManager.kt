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
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
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

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

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
                    val downloadUrl = snapshot.getString("downloadUrl") ?: "https://github.com/NarayanPhukan/Our-bloom"
                    val changelog = snapshot.getString("changelog") ?: "General improvements & security updates."
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

                        // If remote sha differs and is not blank
                        if (remoteShaShort.isNotBlank() && currentSha != "unknown" && remoteShaShort != currentSha) {
                            updateFromGit = UpdateInfo(
                                latestVersionCode = BuildConfig.VERSION_CODE.toLong() + 1,
                                latestVersionName = "Git build ($remoteShaShort)",
                                downloadUrl = "https://github.com/NarayanPhukan/Our-bloom",
                                changelog = "• New Git Commit Pushed: ${gitCommit.message.take(120)}\n• Committed: ${gitCommit.date}\n• Author: ${gitCommit.author}",
                                forceUpdate = false,
                                isFromGitCommit = true
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "GitHub check silent note: ${e.message}")
                }

                // Decide which update to show
                val finalUpdate = updateFromFirestore ?: updateFromGit

                if (finalUpdate != null) {
                    showUpdateDialog(activity, finalUpdate)
                } else if (manualCheck) {
                    Toast.makeText(
                        activity,
                        "✨ You are on the latest version (v${BuildConfig.VERSION_NAME} • Commit ${BuildConfig.GIT_COMMIT_SHA})",
                        Toast.LENGTH_LONG
                    ).show()
                }

            } catch (e: Exception) {
                Log.w(TAG, "Update check failed", e)
                AdminBugRadar.record(
                    tag = "UpdateManager/CheckFailed",
                    message = "Update check encounter: ${e.message}",
                    throwable = e,
                    severity = BugSeverity.WARNING
                )
                if (manualCheck) {
                    Toast.makeText(activity, "Could not check for updates: ${e.message}", Toast.LENGTH_SHORT).show()
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
            if (updateInfo.isFromGitCommit) "New Git Push Detected on GitHub" else "Admin v${updateInfo.latestVersionName} is now ready"

        dialog.findViewById<TextView>(R.id.tv_current_version)?.text =
            "v${BuildConfig.VERSION_NAME} (${BuildConfig.GIT_COMMIT_SHA})"

        dialog.findViewById<TextView>(R.id.tv_latest_version)?.text =
            updateInfo.latestVersionName

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
                    Toast.makeText(activity, "Cannot open link: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(activity, "No download link configured.", Toast.LENGTH_SHORT).show()
            }
            if (!updateInfo.forceUpdate) {
                dialog.dismiss()
            }
        }

        dialog.show()
    }
}
