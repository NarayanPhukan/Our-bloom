package com.ourbloom.app.chat

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GoogleDriveBackupHelper(private val context: Context) {

    private val prefs = context.getSharedPreferences("ourbloom_drive_prefs", Context.MODE_PRIVATE)

    companion object {
        const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.file"
        const val BACKUP_MIME_TYPE = "application/json"
    }

    fun getGoogleSignInClient(): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DRIVE_SCOPE))
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    fun getConnectedAccountEmail(): String? {
        val saved = prefs.getString("connected_google_email", null)
        if (!saved.isNullOrBlank()) return saved

        // Fallback to currently signed-in Google account on device
        val lastAccount = GoogleSignIn.getLastSignedInAccount(context)
        return lastAccount?.email
    }

    fun setConnectedAccountEmail(email: String?) {
        prefs.edit().putString("connected_google_email", email).apply()
    }

    fun getLastBackupTime(): String {
        return prefs.getString("last_backup_time", "Never") ?: "Never"
    }

    fun setLastBackupTime(count: Int) {
        val now = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault()).format(Date())
        prefs.edit()
            .putString("last_backup_time", "$now ($count messages)")
            .apply()
    }

    fun createBackupFileName(): String {
        val dateStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "OurBloom_Chat_Backup_$dateStamp.json"
    }

    fun createSaveDocumentIntent(fileName: String): Intent {
        return Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = BACKUP_MIME_TYPE
            putExtra(Intent.EXTRA_TITLE, fileName)
        }
    }

    fun createOpenDocumentIntent(): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/plain"))
        }
    }

    fun writeBackupToUri(uri: Uri, jsonContent: String): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(jsonContent.toByteArray(Charsets.UTF_8))
                outputStream.flush()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun readBackupFromUri(uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
                reader.readText()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
